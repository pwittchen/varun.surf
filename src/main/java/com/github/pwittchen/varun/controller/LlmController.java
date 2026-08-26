package com.github.pwittchen.varun.controller;

import com.github.pwittchen.varun.model.forecast.Forecast;
import com.github.pwittchen.varun.model.forecast.HourlyForecast;
import com.github.pwittchen.varun.model.forecast.WindTimeline;
import com.github.pwittchen.varun.model.live.CurrentConditions;
import com.github.pwittchen.varun.model.live.filter.CurrentConditionsEmptyFilter;
import com.github.pwittchen.varun.model.spot.Spot;
import com.github.pwittchen.varun.model.spot.SpotInfo;
import com.github.pwittchen.varun.service.AggregatorService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/llms")
public class LlmController {

    private static final String MARKDOWN_MEDIA_TYPE = "text/markdown;charset=UTF-8";
    private static final int HOURLY_FORECAST_LIMIT = 24;

    // The wind grid runs five days out, so a single spot can be handed over whole
    // without the document turning into a wall of rows.
    private static final int DEFAULT_WIND_FORECAST_HOURS = 72;

    // A search across every spot is a different budget: one row per matching spot
    // rather than per hour, so a day ahead is the span worth scanning by default.
    private static final int DEFAULT_WINDY_SPOTS_HOURS = 24;

    // Below this a twin-tip session is not happening, which makes it the sensible
    // floor when the caller names none.
    private static final int DEFAULT_MIN_WIND = 12;

    private static final int DEFAULT_WINDY_SPOTS_LIMIT = 20;
    private static final int MAX_WINDY_SPOTS_LIMIT = 100;

    private final AggregatorService aggregatorService;

    public LlmController(AggregatorService aggregatorService) {
        this.aggregatorService = aggregatorService;
    }

    @GetMapping(value = "/spots.md", produces = MARKDOWN_MEDIA_TYPE)
    public Mono<String> spotsIndex() {
        List<Spot> spots = aggregatorService.getSpots();
        return Mono.just(renderSpotsIndex(spots));
    }

