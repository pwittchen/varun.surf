package com.github.pwittchen.varun.service.forecast;

import com.github.pwittchen.varun.model.forecast.Forecast;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class IcmForecastVisionService {

    private static final Logger log = LoggerFactory.getLogger(IcmForecastVisionService.class);

    // The meteogram x-axis is always plotted in Polish local time (CET/CEST), regardless of server timezone
    private static final ZoneId ICM_ZONE = ZoneId.of("Europe/Warsaw");
    // Must match the format produced by WeatherForecastMapper, the frontend splits it on spaces
    private static final DateTimeFormatter OUTPUT_FORMATTER =
            DateTimeFormatter.ofPattern("EEE dd MMM yyyy HH:mm", Locale.ENGLISH);
    private static final double MS_TO_KNOTS = 1.94384;
    private static final double MAX_PLAUSIBLE_KNOTS = 120.0;
    private static final int MAX_FORECAST_DAYS_AHEAD = 10;
    private static final List<String> CARDINAL_DIRECTIONS = List.of("N", "NE", "E", "SE", "S", "SW", "W", "NW");
    private static final Pattern DAY_MONTH_PATTERN = Pattern.compile("^(\\d{1,2})[.\\-/](\\d{1,2})");
    private static final Pattern CODE_FENCE_PATTERN = Pattern.compile("(?s)^\\s*```(?:json)?\\s*(.*?)\\s*```\\s*$");

    private static final String VISION_PROMPT = """
            You are reading a Polish ICM UM 4 km meteogram image from meteo.pl (numerical weather forecast).

            LAYOUT (panels stacked vertically, top to bottom):
            1. TEMPERATURE - red/blue lines, left axis in degrees Celsius. Thin vertical red bars are
               the spread between grid points, the thick red line is the forecast temperature.
            2. PRECIPITATION - green vertical bars, left axis in mm/h (0 at the bottom).
               The orange line on this panel is relative humidity in percent (right axis) - IGNORE it.
            3. PRESSURE - black line over a color-filled area, left axis in hPa (e.g. 1000, 1010, 1020).
               The right axis is mmHg - IGNORE it. The fill color here means nothing.
            4. WIND (the most important panel) - a smooth colored area with a dark blue outline.
               The LEFT axis is m/s (ticks 0, 5, 10, 15, 20, 25), the RIGHT axis is the same data
               in km/h (ticks 0, 18, 36, 54, 72, 90). Read the wind from the top of the colored area.
               The short HORIZONTAL RED DASHES floating above the area are the GUSTS (szkwaly),
               read them on the same axis. Black dashed lines are only the grid - never read them as data.
               Fill color is a sanity check for the wind speed:
                 dark/light blue = weak, below ~6 m/s (12 kts)
                 green           = ~6-10 m/s (12-20 kts)
                 yellow          = ~10-13 m/s (20-25 kts)
                 orange/red      = ~15 m/s and more (30+ kts)
            5. WIND DIRECTION - a narrow panel with blue arrows, oriented like a compass:
               up = N, right = E, down = S, left = W. Report where the ARROWHEAD POINTS, purely as
               you see it on the image. Do not convert it into a meteorological direction.
            6. CLOUD / VISIBILITY panel - IGNORE it.
            7. CLOUD COVER - gray area, left axis in octants (0 = clear sky, 8 = fully overcast).

            TIME AXIS:
            - Use the TOP axis, labelled CEST or CET (Polish local time). Do NOT use the bottom UTC axis.
            - Day labels above the top axis look like "Mon, 27.07" (day of month and month, no year).
            - Gray vertical bands mark the night (between sunset and sunrise).

            TASK: read one data point every 3 hours across the whole chart, from left to right.

            Return ONLY a JSON array, no markdown fences and no commentary. One object per time point:
            [{"day":"27.07","hour":15,"windMs":8.5,"gustMs":14.0,"arrowPointsTo":"SE","tempC":19.0,
              "precipitationMm":0.0,"pressureHpa":1012,"cloudCoverOctants":6}]

            Rules:
            - "day" is exactly the DD.MM label of the day that time point belongs to, "hour" is 0-23 local time.
            - Report wind and gusts in m/s as read from the chart. Do NOT convert to other units.
            - "arrowPointsTo" is one of N, NE, E, SE, S, SW, W, NW.
            - Use 0.0 when a value is zero (for example no precipitation), never null.
            """;

    private static final Type VISION_POINT_LIST_TYPE = new TypeToken<List<VisionPoint>>() {}.getType();

    private final ChatClient chatClient;
    private final OkHttpClient httpClient;
    private final Gson gson;

    public IcmForecastVisionService(ChatClient chatClient, OkHttpClient httpClient, Gson gson) {
        this.chatClient = chatClient;
        this.httpClient = httpClient;
        this.gson = gson;
    }

    public Optional<List<Forecast>> extractForecastFromMeteogram(String icmUrl) {
        try {
            byte[] imageBytes = downloadImage(toEnglishMeteogramUrl(icmUrl));
            if (imageBytes == null || imageBytes.length == 0) {
                log.warn("Failed to download ICM meteogram from {}", icmUrl);
                return Optional.empty();
            }

            String response = chatClient
                    .prompt()
                    .user(u -> u
                            .text(VISION_PROMPT)
                            .media(MimeTypeUtils.IMAGE_PNG, new ByteArrayResource(imageBytes)))
                    .stream()
                    .content()
                    .timeout(Duration.ofSeconds(60))
                    .collectList()
                    .map(chunks -> String.join("", chunks))
                    .block();

            if (response == null || response.isBlank()) {
                log.warn("Empty response from vision API for ICM meteogram");
                return Optional.empty();
            }

            List<VisionPoint> points = gson.fromJson(stripCodeFences(response), VISION_POINT_LIST_TYPE);
            if (points == null || points.isEmpty()) {
                log.warn("Parsed empty forecast list from vision API response");
                return Optional.empty();
            }

            List<Forecast> forecasts = toForecasts(points);
            if (forecasts.isEmpty()) {
                log.warn("All {} points extracted from the ICM meteogram were rejected as invalid", points.size());
                return Optional.empty();
            }

            log.info("Extracted {} forecast entries from ICM meteogram", forecasts.size());
            return Optional.of(forecasts);
        } catch (JsonSyntaxException e) {
            log.warn("Failed to parse vision API response as JSON: {}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to extract forecast from ICM meteogram: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * The stored spot URL uses lang=pl (it is also opened by the frontend), but English axis labels
     * are easier for the vision model to map onto the expected output format.
     */
    private String toEnglishMeteogramUrl(String icmUrl) {
        return icmUrl.replace("lang=pl", "lang=en");
    }

    private static String stripCodeFences(String response) {
        Matcher matcher = CODE_FENCE_PATTERN.matcher(response.strip());
        return matcher.matches() ? matcher.group(1) : response.strip();
    }

    /**
     * Converts raw chart readings into forecasts: m/s to knots, octants to percent and DD.MM labels
     * into absolute dates. Doing the conversions here instead of asking the model for them keeps the
     * vision task limited to reading values off the chart.
     */
    private List<Forecast> toForecasts(List<VisionPoint> points) {
        LocalDate today = LocalDate.now(ICM_ZONE);
        List<Forecast> forecasts = new ArrayList<>(points.size());

        for (VisionPoint point : points) {
            if (point == null || point.hour() == null || point.hour() < 0 || point.hour() > 23) {
                continue;
            }
            Optional<LocalDate> date = resolveDate(point.day(), today);
            if (date.isEmpty()) {
                continue;
            }
            double wind = toKnots(point.windMs());
            double gusts = Math.max(toKnots(point.gustMs()), wind);
            if (wind > MAX_PLAUSIBLE_KNOTS || gusts > MAX_PLAUSIBLE_KNOTS) {
                continue;
            }
            LocalDateTime dateTime = LocalDateTime.of(date.get(), LocalTime.of(point.hour(), 0));
            forecasts.add(new Forecast(
                    dateTime.format(OUTPUT_FORMATTER),
                    wind,
                    gusts,
                    toWindOrigin(point.arrowPointsTo()),
                    round(orZero(point.tempC())),
                    Math.max(0.0, round(orZero(point.precipitationMm()))),
                    toCloudCoverPercent(point.cloudCoverOctants()),
                    round(orZero(point.pressureHpa()))
            ));
        }

        forecasts.sort(Comparator.comparing(f -> LocalDateTime.parse(f.date(), OUTPUT_FORMATTER)));
        return forecasts;
    }

    /**
     * The meteogram labels days as DD.MM without a year, so the year is inferred from the current
     * date. Points more than {@value #MAX_FORECAST_DAYS_AHEAD} days away are dropped as misreadings.
     */
    private Optional<LocalDate> resolveDate(String day, LocalDate today) {
        if (day == null) {
            return Optional.empty();
        }
        Matcher matcher = DAY_MONTH_PATTERN.matcher(day.strip());
        if (!matcher.find()) {
            return Optional.empty();
        }
        int dayOfMonth = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));

        for (int year : new int[]{today.getYear(), today.getYear() + 1, today.getYear() - 1}) {
            try {
                LocalDate candidate = LocalDate.of(year, month, dayOfMonth);
                long daysFromToday = candidate.toEpochDay() - today.toEpochDay();
                if (daysFromToday >= -1 && daysFromToday <= MAX_FORECAST_DAYS_AHEAD) {
                    return Optional.of(candidate);
                }
            } catch (Exception e) {
                log.trace("Invalid ICM meteogram date {}.{}.{}", dayOfMonth, month, year);
            }
        }
        return Optional.empty();
    }

    /**
     * ICM draws the direction arrows as downwind flow vectors (an arrow pointing east means the wind
     * blows towards the east), while the rest of the app reports the direction the wind comes from,
     * so the arrow has to be reversed by 180 degrees.
     */
    private static String toWindOrigin(String arrowPointsTo) {
        if (arrowPointsTo == null) {
            return "";
        }
        int index = CARDINAL_DIRECTIONS.indexOf(arrowPointsTo.strip().toUpperCase(Locale.ENGLISH));
        if (index < 0) {
            return "";
        }
        return CARDINAL_DIRECTIONS.get((index + CARDINAL_DIRECTIONS.size() / 2) % CARDINAL_DIRECTIONS.size());
    }

    private static double toKnots(Double metersPerSecond) {
        return Math.max(0.0, round(orZero(metersPerSecond) * MS_TO_KNOTS));
    }

    private static double toCloudCoverPercent(Double octants) {
        return round(Math.clamp(orZero(octants), 0.0, 8.0) / 8.0 * 100.0);
    }

    private static double orZero(Double value) {
        return value == null ? 0.0 : value;
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private byte[] downloadImage(String url) {
        Request request = new Request.Builder().url(url).get().build();
        try (var response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().bytes();
            }
            log.warn("ICM image download failed with status {}", response.code());
            return null;
        } catch (IOException e) {
            log.warn("ICM image download failed: {}", e.getMessage());
            return null;
        }
    }

    private record VisionPoint(
            String day,
            Integer hour,
            Double windMs,
            Double gustMs,
            String arrowPointsTo,
            Double tempC,
            Double precipitationMm,
            Double pressureHpa,
            Double cloudCoverOctants
    ) {}
}
