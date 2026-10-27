package com.github.pwittchen.varun.service.forecast;

import com.github.pwittchen.varun.model.forecast.Forecast;
import com.github.pwittchen.varun.model.map.Coordinates;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads the ICM UM 4 km forecast from the meteo.pl meteogram API - the same JSON the
 * app.meteo.pl meteogram is drawn from.
 *
 * <p>ICM retired the old meteogram images (www.meteo.pl/um/metco/mgram_pict.php now
 * answers every grid point with a "get the new app" banner), and with them the reason
 * the ICM forecast was read off a picture by a vision model. The API serves the
 * numbers themselves, hour by hour, for any point inside the model domain, so the
 * forecast now costs one free HTTP request and no model call.
 */
@Service
public class IcmForecastService {

    private static final Logger log = LoggerFactory.getLogger(IcmForecastService.class);

    static final String DEFAULT_API_URL = "https://devmgramapi.meteo.pl";
    private static final String METEOGRAM_PAGE_URL_FORMAT = "https://app.meteo.pl/meteogram/%.4f,%.4f";

    // The 120-hour run comes out twice a day and the 60-hour one four times; the longer
    // one reaches as far as the other models do, the shorter is the fallback
    static final String LONG_RUN = "um4_120";
    static final String SHORT_RUN = "um4_60";

    // Only the countries the old grid lookup served. The UM 4 km domain is wider
    // (roughly 44.5-66.5 N, 8-30 E), but the API answers points outside it too - with
    // the nearest edge cell - so the list is what keeps a spot in Spain from getting a
    // Polish forecast
    private static final Set<String> SUPPORTED_COUNTRIES = Set.of("Poland", "Czech Republic");

    // Times are rendered in the spots' local time, like the Windguru models are
    private static final ZoneId ICM_ZONE = ZoneId.of("Europe/Warsaw");
    // Must match the format produced by WeatherForecastMapper, the frontend splits it on spaces
    private static final DateTimeFormatter OUTPUT_FORMATTER =
            DateTimeFormatter.ofPattern("EEE dd MMM yyyy HH:mm", Locale.ENGLISH);
    private static final List<String> DIRECTIONS = List.of("N", "NE", "E", "SE", "S", "SW", "W", "NW");
    private static final double MS_TO_KNOTS = 1.94384;
    // The API marks "no value" (e.g. no cloud base) with a huge negative number
    private static final double MISSING_VALUE_THRESHOLD = -1_000_000;
    // The app replaces bare nan tokens before parsing; they are not valid JSON
    private static final Pattern NAN_TOKEN = Pattern.compile("\\bnan\\b");
    private static final MediaType JSON = MediaType.get("application/json");

    private static final String WIND = "wind10_sd_true_prev_point";
    private static final String DIRECTION = "wind10_dr_deg_true_prev_point";
    private static final String GUST = "wind_gust_max";
    private static final String TEMPERATURE = "airtmp_point";
    private static final String PRECIPITATION = "pcpttl_aver";
    private static final String CLOUD_COVER = "cldtot_aver";
    private static final String PRESSURE = "slpres_point";

    private final OkHttpClient httpClient;
    private final String apiUrl;

    @Autowired
    public IcmForecastService(OkHttpClient httpClient) {
        this(httpClient, DEFAULT_API_URL);
    }

    IcmForecastService(OkHttpClient httpClient, String apiUrl) {
        this.httpClient = httpClient;
        this.apiUrl = apiUrl;
    }

    /**
     * Tells whether the ICM UM 4 km forecast covers the given country.
     */
    public boolean isCountrySupported(String country) {
        return SUPPORTED_COUNTRIES.contains(country);
    }

    /**
     * The app.meteo.pl meteogram page for a spot, or empty for a country ICM does not cover.
     */
    public Optional<String> toMeteogramUrl(Coordinates coordinates, String country) {
        if (coordinates == null || !isCountrySupported(country)) {
            return Optional.empty();
        }
        return Optional.of(String.format(Locale.ROOT, METEOGRAM_PAGE_URL_FORMAT, coordinates.lat(), coordinates.lon()));
    }

    /**
     * Fetches the hourly ICM forecast for a point from the latest model run.
     *
     * @return the forecast, or empty when no run is available or the API failed
     */
    public Optional<List<Forecast>> fetchForecast(Coordinates coordinates) {
        try {
            JsonObject available = JsonParser.parseString(get(apiUrl + "/meteorograms/available")).getAsJsonObject();
            for (String run : List.of(LONG_RUN, SHORT_RUN)) {
                Optional<Long> latestRun = latestRun(available, run);
                if (latestRun.isEmpty()) {
                    continue;
                }
                List<Forecast> forecasts = toForecasts(fetchMeteogram(run, latestRun.get(), coordinates));
                if (!forecasts.isEmpty()) {
                    log.debug("Fetched {} ICM {} entries for {}", forecasts.size(), run, coordinates);
                    return Optional.of(forecasts);
                }
            }
            log.warn("No ICM forecast available for {}", coordinates);
        } catch (IOException | JsonParseException | IllegalStateException e) {
            log.warn("Failed to fetch ICM forecast for {}: {}", coordinates, e.getMessage());
        }
        return Optional.empty();
    }