    @GetMapping(value = "/spots/{id}.md", produces = MARKDOWN_MEDIA_TYPE)
    public Mono<ResponseEntity<String>> spot(@PathVariable int id) {
        return Mono
                .justOrEmpty(aggregatorService.getSpotById(id))
                .map(spot -> ResponseEntity
                        .ok()
                        .contentType(MediaType.parseMediaType(MARKDOWN_MEDIA_TYPE))
                        .body(renderSpot(spot)))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * One spot's wind hour by hour, which is what the spot document's forecast
     * table has to cut short. The table there is capped at a day and answers "what
     * is this spot like"; this answers "when will it blow", on the same grid the
     * {@code /api/v1/forecast/{wgId}} endpoint serves.
     */
    @GetMapping(value = "/spots/{id}/wind.md", produces = MARKDOWN_MEDIA_TYPE)
    public Mono<ResponseEntity<String>> spotWind(
            @PathVariable int id,
            @RequestParam(required = false) Integer hours,
            @RequestParam(required = false) Integer minWind
    ) {
        return Mono
                .justOrEmpty(aggregatorService.getSpotById(id))
                .map(spot -> markdown(renderWindForecast(
                        spot,
                        aggregatorService.getHourlyForecast(id).orElse(null),
                        hours,
                        minWind)))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Where it will blow, across every spot at once - the Markdown reading of the
     * shared wind grid behind {@code /api/v1/wind}. Reduced to one row per spot
     * that reaches the wind asked for, so a reader picking a destination never has
     * to fetch a document per spot.
     */
    @GetMapping(value = "/wind.md", produces = MARKDOWN_MEDIA_TYPE)
    public Mono<ResponseEntity<String>> wind(
            @RequestParam(required = false) Integer minWind,
            @RequestParam(required = false) Integer hours,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) Integer limit
    ) {
        final Map<Integer, Spot> spotsByWgId = spotsByWgId(aggregatorService.getSpots(), country);
        if (hasValue(country) && spotsByWgId.isEmpty()) {
            return Mono.just(ResponseEntity.notFound().build());
        }

        final int scanHours = clamp(hours, DEFAULT_WINDY_SPOTS_HOURS, 1, Integer.MAX_VALUE);
        final WindTimeline timeline = aggregatorService.getWindTimeline(scanHours);
        return Mono.just(markdown(renderWindySpots(timeline, spotsByWgId, minWind, hours, country, limit)));
    }

    @GetMapping(value = "/countries.md", produces = MARKDOWN_MEDIA_TYPE)
    public Mono<String> countriesIndex() {
        List<Spot> spots = aggregatorService.getSpots();
        return Mono.just(renderCountriesIndex(spots));
    }

    @GetMapping(value = "/countries/{slug}.md", produces = MARKDOWN_MEDIA_TYPE)
    public Mono<ResponseEntity<String>> country(@PathVariable String slug) {
        List<Spot> spots = aggregatorService.getSpots();
        Optional<String> match = spots
                .stream()
                .map(Spot::country)
                .distinct()
                .filter(c -> toSlug(c).equals(slug.toLowerCase(Locale.ROOT)))
                .findFirst();
        return match
                .map(country -> Mono.just(ResponseEntity
                        .ok()
                        .contentType(MediaType.parseMediaType(MARKDOWN_MEDIA_TYPE))
                        .body(renderCountry(country, spots))))
                .orElseGet(() -> Mono.just(ResponseEntity.notFound().build()));
    }

    public static String renderSpotsIndex(List<Spot> spots) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Kite spots on VARUN.SURF\n\n");
        sb.append("Complete list of kite spots with live conditions and forecasts.\n");
        sb.append("Each spot has a dedicated markdown document with details, forecast and live conditions.\n\n");
        sb.append(String.format("Total: %d spots across %d countries.%n%n",
                spots.size(), countCountries(spots)));

        sb.append("## Spots\n\n");
        spots
                .stream()
                .sorted(Comparator.comparing(Spot::country).thenComparing(Spot::name))
                .forEach(spot -> sb.append(String.format("- [%s, %s](/llms/spots/%d.md)%n",
                        spot.name(), spot.country(), spot.wgId())));

        sb.append("\n## Countries\n\n");
        countriesWithCounts(spots).forEach((country, count) ->
                sb.append(String.format("- [%s](/llms/countries/%s.md) — %d %s%n",
                        country, toSlug(country), count, count == 1 ? "spot" : "spots")));

        sb.append("\n## Wind\n\n");
        sb.append("- [Where it will blow](/llms/wind.md) — every spot reaching a given wind speed "
                + "in the hours ahead. Query parameters: `minWind` (knots), `hours`, `country`, `limit`.\n");
        sb.append("- Hour-by-hour wind for one spot: /llms/spots/{wgId}/wind.md — "
                + "query parameters: `hours`, `minWind`.\n");

        return sb.toString();
    }

    public static String renderCountriesIndex(List<Spot> spots) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Countries on VARUN.SURF\n\n");
        sb.append("Kite spots grouped by country. Each country has a dedicated markdown document.\n\n");
        sb.append(String.format("Total: %d countries.%n%n", countCountries(spots)));

        countriesWithCounts(spots).forEach((country, count) ->
                sb.append(String.format("- [%s](/llms/countries/%s.md) — %d %s%n",
                        country, toSlug(country), count, count == 1 ? "spot" : "spots")));

