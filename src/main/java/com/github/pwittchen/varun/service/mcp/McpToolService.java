package com.github.pwittchen.varun.service.mcp;

import com.github.pwittchen.varun.controller.LlmController;
import com.github.pwittchen.varun.model.forecast.WindTimeline;
import com.github.pwittchen.varun.model.spot.Spot;
import com.github.pwittchen.varun.service.AggregatorService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class McpToolService {

    // How far ahead find_windy_spots asks the grid to reach when the caller names
    // no span. Everything else about the two wind tools - the defaults, the floor,
    // the layout - lives with the Markdown they share with the /llms endpoints.
    private static final int DEFAULT_WINDY_SPOTS_HOURS = 24;

    private final AggregatorService aggregatorService;

    public McpToolService(@Lazy AggregatorService aggregatorService) {
        this.aggregatorService = aggregatorService;
    }

    @Tool(
            name = "list_spots",
            description = "List all kite spots tracked by varun.surf, grouped by country. "
                    + "Returns a Markdown index with spot names, countries, Windguru spot IDs (wgId), "
                    + "and per-spot document links. Use this to discover available spots."
    )
    public String listSpots() {
        return LlmController.renderSpotsIndex(aggregatorService.getSpots());
    }

    @Tool(
            name = "get_spot",
            description = "Get full details for a single kite spot identified by its Windguru spot ID (wgId). "
                    + "Returns Markdown including overview, current wind conditions (when available), "
                    + "daily and hourly forecasts, and external links (Windguru, Windfinder, ICM, webcam, location)."
    )
    public String getSpot(
            @ToolParam(description = "Windguru spot ID (integer wgId), e.g. 500760 for Jastarnia")
            int wgId
    ) {
        Optional<Spot> spot = aggregatorService.getSpotById(wgId);
        return spot
                .map(LlmController::renderSpot)
                .orElseGet(() -> "No spot found for wgId=" + wgId
                        + ". Use list_spots or find_spot_by_name to discover available spots.");
    }

    @Tool(
            name = "find_spot_by_name",
            description = "Find kite spots whose name contains the given query (case-insensitive). "
                    + "Returns a Markdown list of matches with their Windguru spot IDs (wgId). "
                    + "Use the wgId of a match with get_spot to fetch full details."
    )
    public String findSpotByName(
            @ToolParam(description = "Substring to search for in spot names, e.g. 'jastarnia' or 'tarifa'")
            String query
    ) {
        if (query == null || query.isBlank()) {
            return "Query must not be empty.";
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<Spot> matches = aggregatorService
                .getSpots()
                .stream()
                .filter(spot -> spot.name() != null
                        && spot.name().toLowerCase(Locale.ROOT).contains(needle))
                .toList();

        if (matches.isEmpty()) {
            return "No spots found matching '" + query + "'.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Spots matching '").append(query).append("'\n\n");
        for (Spot spot : matches) {
            sb.append(String.format("- %s, %s (wgId=%d)%n",
                    spot.name(), spot.country(), spot.wgId()));
        }
        return sb.toString();
    }

    @Tool(
            name = "list_countries",
            description = "List all countries that have at least one kite spot, with the number of spots per country. "
                    + "Returns Markdown. Use get_spots_by_country with the country slug to list spots in a country."
    )
    public String listCountries() {
        return LlmController.renderCountriesIndex(aggregatorService.getSpots());
    }

    @Tool(
            name = "get_spots_by_country",
            description = "List all kite spots in a country identified by its slug "
                    + "(lowercased country name with spaces replaced by hyphens, e.g. 'poland', 'czech-republic'). "
                    + "Returns Markdown."
    )
    public String getSpotsByCountry(
            @ToolParam(description = "Country slug, e.g. 'poland', 'austria', 'spain'")
            String slug
    ) {
        if (slug == null || slug.isBlank()) {
            return "Country slug must not be empty.";
        }
        String normalized = slug.trim().toLowerCase(Locale.ROOT);
        List<Spot> spots = aggregatorService.getSpots();
        Optional<String> match = spots
                .stream()
                .map(Spot::country)
                .distinct()
                .filter(c -> LlmController.toSlug(c).equals(normalized))
                .findFirst();
        return match
                .map(country -> LlmController.renderCountry(country, spots))
                .orElseGet(() -> "No country found for slug '" + slug
                        + "'. Use list_countries to see available countries.");
    }

    @Tool(
            name = "get_wind_forecast",
            description = "Get the hour-by-hour wind forecast for a single kite spot identified by its "
                    + "Windguru spot ID (wgId). Returns a Markdown table with wind speed, gusts and wind "
                    + "direction for every hour, starting at the current hour, plus a summary naming the "
                    + "windiest hour and the kiteable windows. Use this instead of get_spot when the "
                    + "question is about when it will blow and how strongly, rather than about the spot itself."
    )
    public String getWindForecast(
            @ToolParam(description = "Windguru spot ID (integer wgId), e.g. 500760 for Jastarnia")
            int wgId,
            @ToolParam(required = false, description =
                    "How many hours ahead to return. Defaults to 72, and is capped by how far the forecast reaches.")
            Integer hours,
            @ToolParam(required = false, description =
                    "Only report hours with at least this wind speed in knots. Omit to get every hour.")
            Integer minWind
    ) {
        Optional<Spot> match = aggregatorService.getSpotById(wgId);
        if (match.isEmpty()) {
            return "No spot found for wgId=" + wgId
                    + ". Use list_spots or find_spot_by_name to discover available spots.";
        }

        return LlmController.renderWindForecast(
                match.get(),
                aggregatorService.getHourlyForecast(wgId).orElse(null),
                hours,
                minWind
        );
    }

    @Tool(
            name = "find_windy_spots",
            description = "Find the kite spots that will have wind in the hours ahead, across every spot at once. "
                    + "Scans the shared hourly wind grid and returns a Markdown table of the spots whose forecast "
                    + "reaches the given wind speed, with the hours the window runs over, the peak wind and gusts, "
                    + "and the wind direction at the peak. Use this to answer 'where should I go' questions instead "
                    + "of calling get_spot spot by spot."
    )
    public String findWindySpots(
            @ToolParam(required = false, description =
                    "Minimum wind speed in knots a spot must reach to be reported. Defaults to 12.")
            Integer minWind,
            @ToolParam(required = false, description =
                    "How many hours ahead to scan. Defaults to 24, and is capped by how far the forecast reaches.")
            Integer hours,
            @ToolParam(required = false, description =
                    "Only look at spots in this country, by name or slug, e.g. 'Poland' or 'czech-republic'. "
                            + "Omit to scan every country.")
            String country,
            @ToolParam(required = false, description =
                    "How many spots to report, strongest wind first. Defaults to 20, at most 100.")
            Integer limit
    ) {
        final Map<Integer, Spot> spotsByWgId =
                LlmController.spotsByWgId(aggregatorService.getSpots(), country);
        if (country != null && !country.isBlank() && spotsByWgId.isEmpty()) {
            return "No country found for '" + country + "'. Use list_countries to see available countries.";
        }

        final WindTimeline timeline = aggregatorService.getWindTimeline(
                hours == null ? DEFAULT_WINDY_SPOTS_HOURS : Math.max(1, hours));

        return LlmController.renderWindySpots(timeline, spotsByWgId, minWind, hours, country, limit);
    }

    @Tool(
            name = "get_status",
            description = "Get a short summary of the varun.surf service: number of spots, countries, "
                    + "and active live weather stations currently reporting wind."
    )
    public String getStatus() {
        int spots = aggregatorService.countSpots();
        int countries = aggregatorService.countCountries();
        int liveStations = aggregatorService.countLiveStations();
        return String.format(
                "varun.surf is tracking %d spots across %d countries. %d live weather %s currently reporting.",
                spots,
                countries,
                liveStations,
                liveStations == 1 ? "station is" : "stations are"
        );
    }

}