    private static Optional<Long> latestRun(JsonObject available, String run) {
        if (!available.has(run) || !available.get(run).isJsonArray()) {
            return Optional.empty();
        }
        long latest = -1;
        for (JsonElement timestamp : available.getAsJsonArray(run)) {
            latest = Math.max(latest, timestamp.getAsLong());
        }
        return latest < 0 ? Optional.empty() : Optional.of(latest);
    }

    private JsonObject fetchMeteogram(String run, long runTimestamp, Coordinates coordinates) throws IOException {
        JsonObject point = new JsonObject();
        point.addProperty("lat", String.format(Locale.ROOT, "%.4f", coordinates.lat()));
        point.addProperty("lon", String.format(Locale.ROOT, "%.4f", coordinates.lon()));
        JsonObject body = new JsonObject();
        body.add("point", point);
        body.addProperty("date", runTimestamp);

        Request request = new Request.Builder()
                .url(apiUrl + "/meteorograms/" + run)
                .post(RequestBody.create(body.toString(), JSON))
                .build();
        String response = execute(request);
        return JsonParser.parseString(NAN_TOKEN.matcher(response).replaceAll("null")).getAsJsonObject();
    }

    private String get(String url) throws IOException {
        return execute(new Request.Builder().url(url).get().build());
    }

    private String execute(Request request) throws IOException {
        try (var response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " from " + request.url());
            }
            return response.body().string();
        }
    }

    /**
     * Turns the per-variable series into hourly forecasts. Every series shares one time
     * axis, given by its first timestamp and interval; the wind series sets it here.
     */
    static List<Forecast> toForecasts(JsonObject meteogram) {
        JsonObject data = meteogram.getAsJsonObject("data");
        if (data == null || !data.has(WIND) || !data.has(DIRECTION)) {
            return List.of();
        }
        JsonObject windSeries = data.getAsJsonObject(WIND);
        long firstTimestamp = windSeries.get("first_timestamp").getAsLong();
        long interval = windSeries.get("interval").getAsLong();
        JsonArray wind = values(data, WIND);
        JsonArray direction = values(data, DIRECTION);
        JsonArray gust = values(data, GUST);
        JsonArray temperature = values(data, TEMPERATURE);
        JsonArray precipitation = values(data, PRECIPITATION);
        JsonArray cloudCover = values(data, CLOUD_COVER);
        JsonArray pressure = values(data, PRESSURE);

        List<Forecast> forecasts = new ArrayList<>(wind.size());
        for (int i = 0; i < wind.size(); i++) {
            Optional<Double> windMs = value(wind, i);
            Optional<Double> directionDeg = value(direction, i);
            if (windMs.isEmpty() || directionDeg.isEmpty()) {
                continue;
            }
            double windKts = toKnots(windMs.get());
            String date = Instant.ofEpochSecond(firstTimestamp + i * interval)
                    .atZone(ICM_ZONE)
                    .format(OUTPUT_FORMATTER);
            forecasts.add(new Forecast(
                    date,
                    windKts,
                    Math.max(windKts, value(gust, i).map(IcmForecastService::toKnots).orElse(windKts)),
                    toCardinal(directionDeg.get()),
                    round(value(temperature, i).orElse(0.0)),
                    Math.max(0.0, round(value(precipitation, i).orElse(0.0))),
                    // a 0-1 fraction, despite the "%" unit the API labels it with
                    round(Math.clamp(value(cloudCover, i).orElse(0.0), 0.0, 1.0) * 100.0),
                    round(value(pressure, i).orElse(0.0) / 100.0)
            ));
        }
        return forecasts;
    }

    private static JsonArray values(JsonObject data, String series) {
        if (!data.has(series) || !data.get(series).isJsonObject()) {
            return new JsonArray();
        }
        JsonElement values = data.getAsJsonObject(series).get("data");
        return values != null && values.isJsonArray() ? values.getAsJsonArray() : new JsonArray();
    }

    private static Optional<Double> value(JsonArray values, int index) {
        if (index >= values.size() || values.get(index).isJsonNull()) {
            return Optional.empty();
        }
        double value = values.get(index).getAsDouble();
        return Double.isFinite(value) && value > MISSING_VALUE_THRESHOLD ? Optional.of(value) : Optional.empty();
    }

    // The API gives the meteorological direction, the one the wind blows from - like Windguru
    private static String toCardinal(double degrees) {
        double normalized = (degrees % 360 + 360) % 360;
        return DIRECTIONS.get((int) Math.round(normalized / 45) % DIRECTIONS.size());
    }

    private static double toKnots(double metersPerSecond) {
        return Math.max(0.0, round(metersPerSecond * MS_TO_KNOTS));
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
