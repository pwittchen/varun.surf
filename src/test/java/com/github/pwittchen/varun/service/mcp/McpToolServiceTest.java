package com.github.pwittchen.varun.service.mcp;

import com.github.pwittchen.varun.model.forecast.Forecast;
import com.github.pwittchen.varun.model.forecast.HourlyForecast;
import com.github.pwittchen.varun.model.forecast.WindTimeline;
import com.github.pwittchen.varun.model.spot.Spot;
import com.github.pwittchen.varun.model.spot.SpotInfo;
import com.github.pwittchen.varun.service.AggregatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpToolServiceTest {

    @Mock
    private AggregatorService aggregatorService;

    private McpToolService service;

    @BeforeEach
    void setUp() {
        service = new McpToolService(aggregatorService);
    }

    @Test
    void shouldListSpotsAsMarkdown() {
        when(aggregatorService.getSpots()).thenReturn(sampleSpots());

        String result = service.listSpots();

        assertThat(result).startsWith("# Kite spots on VARUN.SURF");
        assertThat(result).contains("Jastarnia, Poland");
        assertThat(result).contains("Podersdorf, Austria");
    }

    @Test
    void shouldListCountriesAsMarkdown() {
        when(aggregatorService.getSpots()).thenReturn(sampleSpots());

        String result = service.listCountries();

        assertThat(result).startsWith("# Countries on VARUN.SURF");
        assertThat(result).contains("Poland");
        assertThat(result).contains("Austria");
    }

    @Test
    void shouldReturnSpotMarkdownWhenWgIdMatches() {
        Spot spot = spotFor("Jastarnia", "Poland", 500760);
        when(aggregatorService.getSpotById(500760)).thenReturn(Optional.of(spot));

        String result = service.getSpot(500760);

        assertThat(result).startsWith("# Jastarnia, Poland");
        assertThat(result).contains("- Spot ID: 500760");
    }

    @Test
    void shouldReturnNotFoundMessageForUnknownWgId() {
        when(aggregatorService.getSpotById(123)).thenReturn(Optional.empty());

        String result = service.getSpot(123);

        assertThat(result).contains("No spot found for wgId=123");
    }

    @Test
    void shouldFindSpotByNameCaseInsensitive() {
        when(aggregatorService.getSpots()).thenReturn(sampleSpots());

        String result = service.findSpotByName("JASTARN");

        assertThat(result).contains("# Spots matching 'JASTARN'");
        assertThat(result).contains("Jastarnia, Poland (wgId=500760)");
        assertThat(result).doesNotContain("Podersdorf");
    }

    @Test
    void shouldReturnNoMatchesMessageWhenNothingFound() {
        when(aggregatorService.getSpots()).thenReturn(sampleSpots());

        String result = service.findSpotByName("atlantis");

        assertThat(result).isEqualTo("No spots found matching 'atlantis'.");
    }

    @Test
    void shouldRejectBlankFindQuery() {
        String result = service.findSpotByName("   ");

        assertThat(result).isEqualTo("Query must not be empty.");
    }

    @Test
    void shouldReturnSpotsByCountryMarkdown() {
        when(aggregatorService.getSpots()).thenReturn(sampleSpots());

        String result = service.getSpotsByCountry("poland");

        assertThat(result).startsWith("# Kite spots in Poland");
        assertThat(result).contains("Jastarnia");
        assertThat(result).doesNotContain("Podersdorf");
    }

    @Test
    void shouldHandleCountrySlugCaseInsensitively() {
        when(aggregatorService.getSpots()).thenReturn(sampleSpots());

        String result = service.getSpotsByCountry("POLAND");

        assertThat(result).startsWith("# Kite spots in Poland");
    }

    @Test
    void shouldReturnUnknownCountryMessageForBadSlug() {
        when(aggregatorService.getSpots()).thenReturn(sampleSpots());

        String result = service.getSpotsByCountry("atlantis");

        assertThat(result).contains("No country found for slug 'atlantis'");
    }

    @Test
    void shouldRejectBlankCountrySlug() {
        String result = service.getSpotsByCountry("");

        assertThat(result).isEqualTo("Country slug must not be empty.");
    }

    @Test
    void shouldReportStatusSummary() {
        when(aggregatorService.countSpots()).thenReturn(102);
        when(aggregatorService.countCountries()).thenReturn(13);
        when(aggregatorService.countLiveStations()).thenReturn(7);

        String result = service.getStatus();

        assertThat(result).contains("102 spots");
        assertThat(result).contains("13 countries");
        assertThat(result).contains("7 live weather stations are currently reporting");
    }

    @Test
    void shouldUseSingularStationLabelWhenOnlyOne() {
        when(aggregatorService.countSpots()).thenReturn(5);
        when(aggregatorService.countCountries()).thenReturn(2);
        when(aggregatorService.countLiveStations()).thenReturn(1);

        String result = service.getStatus();

        assertThat(result).contains("1 live weather station is currently reporting");
    }

    @Test
    void shouldReturnHourlyWindForecastForSpot() {
        Spot spot = spotFor("Jastarnia", "Poland", 500760);
        when(aggregatorService.getSpotById(500760)).thenReturn(Optional.of(spot));
        when(aggregatorService.getHourlyForecast(500760)).thenReturn(Optional.of(new HourlyForecast(
                500760,
                List.of(
                        forecastAt("Fri 26 Aug 2026 12:00", 14, 19, "W"),
                        forecastAt("Fri 26 Aug 2026 13:00", 21, 27, "NW")
                )
        )));

        String result = service.getWindForecast(500760, null, null);

        assertThat(result).startsWith("# Wind forecast for Jastarnia, Poland (wgId=500760)");
        assertThat(result).contains("Windiest hour: Fri 26 Aug 2026 13:00 at 21 kts, gusting 27 kts from NW.");
        assertThat(result).contains("Hours at or above 12 kts: 2 of 2.");
        assertThat(result).contains("| Fri 26 Aug 2026 12:00 | 14 | 19 | W |");
        assertThat(result).contains("| Fri 26 Aug 2026 13:00 | 21 | 27 | NW |");
    }

    @Test
    void shouldLimitWindForecastHoursAndFilterByMinWind() {
        Spot spot = spotFor("Jastarnia", "Poland", 500760);
        when(aggregatorService.getSpotById(500760)).thenReturn(Optional.of(spot));
        when(aggregatorService.getHourlyForecast(500760)).thenReturn(Optional.of(new HourlyForecast(
                500760,
                List.of(
                        forecastAt("Fri 26 Aug 2026 12:00", 8, 11, "W"),
                        forecastAt("Fri 26 Aug 2026 13:00", 18, 24, "NW"),
                        forecastAt("Fri 26 Aug 2026 14:00", 25, 31, "N")
                )
        )));

        String result = service.getWindForecast(500760, 2, 15);

        assertThat(result).contains("2 hours ahead");
        assertThat(result).contains("Only hours with wind of at least 15 kts are listed.");
        assertThat(result).contains("| Fri 26 Aug 2026 13:00 | 18 | 24 | NW |");
        assertThat(result).doesNotContain("12:00 | 8");
        assertThat(result).doesNotContain("14:00");
    }

    @Test
    void shouldSayNoHourReachesTheRequestedWind() {
        Spot spot = spotFor("Jastarnia", "Poland", 500760);
        when(aggregatorService.getSpotById(500760)).thenReturn(Optional.of(spot));
        when(aggregatorService.getHourlyForecast(500760)).thenReturn(Optional.of(new HourlyForecast(
                500760,
                List.of(forecastAt("Fri 26 Aug 2026 12:00", 6, 9, "W"))
        )));

        String result = service.getWindForecast(500760, null, 20);

        assertThat(result).contains("No hour in this window reaches 20 kts.");
    }

    @Test
    void shouldReportMissingSpotForWindForecast() {
        when(aggregatorService.getSpotById(123)).thenReturn(Optional.empty());

        String result = service.getWindForecast(123, null, null);

        assertThat(result).contains("No spot found for wgId=123");
    }

    @Test
    void shouldReportEmptyWindForecastForKnownSpot() {
        Spot spot = spotFor("Jastarnia", "Poland", 500760);
        when(aggregatorService.getSpotById(500760)).thenReturn(Optional.of(spot));
        when(aggregatorService.getHourlyForecast(500760))
                .thenReturn(Optional.of(new HourlyForecast(500760, List.of())));

        String result = service.getWindForecast(500760, null, null);

        assertThat(result).contains("No hourly wind forecast cached yet for Jastarnia, Poland (wgId=500760).");
    }

    @Test
    void shouldFindWindySpotsAcrossTheGrid() {
        when(aggregatorService.getSpots()).thenReturn(sampleSpots());
        when(aggregatorService.getWindTimeline(24)).thenReturn(sampleTimeline());

        String result = service.findWindySpots(null, null, null, null);

        assertThat(result).startsWith("# Spots with at least 12 kts in the next 3 hours");
        assertThat(result).contains("Grid: Fri 26 Aug 2026 12:00 to Fri 26 Aug 2026 14:00");
        assertThat(result).contains("Scanned 2 spots.");
        assertThat(result).contains("2 spots match, showing 2.");
        // Podersdorf peaks at 24 kts, Jastarnia at 18, so the stronger spot comes first
        assertThat(result.indexOf("Podersdorf")).isLessThan(result.indexOf("Jastarnia"));
        assertThat(result).contains(
                "| Jastarnia | Poland | 500760 | Fri 26 Aug 2026 13:00 | Fri 26 Aug 2026 14:00 | 2 | 18 | 24 | NW |");
    }

    @Test
    void shouldFilterWindySpotsByCountryAndMinWind() {
        when(aggregatorService.getSpots()).thenReturn(sampleSpots());
        when(aggregatorService.getWindTimeline(24)).thenReturn(sampleTimeline());

        String result = service.findWindySpots(20, null, "poland", null);

        assertThat(result).contains("Scanned 1 spot in poland.");
        assertThat(result).contains("No spot reaches 20 kts in this window.");
        assertThat(result).doesNotContain("Podersdorf");
    }

    @Test
    void shouldCapTheNumberOfWindySpotsReported() {
        when(aggregatorService.getSpots()).thenReturn(sampleSpots());
        when(aggregatorService.getWindTimeline(24)).thenReturn(sampleTimeline());

        String result = service.findWindySpots(null, null, null, 1);

        assertThat(result).contains("2 spots match, showing 1.");
        assertThat(result).contains("Podersdorf");
        assertThat(result).doesNotContain("| Jastarnia |");
    }

    @Test
    void shouldReportUnknownCountryWhenSearchingForWindySpots() {
        when(aggregatorService.getSpots()).thenReturn(sampleSpots());

        String result = service.findWindySpots(null, null, "atlantis", null);

        assertThat(result).contains("No country found for 'atlantis'");
    }

    @Test
    void shouldReportEmptyTimelineWhenNothingIsCached() {
        when(aggregatorService.getWindTimeline(24)).thenReturn(WindTimeline.EMPTY);

        String result = service.findWindySpots(null, null, null, null);

        assertThat(result).contains("No wind forecast is cached yet.");
    }

    private WindTimeline sampleTimeline() {
        return new WindTimeline(
                List.of("Fri 26 Aug 2026 12:00", "Fri 26 Aug 2026 13:00", "Fri 26 Aug 2026 14:00"),
                List.of(
                        // 6 kts is below the floor, so the window starts at 13:00
                        new WindTimeline.SpotWind(500760,
                                java.util.Arrays.asList(6, 18, 15),
                                java.util.Arrays.asList(9, 24, 21),
                                java.util.Arrays.asList(6, 7, 7)),
                        new WindTimeline.SpotWind(859182,
                                java.util.Arrays.asList(24, null, 12),
                                java.util.Arrays.asList(30, null, 16),
                                java.util.Arrays.asList(4, null, 4))
                )
        );
    }

    private Forecast forecastAt(String date, double wind, double gusts, String direction) {
        return new Forecast(date, wind, gusts, direction, 20, 0, 10, 1015);
    }

    private List<Spot> sampleSpots() {
        return List.of(
                spotFor("Jastarnia", "Poland", 500760),
                spotFor("Podersdorf", "Austria", 859182)
        );
    }

    private Spot spotFor(String name, String country, int wgId) {
        SpotInfo info = new SpotInfo("Beach", "W, SW", "18-22°C", "Intermediate",
                "sandy", "none", "Spring, Summer", "Great spot", "");
        return new Spot(
                name,
                country,
                "https://www.windguru.cz/" + wgId,
                null,
                "https://www.windfinder.com/forecast/" + name.toLowerCase(),
                "https://www.meteo.pl",
                "https://www.webcam.pl",
                "https://maps.google.com",
                null,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                null,
                null,
                null,
                null,
                info,
                null,
                null,
                null,
                "2025-01-15 14:30:00 CET"
        );
    }
}
