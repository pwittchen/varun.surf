package com.github.pwittchen.varun.controller;

import com.github.pwittchen.varun.model.forecast.Forecast;
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
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Comparator;
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

    static String renderSpotsIndex(List<Spot> spots) {
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

        return sb.toString();
    }

    static String renderCountriesIndex(List<Spot> spots) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Countries on VARUN.SURF\n\n");
        sb.append("Kite spots grouped by country. Each country has a dedicated markdown document.\n\n");
        sb.append(String.format("Total: %d countries.%n%n", countCountries(spots)));

        countriesWithCounts(spots).forEach((country, count) ->
                sb.append(String.format("- [%s](/llms/countries/%s.md) — %d %s%n",
                        country, toSlug(country), count, count == 1 ? "spot" : "spots")));

        return sb.toString();
    }

    static String renderCountry(String country, List<Spot> allSpots) {
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

    static String renderSpot(Spot spot) {
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
        sb.append(String.format("- [Kite spots in %s](/llms/countries/%s.md)%n",
                spot.country(), toSlug(spot.country())));
        sb.append("- [All spots](/llms/spots.md)\n");
        sb.append("- [All countries](/llms/countries.md)\n");
        return sb.toString();
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

    static String toSlug(String country) {
        if (country == null) {
            return "";
        }
        return country
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", "-");
    }
}