        return sb.toString();
    }

    public static String renderCountry(String country, List<Spot> allSpots) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("# Kite spots in %s%n%n", country));
        List<Spot> countrySpots = allSpots
                .stream()
                .filter(s -> country.equals(s.country()))
                .sorted(Comparator.comparing(Spot::name))
                .toList();

        sb.append(String.format("Total: %d %s.%n%n",
                countrySpots.size(), countrySpots.size() == 1 ? "spot" : "spots"));

        sb.append("## Spots\n\n");
        countrySpots.forEach(spot ->
                sb.append(String.format("- [%s](/llms/spots/%d.md)%n", spot.name(), spot.wgId())));

        sb.append("\n## Related\n\n");
        sb.append("- [All spots](/llms/spots.md)\n");
        sb.append("- [All countries](/llms/countries.md)\n");
        return sb.toString();
    }

    public static String renderSpot(Spot spot) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("# %s, %s%n%n", spot.name(), spot.country()));

        if (spot.lastUpdated() != null && !spot.lastUpdated().isEmpty()) {
            sb.append(String.format("Last updated: %s%n%n", spot.lastUpdated()));
        }

        sb.append("## Overview\n\n");
        sb.append(String.format("- Spot ID: %d%n", spot.wgId()));
        if (spot.coordinates() != null) {
            sb.append(String.format(Locale.ROOT, "- Coordinates: %.5f, %.5f%n",
                    spot.coordinates().lat(), spot.coordinates().lon()));
        }
        appendSpotInfo(sb, spot.spotInfo());

        appendCurrentConditions(sb, spot.currentConditions());
        appendDailyForecast(sb, spot.forecast());
        appendHourlyForecast(sb, spot.forecastHourly());
        appendLinks(sb, spot);

        sb.append("\n## Related\n\n");
        sb.append(String.format("- [Hour-by-hour wind forecast](/llms/spots/%d/wind.md)%n", spot.wgId()));
        sb.append(String.format("- [Kite spots in %s](/llms/countries/%s.md)%n",
                spot.country(), toSlug(spot.country())));
        sb.append("- [All spots](/llms/spots.md)\n");
        sb.append("- [All countries](/llms/countries.md)\n");
        return sb.toString();
    }

    /**
     * One spot's wind hour by hour.
     *
     * @param spot     the spot the forecast belongs to
     * @param forecast its grid-aligned hourly forecast, null when nothing is cached
     * @param hours    how far ahead to report, null for the default span
     * @param minWind  only list hours reaching this wind speed, null for all of them
     */
    public static String renderWindForecast(Spot spot, HourlyForecast forecast, Integer hours, Integer minWind) {
        if (forecast == null || forecast.isEmpty()) {
            return String.format("No hourly wind forecast cached yet for %s, %s (wgId=%d).%n",
                    spot.name(), spot.country(), spot.wgId());
        }

        final int span = clamp(hours, DEFAULT_WIND_FORECAST_HOURS, 1, forecast.hours().size());
        final int floor = minWind == null ? 0 : Math.max(0, minWind);
        final List<Forecast> window = forecast.hours().subList(0, span);
        final List<Forecast> rows = window
                .stream()
                .filter(f -> Math.round(f.wind()) >= floor)
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("# Wind forecast for %s, %s (wgId=%d)%n%n",
                spot.name(), spot.country(), spot.wgId()));
        sb.append(String.format("Model: GFS. Hourly, starting at the current hour, %d %s ahead.%n",
                span, span == 1 ? "hour" : "hours"));
        if (floor > 0) {
            sb.append(String.format("Only hours with wind of at least %d kts are listed.%n", floor));
        }

        appendWindSummary(sb, window);

        if (rows.isEmpty()) {
            sb.append(String.format("%nNo hour in this window reaches %d kts.%n", floor));
        } else {
            sb.append("\n| Hour | Wind (kts) | Gusts (kts) | Direction |\n");
            sb.append("|------|------------|-------------|-----------|\n");
            for (Forecast f : rows) {
                sb.append(String.format(Locale.ROOT, "| %s | %.0f | %.0f | %s |%n",
                        nullSafe(f.date()), f.wind(), f.gusts(), nullSafe(f.direction())));
            }
        }

        sb.append("\n## Related\n\n");
        sb.append(String.format("- [Spot details](/llms/spots/%d.md)%n", spot.wgId()));
        sb.append("- [Where it will blow](/llms/wind.md)\n");
        return sb.toString();
    }

    /**
     * The spots that reach a given wind speed over the hours ahead, strongest
     * first. One row per spot rather than per hour: a reader choosing where to go
     * needs to know when the wind is there and how hard it gets, and can follow the
     * per-spot document for the rest.
     *
     * @param timeline    the shared wind grid
     * @param spotsByWgId the spots to consider, keyed by Windguru id
     * @param minWind     wind speed a spot must reach, null for the default floor
     * @param hours       how far ahead to scan, null for the default span
     * @param country     country the spots were filtered by, for the header only
     * @param limit       how many spots to report, null for the default
     */
    public static String renderWindySpots(
            WindTimeline timeline,
            Map<Integer, Spot> spotsByWgId,
            Integer minWind,
            Integer hours,
            String country,
            Integer limit
    ) {
        if (timeline == null || timeline.hours().isEmpty()) {
            return "No wind forecast is cached yet.\n";
        }

        final int floor = minWind == null ? DEFAULT_MIN_WIND : Math.max(1, minWind);
        final int cap = clamp(limit, DEFAULT_WINDY_SPOTS_LIMIT, 1, MAX_WINDY_SPOTS_LIMIT);
        final int span = Math.min(
                clamp(hours, DEFAULT_WINDY_SPOTS_HOURS, 1, Integer.MAX_VALUE),
                timeline.hours().size());

        final List<WindyWindow> windows = new ArrayList<>();
        int scanned = 0;

        for (WindTimeline.SpotWind spotWind : timeline.spots()) {
            Spot spot = spotsByWgId.get(spotWind.wgId());
            if (spot == null) {
                continue;
            }
            scanned++;
            WindyWindow window = toWindyWindow(spot, spotWind, span, floor);
            if (window != null) {
                windows.add(window);
            }
        }

        windows.sort(Comparator
                .comparingInt(WindyWindow::peakWind).reversed()
                .thenComparing(WindyWindow::name));

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("# Spots with at least %d kts in the next %d %s%n%n",
                floor, span, span == 1 ? "hour" : "hours"));
        sb.append(String.format("Grid: %s to %s (GFS, hourly).%n",
                timeline.hours().getFirst(), timeline.hours().get(span - 1)));
        sb.append(String.format("Scanned %d %s%s.%n",
                scanned,
                scanned == 1 ? "spot" : "spots",
                hasValue(country) ? " in " + country.trim() : ""));

        if (windows.isEmpty()) {
            sb.append(String.format("%nNo spot reaches %d kts in this window.%n", floor));
            return sb.toString();
        }

        final List<WindyWindow> reported = windows.stream().limit(cap).toList();
        sb.append(String.format("%d %s match, showing %d.%n%n",
                windows.size(), windows.size() == 1 ? "spot" : "spots", reported.size()));

        sb.append("| Spot | Country | wgId | From | To | Windy hours | Peak wind (kts) "
                + "| Peak gusts (kts) | Direction at peak |\n");
        sb.append("|------|---------|------|------|----|-------------|-----------------"
                + "|------------------|-------------------|\n");
        for (WindyWindow w : reported) {
            sb.append(String.format(Locale.ROOT, "| %s | %s | %d | %s | %s | %d | %d | %d | %s |%n",
                    w.name(), w.country(), w.wgId(),
                    timeline.hours().get(w.firstHour()),
                    timeline.hours().get(w.lastHour()),
                    w.windyHours(), w.peakWind(), w.peakGusts(), w.peakDirection()));
        }

        sb.append("\n## Related\n\n");
        sb.append("- Hour-by-hour wind for one spot: /llms/spots/{wgId}/wind.md\n");
        sb.append("- [All spots](/llms/spots.md)\n");
        return sb.toString();
    }

    /**
     * Spots keyed by Windguru id, which is how a wind series is joined back to the
     * spot it belongs to. With a country given, an empty map means the name matched
     * no country rather than that there are no spots.
     *
     * @param spots   the spots to index
     * @param country country name or slug to filter by, null or blank for all of them
     */
    public static Map<Integer, Spot> spotsByWgId(List<Spot> spots, String country) {
        final String slug = hasValue(country) ? toSlug(country.trim()) : null;
        final Map<Integer, Spot> byWgId = new HashMap<>(spots.size());
        for (Spot spot : spots) {
            if (slug != null && !toSlug(spot.country()).equals(slug)) {
                continue;
            }
            byWgId.put(spot.wgId(), spot);
        }
        return byWgId;
    }

    /**
     * The strongest stretch of one spot's grid, or null when it never reaches the
     * floor. First and last hour bracket every matching hour rather than one
     * unbroken run: a reader asking where to go wants to know the wind is there in
     * the evening even if the afternoon drops out.
     */
    private static WindyWindow toWindyWindow(Spot spot, WindTimeline.SpotWind spotWind, int span, int floor) {
        final List<Integer> wind = spotWind.wind();
        final List<Integer> gusts = spotWind.gusts();
        final List<Integer> direction = spotWind.direction();

        int first = -1;
        int last = -1;
        int windyHours = 0;
        int peakWind = 0;
        int peakGusts = 0;
        int peakHour = -1;

        for (int hour = 0; hour < span && hour < wind.size(); hour++) {
            Integer speed = wind.get(hour);
            if (speed == null || speed < floor) {
                continue;
            }
            if (first < 0) {
                first = hour;
            }
            last = hour;
            windyHours++;
            if (speed > peakWind) {
                peakWind = speed;
                peakHour = hour;
                Integer gust = hour < gusts.size() ? gusts.get(hour) : null;
                peakGusts = gust == null ? speed : gust;
            }
        }

        if (first < 0) {
            return null;
        }

        return new WindyWindow(
                spot.name(),
                spot.country(),
                spotWind.wgId(),
                first,
                last,
                windyHours,
                peakWind,
                peakGusts,
                directionAt(direction, peakHour)
        );
    }

    private static String directionAt(List<Integer> direction, int hour) {
        if (hour < 0 || hour >= direction.size()) {
            return "";
        }
        Integer index = direction.get(hour);
        if (index == null || index < 0 || index >= WindTimeline.DIRECTIONS.size()) {
            return "";
        }
        return WindTimeline.DIRECTIONS.get(index);
    }

    private static void appendWindSummary(StringBuilder sb, List<Forecast> window) {
        Forecast peak = window
                .stream()
                .max(Comparator.comparingDouble(Forecast::wind))
                .orElse(null);
        if (peak == null) {
            return;
        }
        sb.append(String.format(Locale.ROOT, "Windiest hour: %s at %.0f kts, gusting %.0f kts from %s.%n",
                nullSafe(peak.date()), peak.wind(), peak.gusts(), nullSafe(peak.direction())));

        long kiteable = window.stream().filter(f -> Math.round(f.wind()) >= DEFAULT_MIN_WIND).count();
        sb.append(String.format("Hours at or above %d kts: %d of %d.%n",
                DEFAULT_MIN_WIND, kiteable, window.size()));
    }

    private static int clamp(Integer value, int fallback, int min, int max) {
        int resolved = value == null ? fallback : value;
        return Math.max(min, Math.min(max, resolved));
    }

    private ResponseEntity<String> markdown(String body) {
        return ResponseEntity
                .ok()
                .contentType(MediaType.parseMediaType(MARKDOWN_MEDIA_TYPE))
                .body(body);
    }

    /**
     * One spot's answer to "will it blow": when the wind is there over the scanned
     * grid, and how strong it gets at its best.
     */
    private record WindyWindow(
            String name,
            String country,
            int wgId,
            int firstHour,
            int lastHour,
            int windyHours,
            int peakWind,
            int peakGusts,
            String peakDirection
    ) {
    }

    private static void appendSpotInfo(StringBuilder sb, SpotInfo info) {
        if (info == null) {
            return;
        }
        appendInfoLine(sb, "Type", info.type());
        appendInfoLine(sb, "Best wind", info.bestWind());
        appendInfoLine(sb, "Water temperature", info.waterTemp());
        appendInfoLine(sb, "Experience", info.experience());
        appendInfoLine(sb, "Launch", info.launch());
        appendInfoLine(sb, "Hazards", info.hazards());
        appendInfoLine(sb, "Season", info.season());
        if (info.description() != null && !info.description().isBlank()) {
            sb.append("\n### Description\n\n").append(info.description().trim()).append("\n");
        }
    }

    private static void appendInfoLine(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(String.format("- %s: %s%n", label, value.trim()));
        }
    }

    private static void appendCurrentConditions(StringBuilder sb, CurrentConditions conditions) {
        if (conditions == null || CurrentConditionsEmptyFilter.isEmpty(conditions)) {
            return;
        }
        sb.append("\n## Current Conditions\n\n");
        if (conditions.date() != null && !conditions.date().isBlank()) {
            sb.append(String.format("- Observed at: %s%n", conditions.date()));
        }
        sb.append(String.format("- Wind: %d kts%n", conditions.wind()));
        sb.append(String.format("- Gusts: %d kts%n", conditions.gusts()));
        if (conditions.direction() != null && !conditions.direction().isBlank()) {
            sb.append(String.format("- Direction: %s%n", conditions.direction()));
        }
        sb.append(String.format("- Temperature: %d°C%n", conditions.temp()));
    }

    private static void appendDailyForecast(StringBuilder sb, List<Forecast> daily) {
        if (daily == null || daily.isEmpty()) {
            return;
        }
        sb.append("\n## Forecast (daily)\n\n");
        appendForecastTable(sb, daily);
    }

    private static void appendHourlyForecast(StringBuilder sb, List<Forecast> hourly) {
        if (hourly == null || hourly.isEmpty()) {
            return;
        }
        sb.append(String.format("%n## Forecast (hourly, next %d entries)%n%n",
                Math.min(hourly.size(), HOURLY_FORECAST_LIMIT)));
        appendForecastTable(sb, hourly.stream().limit(HOURLY_FORECAST_LIMIT).toList());
    }

    private static void appendForecastTable(StringBuilder sb, List<Forecast> forecasts) {
        sb.append("| Date | Wind (kts) | Gusts (kts) | Direction | Temp (°C) | Precip (mm) |\n");
        sb.append("|------|------------|-------------|-----------|-----------|-------------|\n");
        for (Forecast f : forecasts) {
            sb.append(String.format(Locale.ROOT, "| %s | %.1f | %.1f | %s | %.1f | %.1f |%n",
                    nullSafe(f.date()), f.wind(), f.gusts(),
                    nullSafe(f.direction()), f.temp(), f.precipitation()));
        }
    }

    private static void appendLinks(StringBuilder sb, Spot spot) {
        boolean hasLink = hasValue(spot.windguruUrl()) || hasValue(spot.windfinderUrl())
                || hasValue(spot.icmUrl()) || hasValue(spot.webcamUrl()) || hasValue(spot.locationUrl());
        if (!hasLink) {
            return;
        }
        sb.append("\n## Links\n\n");
        sb.append(String.format("- Spot page: https://varun.surf/spot/%d%n", spot.wgId()));
        appendLinkLine(sb, "Windguru", spot.windguruUrl());
        appendLinkLine(sb, "Windfinder", spot.windfinderUrl());
        appendLinkLine(sb, "ICM forecast", spot.icmUrl());
        appendLinkLine(sb, "Webcam", spot.webcamUrl());
        appendLinkLine(sb, "Location", spot.locationUrl());
    }

    private static void appendLinkLine(StringBuilder sb, String label, String url) {
        if (hasValue(url)) {
            sb.append(String.format("- %s: %s%n", label, url));
        }
    }

    private static boolean hasValue(String s) {
        return s != null && !s.isBlank();
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static int countCountries(List<Spot> spots) {
        return (int) spots.stream().map(Spot::country).distinct().count();
    }

    private static Map<String, Long> countriesWithCounts(List<Spot> spots) {
        Map<String, Long> counts = new LinkedHashMap<>();
        spots
                .stream()
                .map(Spot::country)
                .sorted()
                .forEach(c -> counts.merge(c, 1L, Long::sum));
        return counts;
    }

    public static String toSlug(String country) {
        if (country == null) {
            return "";
        }
        return country
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", "-");
    }
}
