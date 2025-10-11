package com.github.pwittchen.varun.controller;

import com.github.pwittchen.varun.model.CurrentConditions;
import com.github.pwittchen.varun.model.Forecast;
import com.github.pwittchen.varun.model.Spot;
import com.github.pwittchen.varun.model.SpotInfo;
import com.github.pwittchen.varun.service.AggregatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpotsControllerTest {

    @Mock
    private AggregatorService aggregatorService;

    private SpotsController controller;

    @BeforeEach
    void setUp() {
        controller = new SpotsController(aggregatorService);
    }

    @Test
    void shouldReturnEmptyFluxWhenNoSpots() {
        when(aggregatorService.getSpots()).thenReturn(new ArrayList<>());

        Flux<Spot> result = controller.spots();

        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void shouldReturnSpotsFromAggregatorService() {
        List<Spot> mockSpots = createMockSpots();
        when(aggregatorService.getSpots()).thenReturn(mockSpots);

        Flux<Spot> result = controller.spots();

        StepVerifier.create(result)
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void shouldReturnSpotsWithCorrectData() {
        List<Spot> mockSpots = createMockSpots();
        when(aggregatorService.getSpots()).thenReturn(mockSpots);

        Flux<Spot> result = controller.spots();

        StepVerifier.create(result)
                .assertNext(spot -> {
                    assertThat(spot.name()).isEqualTo("Jastarnia");
                    assertThat(spot.country()).isEqualTo("Poland");
                    assertThat(spot.windguruUrl()).isEqualTo("https://www.windguru.cz/500760");
                })
                .assertNext(spot -> {
                    assertThat(spot.name()).isEqualTo("Podersdorf");
                    assertThat(spot.country()).isEqualTo("Austria");
                    assertThat(spot.windguruUrl()).isEqualTo("https://www.windguru.cz/859182");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnHealthStatusUp() {
        Mono<Map<String, String>> result = controller.health();

        StepVerifier.create(result)
                .assertNext(health -> {
                    assertThat(health).isNotNull();
                    assertThat(health).containsEntry("status", "UP");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnSpotsWithForecasts() {
        List<Spot> mockSpots = createMockSpotsWithForecasts();
        when(aggregatorService.getSpots()).thenReturn(mockSpots);

        Flux<Spot> result = controller.spots();

        StepVerifier.create(result)
                .assertNext(spot -> {
                    assertThat(spot.forecast()).isNotEmpty();
                    assertThat(spot.forecast()).hasSize(2);
                    assertThat(spot.forecast().get(0).date()).isEqualTo("Today");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnSpotsWithCurrentConditions() {
        List<Spot> mockSpots = createMockSpotsWithCurrentConditions();
        when(aggregatorService.getSpots()).thenReturn(mockSpots);

        Flux<Spot> result = controller.spots();

        StepVerifier.create(result)
                .assertNext(spot -> {
                    assertThat(spot.currentConditions()).isNotNull();
                    assertThat(spot.currentConditions().wind()).isEqualTo(15);
                    assertThat(spot.currentConditions().direction()).isEqualTo("SW");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnSpotByIdWhenSpotExists() {
        Spot mockSpot = createSingleMockSpot("Jastarnia", "Poland", 500760);
        when(aggregatorService.getSpotById(500760)).thenReturn(Optional.of(mockSpot));

        Mono<ResponseEntity<Spot>> result = controller.spot(500760);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().name()).isEqualTo("Jastarnia");
                    assertThat(response.getBody().country()).isEqualTo("Poland");
                    assertThat(response.getBody().wgId()).isEqualTo(500760);
                })
                .verifyComplete();
    }

    @Test
    void shouldReturn404WhenSpotNotFound() {
        when(aggregatorService.getSpotById(999999)).thenReturn(Optional.empty());

        Mono<ResponseEntity<Spot>> result = controller.spot(999999);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(response.getBody()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnSpotWithCompleteData() {
        Spot mockSpot = createMockSpotWithCompleteData();
        when(aggregatorService.getSpotById(500760)).thenReturn(Optional.of(mockSpot));

        Mono<ResponseEntity<Spot>> result = controller.spot(500760);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody()).isNotNull();
                    Spot spot = response.getBody();
                    assertThat(spot.name()).isEqualTo("Jastarnia");
                    assertThat(spot.country()).isEqualTo("Poland");
                    assertThat(spot.windguruUrl()).isEqualTo("https://www.windguru.cz/500760");
                    assertThat(spot.forecast()).isNotEmpty();
                    assertThat(spot.forecast()).hasSize(2);
                    assertThat(spot.currentConditions()).isNotNull();
                    assertThat(spot.spotInfo()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void shouldExtractCorrectWgIdFromUrl() {
        Spot mockSpot = createSingleMockSpot("Podersdorf", "Austria", 859182);
        when(aggregatorService.getSpotById(859182)).thenReturn(Optional.of(mockSpot));

        Mono<ResponseEntity<Spot>> result = controller.spot(859182);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().wgId()).isEqualTo(859182);
                    assertThat(response.getBody().name()).isEqualTo("Podersdorf");
                })
                .verifyComplete();
    }

    private List<Spot> createMockSpots() {
        SpotInfo spotInfo1 = new SpotInfo("Beach", "W, SW", "18-22°C", "Intermediate", "sandy", "none", "Spring, Summer", "Great spot");
        SpotInfo spotInfo2 = new SpotInfo("Lake", "N, NW", "20-24°C", "Beginner", "grass", "rocks", "Summer", "Flat water");

        Spot spot1 = new Spot(
                "Jastarnia",
                "Poland",
                "https://www.windguru.cz/500760",
                "https://www.windfinder.com/forecast/jastarnia",
                "https://www.meteo.pl",
                "https://www.webcam.pl",
                "https://maps.google.com",
                null,
                new ArrayList<>(),
                new ArrayList<>(),
                null,
                spotInfo1,
                "2025-01-15 14:30:00 CET"
        );

        Spot spot2 = new Spot(
                "Podersdorf",
                "Austria",
                "https://www.windguru.cz/859182",
                "https://www.windfinder.com/forecast/podersdorf",
                "https://www.zamg.ac.at",
                "https://www.webcam.at",
                "https://maps.google.com",
                null,
                new ArrayList<>(),
                new ArrayList<>(),
                null,
                spotInfo2,
                "2025-01-15 14:30:00 CET"
        );

        return List.of(spot1, spot2);
    }

    private List<Spot> createMockSpotsWithForecasts() {
        SpotInfo spotInfo = new SpotInfo("Beach", "W, SW", "18-22°C", "Intermediate", "sandy", "none", "Spring, Summer", "Great spot");
        List<Forecast> forecasts = List.of(
                new Forecast("Today", 12.5, 18.3, "SW", 15.0, 0.5),
                new Forecast("Tomorrow", 10.0, 15.0, "W", 14.0, 1.0)
        );

        Spot spot = new Spot(
                "Jastarnia",
                "Poland",
                "https://www.windguru.cz/500760",
                "https://www.windfinder.com/forecast/jastarnia",
                "https://www.meteo.pl",
                "https://www.webcam.pl",
                "https://maps.google.com",
                null,
                forecasts,
                new ArrayList<>(),
                null,
                spotInfo,
                "2025-01-15 14:30:00 CET"
        );

        return List.of(spot);
    }

    private List<Spot> createMockSpotsWithCurrentConditions() {
        SpotInfo spotInfo = new SpotInfo("Beach", "W, SW", "18-22°C", "Intermediate", "sandy", "none", "Spring, Summer", "Great spot");
        CurrentConditions currentConditions = new CurrentConditions(
                "2025-01-15 14:30",
                15,
                20,
                "SW",
                18
        );

        Spot spot = new Spot(
                "Jastarnia",
                "Poland",
                "https://www.windguru.cz/500760",
                "https://www.windfinder.com/forecast/jastarnia",
                "https://www.meteo.pl",
                "https://www.webcam.pl",
                "https://maps.google.com",
                currentConditions,
                new ArrayList<>(),
                new ArrayList<>(),
                null,
                spotInfo,
                "2025-01-15 14:30:00 CET"
        );

        return List.of(spot);
    }

    private Spot createSingleMockSpot(String name, String country, int wgId) {
        SpotInfo spotInfo = new SpotInfo("Beach", "W, SW", "18-22°C", "Intermediate", "sandy", "none", "Spring, Summer", "Great spot");

        return new Spot(
                name,
                country,
                "https://www.windguru.cz/" + wgId,
                "https://www.windfinder.com/forecast/" + name.toLowerCase(),
                "https://www.meteo.pl",
                "https://www.webcam.pl",
                "https://maps.google.com",
                null,
                new ArrayList<>(),
                new ArrayList<>(),
                null,
                spotInfo,
                "2025-01-15 14:30:00 CET"
        );
    }

    private Spot createMockSpotWithCompleteData() {
        SpotInfo spotInfo = new SpotInfo("Beach", "W, SW", "18-22°C", "Intermediate", "sandy", "none", "Spring, Summer", "Great spot");

        List<Forecast> forecasts = List.of(
                new Forecast("Today", 12.5, 18.3, "SW", 15.0, 0.5),
                new Forecast("Tomorrow", 10.0, 15.0, "W", 14.0, 1.0)
        );

        CurrentConditions currentConditions = new CurrentConditions(
                "2025-01-15 14:30",
                15,
                20,
                "SW",
                18
        );

        return new Spot(
                "Jastarnia",
                "Poland",
                "https://www.windguru.cz/500760",
                "https://www.windfinder.com/forecast/jastarnia",
                "https://www.meteo.pl",
                "https://www.webcam.pl",
                "https://maps.google.com",
                currentConditions,
                forecasts,
                new ArrayList<>(),
                null,
                spotInfo,
                "2025-01-15 14:30:00 CET"
        );
    }
}
