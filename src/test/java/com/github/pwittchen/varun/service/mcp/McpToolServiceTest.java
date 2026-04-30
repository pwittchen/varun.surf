package com.github.pwittchen.varun.service.mcp;

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
