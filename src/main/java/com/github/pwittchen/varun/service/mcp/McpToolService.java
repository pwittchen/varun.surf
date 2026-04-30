package com.github.pwittchen.varun.service.mcp;

import com.github.pwittchen.varun.controller.LlmController;
import com.github.pwittchen.varun.model.spot.Spot;
import com.github.pwittchen.varun.service.AggregatorService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class McpToolService {

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
