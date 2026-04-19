package com.github.pwittchen.varun.controller;

import com.github.pwittchen.varun.model.forecast.Forecast;
import com.github.pwittchen.varun.model.live.CurrentConditions;
import com.github.pwittchen.varun.model.map.Coordinates;
import com.github.pwittchen.varun.model.spot.Spot;
import com.github.pwittchen.varun.model.spot.SpotInfo;
import com.github.pwittchen.varun.service.AggregatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmControllerTest {

    @Mock
    private AggregatorService aggregatorService;

    private LlmController controller;

    @BeforeEach
    void setUp() {
        controller = new LlmController(aggregatorService);
    }

    @Test
    void shouldRenderSpotsIndex() {
        when(aggregatorService.getSpots()).thenReturn(sampleSpots());

        Mono<String> result = controller.spotsIndex();

        StepVerifier.create(result)
                .assertNext(body -> {
                    assertThat(body).startsWith("# Kite spots on VARUN.SURF");
                    assertThat(body).contains("Total: 2 spots across 2 countries.");
                    assertThat(body).contains("[Jastarnia, Poland](/llms/spots/500760.md)");
                    assertThat(body).contains("[Podersdorf, Austria](/llms/spots/859182.md)");
                    assertThat(body).contains("[Poland](/llms/countries/poland.md)");
                    assertThat(body).contains("[Austria](/llms/countries/austria.md)");
                })
                .verifyComplete();
    }

    @Test
    void shouldRenderCountriesIndex() {
        when(aggregatorService.getSpots()).thenReturn(sampleSpots());

        Mono<String> result = controller.countriesIndex();

        StepVerifier.create(result)
                .assertNext(body -> {
                    assertThat(body).startsWith("# Countries on VARUN.SURF");
                    assertThat(body).contains("Total: 2 countries.");
                    assertThat(body).contains("[Austria](/llms/countries/austria.md) — 1 spot");
                    assertThat(body).contains("[Poland](/llms/countries/poland.md) — 1 spot");
                })
                .verifyComplete();
    }

    @Test
    void shouldRenderCountryMarkdown() {
        when(aggregatorService.getSpots()).thenReturn(sampleSpots());

        Mono<ResponseEntity<String>> result = controller.country("poland");

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    String body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body).startsWith("# Kite spots in Poland");
                    assertThat(body).contains("- [Jastarnia](/llms/spots/500760.md)");
                    assertThat(body).doesNotContain("Podersdorf");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturn404ForUnknownCountrySlug() {
        when(aggregatorService.getSpots()).thenReturn(sampleSpots());

        Mono<ResponseEntity<String>> result = controller.country("atlantis");

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(response.getBody()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void shouldMatchCountrySlugCaseInsensitively() {
        when(aggregatorService.getSpots()).thenReturn(sampleSpots());

        Mono<ResponseEntity<String>> result = controller.country("POLAND");

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody()).startsWith("# Kite spots in Poland");
                })
                .verifyComplete();
    }

    @Test
    void shouldRenderSingleSpotMarkdown() {
        Spot spot = fullySpecifiedSpot();
        when(aggregatorService.getSpotById(500760)).thenReturn(Optional.of(spot));

        Mono<ResponseEntity<String>> result = controller.spot(500760);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    String body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body).startsWith("# Jastarnia, Poland");
                    assertThat(body).contains("Last updated: 2025-01-15 14:30:00 CET");
                    assertThat(body).contains("## Overview");
                    assertThat(body).contains("- Spot ID: 500760");
                    assertThat(body).contains("- Coordinates: 54.69851, 18.67715");
                    assertThat(body).contains("- Best wind: W, SW");
                    assertThat(body).contains("### Description");
                    assertThat(body).contains("## Current Conditions");
                    assertThat(body).contains("- Wind: 15 kts");
                    assertThat(body).contains("- Direction: SW");
                    assertThat(body).contains("## Forecast (daily)");
                    assertThat(body).contains("| Today | 12.5 | 18.3 | SW | 15.0 | 0.5 |");
                    assertThat(body).contains("## Links");
                    assertThat(body).contains("https://www.windguru.cz/500760");
                    assertThat(body).contains("https://varun.surf/spot/500760");
                    assertThat(body).contains("[Kite spots in Poland](/llms/countries/poland.md)");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturn404WhenSpotNotFound() {
        when(aggregatorService.getSpotById(999999)).thenReturn(Optional.empty());

        Mono<ResponseEntity<String>> result = controller.spot(999999);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(response.getBody()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void shouldOmitLiveConditionsWhenEmpty() {
        Spot spot = spotWithoutConditions();
        when(aggregatorService.getSpotById(500760)).thenReturn(Optional.of(spot));

        Mono<ResponseEntity<String>> result = controller.spot(500760);

        StepVerifier.create(result)
                .assertNext(response -> {
                    String body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body).doesNotContain("## Current Conditions");
                    assertThat(body).doesNotContain("## Forecast (daily)");
                })
                .verifyComplete();
    }

    @Test
    void shouldConvertCountryWithSpacesToSlug() {
        assertThat(LlmController.toSlug("Czech Republic")).isEqualTo("czech-republic");
        assertThat(LlmController.toSlug("United Kingdom")).isEqualTo("united-kingdom");
        assertThat(LlmController.toSlug("Poland")).isEqualTo("poland");
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

    private Spot fullySpecifiedSpot() {
        SpotInfo info = new SpotInfo("Beach", "W, SW", "18-22°C", "Intermediate",
                "sandy", "none", "Spring, Summer", "Flat water lagoon with shallow water", "");
        List<Forecast> daily = List.of(
                new Forecast("Today", 12.5, 18.3, "SW", 15.0, 0.5, 0, 0),
                new Forecast("Tomorrow", 10.0, 15.0, "W", 14.0, 1.0, 0, 0)
        );
        CurrentConditions conditions = new CurrentConditions("2025-01-15 14:30", 15, 20, "SW", 18);

        return new Spot(
                "Jastarnia",
                "Poland",
                "https://www.windguru.cz/500760",
                null,
                "https://www.windfinder.com/forecast/jastarnia",
                "https://www.meteo.pl",
                "https://www.webcam.pl",
                "https://maps.google.com",
                conditions,
                new ArrayList<>(),
                daily,
                new ArrayList<>(),
                null,
                null,
                null,
                new Coordinates(54.69851, 18.67715),
                info,
                null,
                null,
                null,
                "2025-01-15 14:30:00 CET"
        );
    }

    private Spot spotWithoutConditions() {
        SpotInfo info = new SpotInfo("Beach", "W, SW", "18-22°C", "Intermediate",
                "sandy", "none", "Spring, Summer", "Great spot", "");
        return new Spot(
                "Jastarnia",
                "Poland",
                "https://www.windguru.cz/500760",
                null,
                "https://www.windfinder.com/forecast/jastarnia",
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
