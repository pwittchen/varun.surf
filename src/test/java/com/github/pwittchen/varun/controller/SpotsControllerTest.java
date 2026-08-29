package com.github.pwittchen.varun.controller;

import com.github.pwittchen.varun.metrics.SpotsControllerMetrics;
import com.github.pwittchen.varun.model.live.CurrentConditions;
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
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class SpotsControllerTest {

    @Mock
    private AggregatorService aggregatorService;

    @Mock
    private SpotsControllerMetrics metrics;

    private SpotsController controller;

    @BeforeEach
    void setUp() {
        controller = new SpotsController(aggregatorService, metrics);
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

    @Test
    void shouldReturnSpotByIdAndModelWhenSpotExists() {
        Spot mockSpot = createSingleMockSpot("Jastarnia", "Poland", 500760);
        when(aggregatorService.getSpotById(eq(500760), eq("gfs"))).thenReturn(Optional.of(mockSpot));

        Mono<ResponseEntity<Spot>> result = controller.spot(500760, "gfs");

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().name()).isEqualTo("Jastarnia");
                    assertThat(response.getBody().country()).isEqualTo("Poland");
                    assertThat(response.getBody().wgId()).isEqualTo(500760);
                })
                .verifyComplete();

        verify(aggregatorService).fetchForecastsForAllModels(500760);
    }

    @Test
    void shouldReturnSpotWithIfsModel() {
        Spot mockSpot = createSingleMockSpot("Jastarnia", "Poland", 500760);
        when(aggregatorService.getSpotById(eq(500760), eq("ifs"))).thenReturn(Optional.of(mockSpot));

        Mono<ResponseEntity<Spot>> result = controller.spot(500760, "ifs");

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody()).isNotNull();
                })
                .verifyComplete();

        verify(aggregatorService).fetchForecastsForAllModels(500760);
    }

    @Test
    void shouldReturn404WhenSpotNotFoundWithModel() {
        when(aggregatorService.getSpotById(eq(999999), eq("gfs"))).thenReturn(Optional.empty());

        Mono<ResponseEntity<Spot>> result = controller.spot(999999, "gfs");

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(response.getBody()).isNull();
                })
                .verifyComplete();

        verify(aggregatorService).fetchForecastsForAllModels(999999);
    }

    @Test
    void shouldHandleUpperCaseModelParameter() {
        Spot mockSpot = createSingleMockSpot("Jastarnia", "Poland", 500760);
        when(aggregatorService.getSpotById(eq(500760), eq("GFS"))).thenReturn(Optional.of(mockSpot));

        Mono<ResponseEntity<Spot>> result = controller.spot(500760, "GFS");

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                })
                .verifyComplete();
    }

    @Test
    void shouldFallbackToGfsForInvalidModelParameter() {
        Spot mockSpot = createSingleMockSpot("Jastarnia", "Poland", 500760);
        when(aggregatorService.getSpotById(eq(500760), eq("invalid"))).thenReturn(Optional.of(mockSpot));

        Mono<ResponseEntity<Spot>> result = controller.spot(500760, "invalid");

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                })
                .verifyComplete();

        verify(aggregatorService).getSpotById(eq(500760), eq("invalid"));
    }

    @Test
    void shouldTriggerBackgroundFetchForAllModels() {
        Spot mockSpot = createSingleMockSpot("Jastarnia", "Poland", 500760);
        when(aggregatorService.getSpotById(eq(500760), eq("gfs"))).thenReturn(Optional.of(mockSpot));

        controller.spot(500760, "gfs").block();

        verify(aggregatorService, times(1)).fetchForecastsForAllModels(500760);
    }

    @Test
    void shouldReturnSpotWithAverageModel() {
        Spot mockSpot = createSingleMockSpot("Jastarnia", "Poland", 500760);
        when(aggregatorService.getSpotById(eq(500760), eq("average"))).thenReturn(Optional.of(mockSpot));

        Mono<ResponseEntity<Spot>> result = controller.spot(500760, "average");

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().name()).isEqualTo("Jastarnia");
                })
                .verifyComplete();

        verify(aggregatorService).getSpotById(eq(500760), eq("average"));
        verify(aggregatorService).fetchForecastsForAllModels(500760);
    }

    private List<Spot> createMockSpots() {
        SpotInfo spotInfo1 = new SpotInfo("Beach", "W, SW", "18-22°C", "Intermediate", "sandy", "none", "Spring, Summer", "Great spot", "");
        SpotInfo spotInfo2 = new SpotInfo("Lake", "N, NW", "20-24°C", "Beginner", "grass", "rocks", "Summer", "Flat water", "");

        Spot spot1 = new Spot(
                "Jastarnia",
                "Poland",
                "https://www.windguru.cz/500760",
                null, // windguruFallbackUrl
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
                spotInfo1,
                null,
                null,
                null,
                "2025-01-15 14:30:00 CET"
        );

        Spot spot2 = new Spot(
                "Podersdorf",
                "Austria",
                "https://www.windguru.cz/859182",
                null, // windguruFallbackUrl
                "https://www.windfinder.com/forecast/podersdorf",
                "https://www.zamg.ac.at",
                "https://www.webcam.at",
                "https://maps.google.com",
                null,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                null,
                null,
                null,
                null,
                spotInfo2,
                null,
                null,
                null,
                "2025-01-15 14:30:00 CET"
        );

        return List.of(spot1, spot2);
    }

    private List<Spot> createMockSpotsWithForecasts() {
        SpotInfo spotInfo = new SpotInfo("Beach", "W, SW", "18-22°C", "Intermediate", "sandy", "none", "Spring, Summer", "Great spot", "");
        List<Forecast> forecasts = List.of(
                new Forecast("Today", 12.5, 18.3, "SW", 15.0, 0.5, 0, 0),
                new Forecast("Tomorrow", 10.0, 15.0, "W", 14.0, 1.0, 0, 0)
        );

        Spot spot = new Spot(
                "Jastarnia",
                "Poland",
                "https://www.windguru.cz/500760",
                null, // windguruFallbackUrl
                "https://www.windfinder.com/forecast/jastarnia",
                "https://www.meteo.pl",
                "https://www.webcam.pl",
                "https://maps.google.com",
                null,
                new ArrayList<>(),
                forecasts,
                new ArrayList<>(),
                null,
                null,
                null,
                null,
                spotInfo,
                null,
                null,
                null,
                "2025-01-15 14:30:00 CET"
        );

        return List.of(spot);
    }

    private List<Spot> createMockSpotsWithCurrentConditions() {
        SpotInfo spotInfo = new SpotInfo("Beach", "W, SW", "18-22°C", "Intermediate", "sandy", "none", "Spring, Summer", "Great spot", "");
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
                null, // windguruFallbackUrl
                "https://www.windfinder.com/forecast/jastarnia",
                "https://www.meteo.pl",
                "https://www.webcam.pl",
                "https://maps.google.com",
                currentConditions,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                null,
                null,
                null,
                null,
                spotInfo,
                null,
                null,
                null,
                "2025-01-15 14:30:00 CET"
        );

        return List.of(spot);
    }

    private Spot createSingleMockSpot(String name, String country, int wgId) {
        SpotInfo spotInfo = new SpotInfo("Beach", "W, SW", "18-22°C", "Intermediate", "sandy", "none", "Spring, Summer", "Great spot", "");

        return new Spot(
                name,
                country,
                "https://www.windguru.cz/" + wgId,
                null, // windguruFallbackUrl
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
                spotInfo,
                null,
                null,
                null,
                "2025-01-15 14:30:00 CET"
        );
    }

    @Test
    void shouldReturnWindTimelineFromAggregatorService() {
        WindTimeline timeline = new WindTimeline(
                List.of("Tue 28 Oct 2025 14:00", "Tue 28 Oct 2025 15:00"),
                List.of(new WindTimeline.SpotWind(500760, List.of(12, 14), List.of(16, 18), List.of(5, 6)))
        );
        when(aggregatorService.getWindTimeline()).thenReturn(timeline);

        StepVerifier.create(controller.wind(null))
                .assertNext(result -> {
                    assertThat(result.hours()).hasSize(2);
                    assertThat(result.spots()).hasSize(1);
                    assertThat(result.spots().getFirst().wgId()).isEqualTo(500760);
                    assertThat(result.spots().getFirst().wind()).containsExactly(12, 14).inOrder();
                })
                .verifyComplete();

        verify(metrics, times(1)).incrementWindRequestCounter();
    }

    @Test
    void shouldReturnWindTimelineOfTheRequestedLength() {
        WindTimeline timeline = new WindTimeline(
                List.of("Tue 28 Oct 2025 14:00"),
                List.of(new WindTimeline.SpotWind(500760, List.of(12), List.of(16), List.of(5)))
        );
        when(aggregatorService.getWindTimeline(384)).thenReturn(timeline);

        StepVerifier.create(controller.wind(384))
                .assertNext(result -> assertThat(result.hours()).hasSize(1))
                .verifyComplete();

        verify(aggregatorService, times(1)).getWindTimeline(384);
        verify(aggregatorService, never()).getWindTimeline();
    }

    @Test
    void shouldReturnEmptyWindTimelineWhenNoForecastsAreCached() {
        when(aggregatorService.getWindTimeline()).thenReturn(new WindTimeline(List.of(), List.of()));

        StepVerifier.create(controller.wind(null))
                .assertNext(result -> {
                    assertThat(result.hours()).isEmpty();
                    assertThat(result.spots()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnHourlyForecastForSingleSpot() {
        HourlyForecast hourly = new HourlyForecast(500760, List.of(
                new Forecast("Tue 28 Oct 2025 14:00", 12, 16, "NW", 21, 0.4, 40, 1013, 0.8, 4.0, "SW"),
                new Forecast("Tue 28 Oct 2025 15:00", 14, 18, "N", 20, 0.0, 20, 1014, 0.6, 4.0, "SW")
        ));
        when(aggregatorService.getHourlyForecast(500760)).thenReturn(Optional.of(hourly));

        StepVerifier.create(controller.forecast(500760))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().wgId()).isEqualTo(500760);
                    assertThat(response.getBody().hours()).hasSize(2);

                    // the single-spot response carries every field, not just wind
                    Forecast first = response.getBody().hours().getFirst();
                    assertThat(first.temp()).isEqualTo(21.0);
                    assertThat(first.precipitation()).isEqualTo(0.4);
                    assertThat(first.cloudCoverPercent()).isEqualTo(40.0);
                    assertThat(first.pressureHpa()).isEqualTo(1013.0);
                    assertThat(first.wave()).isEqualTo(0.8);
                })
                .verifyComplete();

        verify(metrics, times(1)).incrementForecastRequestCounter();
    }

    @Test
    void shouldReturnNotFoundForUnknownSpotHourlyForecast() {
        when(aggregatorService.getHourlyForecast(999999)).thenReturn(Optional.empty());

        StepVerifier.create(controller.forecast(999999))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(response.getBody()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyHourlyForecastForSpotWithoutCachedForecast() {
        // A known spot with nothing fetched yet answers 200 with no hours, which is
        // a different answer from "no such spot"
        when(aggregatorService.getHourlyForecast(500760))
                .thenReturn(Optional.of(new HourlyForecast(500760, List.of())));

        StepVerifier.create(controller.forecast(500760))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().hours()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void shouldGenerateAiAnalysisInAllLanguagesAndAnswerWithTheSpot() {
        // The endpoint writes both languages on one press, so the language switch on
        // the page it serves keeps working for the rest of the day
        Spot spot = createMockSpotWithCompleteData();
        when(aggregatorService.getSpotById(500760)).thenReturn(Optional.of(spot));
        when(aggregatorService.generateAiAnalysisInAllLanguages(500760, "pl"))
                .thenReturn(Mono.just("Polska analiza AI"));

        StepVerifier.create(controller.generateAiAnalysis(500760, "pl"))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody()).isEqualTo(spot);
                })
                .verifyComplete();

        verify(metrics).incrementAiAnalysisRequestCounter();
    }

    @Test
    void shouldReturn404WhenGeneratingAnAnalysisForUnknownSpot() {
        when(aggregatorService.getSpotById(999999)).thenReturn(Optional.empty());

        StepVerifier.create(controller.generateAiAnalysis(999999, "en"))
                .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND))
                .verifyComplete();

        verify(aggregatorService, never()).generateAiAnalysisInAllLanguages(anyInt(), anyString());
    }

    @Test
    void shouldReturn503WhenTheAnalysisCouldNotBeWritten() {
        when(aggregatorService.getSpotById(500760)).thenReturn(Optional.of(createMockSpotWithCompleteData()));
        when(aggregatorService.generateAiAnalysisInAllLanguages(500760, "en")).thenReturn(Mono.empty());

        StepVerifier.create(controller.generateAiAnalysis(500760, "en"))
                .assertNext(response ->
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE))
                .verifyComplete();
    }

    private Spot createMockSpotWithCompleteData() {
        SpotInfo spotInfo = new SpotInfo("Beach", "W, SW", "18-22°C", "Intermediate", "sandy", "none", "Spring, Summer", "Great spot", "");

        List<Forecast> forecasts = List.of(
                new Forecast("Today", 12.5, 18.3, "SW", 15.0, 0.5, 0, 0),
                new Forecast("Tomorrow", 10.0, 15.0, "W", 14.0, 1.0, 0, 0)
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
                null, // windguruFallbackUrl
                "https://www.windfinder.com/forecast/jastarnia",
                "https://www.meteo.pl",
                "https://www.webcam.pl",
                "https://maps.google.com",
                currentConditions,
                new ArrayList<>(),
                forecasts,
                new ArrayList<>(),
                null,
                null,
                null,
                null,
                spotInfo,
                null,
                null,
                null,
                "2025-01-15 14:30:00 CET"
        );
    }
}
