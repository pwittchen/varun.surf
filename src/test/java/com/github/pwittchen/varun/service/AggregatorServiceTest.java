package com.github.pwittchen.varun.service;

import com.github.pwittchen.varun.exception.FetchingAiForecastAnalysisException;
import com.github.pwittchen.varun.exception.FetchingCurrentConditionsException;
import com.github.pwittchen.varun.exception.FetchingForecastException;
import com.github.pwittchen.varun.model.live.CurrentConditions;
import com.github.pwittchen.varun.model.forecast.Forecast;
import com.github.pwittchen.varun.model.forecast.ForecastData;
import com.github.pwittchen.varun.model.spot.Spot;
import com.github.pwittchen.varun.provider.spots.SpotsDataProvider;
import com.github.pwittchen.varun.service.ai.AiServiceEn;
import com.github.pwittchen.varun.service.ai.AiServicePl;
import com.github.pwittchen.varun.service.live.CurrentConditionsService;
import com.github.pwittchen.varun.service.forecast.ForecastService;
import com.github.pwittchen.varun.service.map.GoogleMapsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AggregatorServiceTest {

    @Mock
    private SpotsDataProvider spotsDataProvider;

    @Mock
    private ForecastService forecastService;

    @Mock
    private CurrentConditionsService currentConditionsService;

    @Mock
    private AiServiceEn aiServiceEn;

    @Mock
    private AiServicePl aiServicePl;

    @Mock
    private GoogleMapsService googleMapsService;

    private AggregatorService aggregatorService;

    @BeforeEach
    void setUp() {
        aggregatorService = new AggregatorService(
                spotsDataProvider,
                forecastService,
                currentConditionsService,
                aiServiceEn,
                aiServicePl,
                googleMapsService
        );
    }

    @Test
    void shouldInitializeWithEmptySpots() {
        // given
        when(spotsDataProvider.getSpots()).thenReturn(Flux.empty());

        // when
        aggregatorService.init();

        // then
        verify(spotsDataProvider).getSpots();
    }

    @Test
    void shouldInitializeWithSpots() throws InterruptedException {
        // given
        var spot1 = createTestSpot(1, "Test Spot 1");
        var spot2 = createTestSpot(2, "Test Spot 2");
        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot1, spot2));

        // when
        aggregatorService.init();

        // give reactor time to process async
        Thread.sleep(100);

        // then
        verify(spotsDataProvider).getSpots();
        assertThat(aggregatorService.getSpots()).hasSize(2);
    }

    @Test
    void shouldHandleErrorDuringInitialization() {
        // given
        when(spotsDataProvider.getSpots()).thenReturn(Flux.error(new RuntimeException("Failed to load")));

        // when
        aggregatorService.init();

        // then - should not throw, error is logged
        verify(spotsDataProvider).getSpots();
    }

    @Test
    void shouldDisposeOnCleanup() throws InterruptedException {
        // given
        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(createTestSpot(1, "Test")));
        aggregatorService.init();
        Thread.sleep(100);

        // when
        aggregatorService.cleanup();

        // then - no exception should be thrown
    }

    @Test
    void shouldGetSpots() {
        // given
        var spot = createTestSpot(1, "Test Spot");
        ReflectionTestUtils.setField(
                aggregatorService,
                "spots",
                new java.util.concurrent.atomic.AtomicReference<>(List.of(spot))
        );

        // when
        var result = aggregatorService.getSpots();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Test Spot");
    }

    @Test
    void shouldFetchForecastsSuccessfully() throws FetchingForecastException, InterruptedException {
        // given
        var spot = createTestSpot(123, "Test Spot");
        var forecast = new Forecast("Mon 12:00", 10.0, 20.0, "N", 15.0, 0.0);

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot));
        when(forecastService.getForecastData(123)).thenReturn(Mono.just(new ForecastData(List.of(forecast), List.of(), List.of())));

        aggregatorService.init();
        Thread.sleep(100);

        // when
        aggregatorService.fetchForecastsEveryThreeHours();

        // then
        verify(forecastService).getForecastData(123);
    }

    @Test
    void shouldReturnHourlyForecastForSingleSpot() {
        // given
        var spot = createTestSpot(123, "Test Spot");
        var hourlyForecast = List.of(new Forecast("Mon 01 Jan 2025 01:00", 9.0, 11.0, "N", 14.0, 0.1));
        var dailyForecast = List.of(new Forecast("Today", 10.0, 12.0, "N", 15.0, 0.5));

        ReflectionTestUtils.setField(
                aggregatorService,
                "spots",
                new java.util.concurrent.atomic.AtomicReference<>(List.of(spot))
        );

        @SuppressWarnings("unchecked")
        var forecastCache = (java.util.concurrent.ConcurrentMap<Integer, ForecastData>)
                ReflectionTestUtils.getField(aggregatorService, "forecastCache");
        forecastCache.put(123, new ForecastData(dailyForecast, hourlyForecast, List.of()));

        // when
        var result = aggregatorService.getSpotById(123);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().forecastHourly()).containsExactlyElementsOf(hourlyForecast);
        assertThat(result.get().forecast()).isEqualTo(spot.forecast());
    }

    @Test
    void shouldThrowExceptionWhenForecastFetchFails() throws InterruptedException {
        // given
        var spot = createTestSpot(123, "Test Spot");

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot));
        when(forecastService.getForecastData(123)).thenReturn(Mono.error(new RuntimeException("API Error")));

        aggregatorService.init();
        Thread.sleep(100);

        // when/then
        assertThatThrownBy(() -> aggregatorService.fetchForecastsEveryThreeHours())
                .isInstanceOf(FetchingForecastException.class);
    }

    @Test
    void shouldRecoverFromFetchingForecastsError() {
        // given
        var exception = new FetchingForecastException("Test error");

        // when
        aggregatorService.recoverFromFetchingForecasts(exception);

        // then - should not throw
    }

    @Test
    void shouldFetchCurrentConditionsSuccessfully() throws FetchingCurrentConditionsException, InterruptedException {
        // given
        var spot = createTestSpot(123, "Test Spot");
        var currentConditions = new CurrentConditions("2025-01-01 12:00", 15, 25, "NW", 10);

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot));
        when(currentConditionsService.fetchCurrentConditions(123)).thenReturn(Mono.just(currentConditions));

        aggregatorService.init();
        Thread.sleep(100);

        // when
        aggregatorService.fetchCurrentConditionsEveryOneMinute();

        // then
        verify(currentConditionsService).fetchCurrentConditions(123);
    }

    @Test
    void shouldHandlePartialFailuresInCurrentConditionsFetch() throws FetchingCurrentConditionsException, InterruptedException {
        // given
        var spot1 = createTestSpot(123, "Test Spot 1");
        var spot2 = createTestSpot(456, "Test Spot 2");
        var currentConditions = new CurrentConditions("2025-01-01 12:00", 15, 25, "NW", 10);

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot1, spot2));
        when(currentConditionsService.fetchCurrentConditions(123)).thenReturn(Mono.just(currentConditions));
        when(currentConditionsService.fetchCurrentConditions(456)).thenReturn(Mono.error(new RuntimeException("Failed")));

        aggregatorService.init();
        Thread.sleep(100);

        // when
        aggregatorService.fetchCurrentConditionsEveryOneMinute();

        // then - should complete without throwing
        verify(currentConditionsService).fetchCurrentConditions(123);
        verify(currentConditionsService).fetchCurrentConditions(456);
    }

    @Test
    void shouldRecoverFromFetchingCurrentConditionsError() {
        // given
        var exception = new FetchingCurrentConditionsException("Test error");

        // when
        aggregatorService.recoverFromFetchingCurrentConditions(exception);

        // then - should not throw
    }

    @Test
    void shouldSkipAiForecastAnalysisWhenDisabled() throws FetchingForecastException, InterruptedException {
        // given
        ReflectionTestUtils.setField(aggregatorService, "aiForecastAnalysisEnabled", false);
        when(spotsDataProvider.getSpots()).thenReturn(Flux.empty());

        aggregatorService.init();
        Thread.sleep(100);

        // when
        aggregatorService.fetchAiAnalysisEveryEightHoursEn();

        // then
        verify(aiServiceEn, never()).fetchAiAnalysis(any());
    }

    @Test
    void shouldFetchAiAnalysisWhenEnabled() throws FetchingForecastException, InterruptedException {
        // given
        var spot = createTestSpot(123, "Test Spot");
        ReflectionTestUtils.setField(aggregatorService, "aiForecastAnalysisEnabled", true);

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot));
        when(aiServiceEn.fetchAiAnalysis(any())).thenReturn(Mono.just("AI analysis result"));

        aggregatorService.init();
        Thread.sleep(100);

        // when
        aggregatorService.fetchAiAnalysisEveryEightHoursEn();

        // then
        verify(aiServiceEn).fetchAiAnalysis(any());
    }

    @Test
    void shouldRecoverFromFetchingAiAnalysisError() {
        // given
        var exception = new FetchingAiForecastAnalysisException("Test error");

        // when
        aggregatorService.recoverFromFetchingAiAnalysis(exception);

        // then - should not throw
    }

    @Test
    void shouldHandleEmptySpotsList() throws FetchingForecastException, InterruptedException {
        // given
        when(spotsDataProvider.getSpots()).thenReturn(Flux.empty());

        aggregatorService.init();
        Thread.sleep(100);

        // when
        aggregatorService.fetchForecastsEveryThreeHours();

        // then
        verify(forecastService, never()).getForecastData(anyInt());
        assertThat(aggregatorService.getSpots()).isEmpty();
    }

    @Test
    void shouldFilterEmptyCurrentConditions() throws FetchingCurrentConditionsException, InterruptedException {
        // given
        var spot = createTestSpot(123, "Test Spot");
        var emptyConditions = new CurrentConditions(null, 0, 0, null, 0);

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot));
        when(currentConditionsService.fetchCurrentConditions(123)).thenReturn(Mono.just(emptyConditions));

        aggregatorService.init();
        Thread.sleep(100);

        // when
        aggregatorService.fetchCurrentConditionsEveryOneMinute();

        // then
        verify(currentConditionsService).fetchCurrentConditions(123);
    }

    @Test
    void shouldFilterEmptyAiAnalysis() throws FetchingForecastException, InterruptedException {
        // given
        var spot = createTestSpot(123, "Test Spot");
        ReflectionTestUtils.setField(aggregatorService, "aiForecastAnalysisEnabled", true);

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot));
        when(aiServiceEn.fetchAiAnalysis(any())).thenReturn(Mono.just(""));

        aggregatorService.init();
        Thread.sleep(100);

        // when
        aggregatorService.fetchAiAnalysisEveryEightHoursEn();

        // then
        verify(aiServiceEn).fetchAiAnalysis(any());
    }

    @Test
    void shouldSkipAiForecastAnalysisPlWhenDisabled() throws FetchingForecastException, InterruptedException {
        // given
        ReflectionTestUtils.setField(aggregatorService, "aiForecastAnalysisEnabled", false);
        when(spotsDataProvider.getSpots()).thenReturn(Flux.empty());

        aggregatorService.init();
        Thread.sleep(100);

        // when
        aggregatorService.fetchAiAnalysisEveryEightHoursPl();

        // then
        verify(aiServicePl, never()).fetchAiAnalysis(any());
    }

    @Test
    void shouldFetchAiAnalysisPlWhenEnabled() throws FetchingForecastException, InterruptedException {
        // given
        var spot = createTestSpot(123, "Test Spot");
        ReflectionTestUtils.setField(aggregatorService, "aiForecastAnalysisEnabled", true);

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot));
        when(aiServicePl.fetchAiAnalysis(any())).thenReturn(Mono.just("Analiza AI"));

        aggregatorService.init();
        Thread.sleep(100);

        // when
        aggregatorService.fetchAiAnalysisEveryEightHoursPl();

        // then
        verify(aiServicePl).fetchAiAnalysis(any());
    }

    @Test
    void shouldFilterEmptyAiAnalysisPl() throws FetchingForecastException, InterruptedException {
        // given
        var spot = createTestSpot(123, "Test Spot");
        ReflectionTestUtils.setField(aggregatorService, "aiForecastAnalysisEnabled", true);

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot));
        when(aiServicePl.fetchAiAnalysis(any())).thenReturn(Mono.just(""));

        aggregatorService.init();
        Thread.sleep(100);

        // when
        aggregatorService.fetchAiAnalysisEveryEightHoursPl();

        // then
        verify(aiServicePl).fetchAiAnalysis(any());
    }

    @Test
    void shouldUpdateAiAnalysisPlIncrementallyInCache() throws Exception {
        // given
        var spot1 = createTestSpot(123, "Test Spot 1");
        var spot2 = createTestSpot(456, "Test Spot 2");

        ReflectionTestUtils.setField(aggregatorService, "aiForecastAnalysisEnabled", true);
        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot1, spot2));

        // First spot completes quickly, second spot is slow
        when(aiServicePl.fetchAiAnalysis(spot1)).thenReturn(Mono.just("Analiza dla spotu 1"));
        when(aiServicePl.fetchAiAnalysis(spot2)).thenAnswer(invocation -> {
            Thread.sleep(300);
            return Mono.just("Analiza dla spotu 2");
        });

        aggregatorService.init();
        Thread.sleep(100);

        // when
        var aiThread = new Thread(() -> {
            try {
                aggregatorService.fetchAiAnalysisEveryEightHoursPl();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        aiThread.start();

        Thread.sleep(150); // Wait for first spot to complete, second still processing

        // then - first spot should already have AI analysis in cache even though second is still processing
        @SuppressWarnings("unchecked")
        var aiAnalysisCache = (Map<Integer, String>) ReflectionTestUtils.getField(aggregatorService, "aiAnalysisPl");
        assertThat(aiAnalysisCache).containsKey(123);
        assertThat(aiAnalysisCache.get(123)).isEqualTo("Analiza dla spotu 1");

        // Verify enrichment works - the spot should have AI analysis when retrieved
        var spots = aggregatorService.getSpots();
        var enrichedSpot1 = spots.stream()
                .filter(s -> s.wgId() == 123)
                .findFirst();

        assertThat(enrichedSpot1).isPresent();
        assertThat(enrichedSpot1.get().aiAnalysisPl()).isEqualTo("Analiza dla spotu 1");

        aiThread.join(5000);
    }

    @Test
    void shouldEnrichSpotsWithBothEnAndPlAiAnalysis() throws Exception {
        // given
        var spot = createTestSpot(123, "Test Spot");
        ReflectionTestUtils.setField(aggregatorService, "aiForecastAnalysisEnabled", true);

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot));
        when(aiServiceEn.fetchAiAnalysis(any())).thenReturn(Mono.just("English AI analysis"));
        when(aiServicePl.fetchAiAnalysis(any())).thenReturn(Mono.just("Polska analiza AI"));

        aggregatorService.init();
        Thread.sleep(100);

        // when - fetch both languages
        aggregatorService.fetchAiAnalysisEveryEightHoursEn();
        aggregatorService.fetchAiAnalysisEveryEightHoursPl();

        // then - verify both are in caches
        @SuppressWarnings("unchecked")
        var aiAnalysisCacheEn = (Map<Integer, String>) ReflectionTestUtils.getField(aggregatorService, "aiAnalysisEn");
        @SuppressWarnings("unchecked")
        var aiAnalysisCachePl = (Map<Integer, String>) ReflectionTestUtils.getField(aggregatorService, "aiAnalysisPl");

        assertThat(aiAnalysisCacheEn).containsKey(123);
        assertThat(aiAnalysisCacheEn.get(123)).isEqualTo("English AI analysis");
        assertThat(aiAnalysisCachePl).containsKey(123);
        assertThat(aiAnalysisCachePl.get(123)).isEqualTo("Polska analiza AI");

        // and verify spot has both analyses when retrieved
        var spots = aggregatorService.getSpots();
        assertThat(spots).hasSize(1);
        assertThat(spots.get(0).aiAnalysisEn()).isEqualTo("English AI analysis");
        assertThat(spots.get(0).aiAnalysisPl()).isEqualTo("Polska analiza AI");
    }

    @Test
    void shouldUpdateAiAnalysisIncrementallyInCache() throws Exception {
        // given
        var spot1 = createTestSpot(123, "Test Spot 1");
        var spot2 = createTestSpot(456, "Test Spot 2");

        ReflectionTestUtils.setField(aggregatorService, "aiForecastAnalysisEnabled", true);
        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot1, spot2));

        // First spot completes quickly, second spot is slow
        when(aiServiceEn.fetchAiAnalysis(spot1)).thenReturn(Mono.just("Analysis for spot 1"));
        when(aiServiceEn.fetchAiAnalysis(spot2)).thenAnswer(invocation -> {
            Thread.sleep(300);
            return Mono.just("Analysis for spot 2");
        });

        aggregatorService.init();
        Thread.sleep(100);

        // when
        var aiThread = new Thread(() -> {
            try {
                aggregatorService.fetchAiAnalysisEveryEightHoursEn();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        aiThread.start();

        Thread.sleep(150); // Wait for first spot to complete, second still processing

        // then - first spot should already have AI analysis in cache even though second is still processing
        @SuppressWarnings("unchecked")
        var aiAnalysisCache = (Map<Integer, String>) ReflectionTestUtils.getField(aggregatorService, "aiAnalysisEn");
        assertThat(aiAnalysisCache).containsKey(123);
        assertThat(aiAnalysisCache.get(123)).isEqualTo("Analysis for spot 1");

        // Verify enrichment works - the spot should have AI analysis when retrieved
        var spots = aggregatorService.getSpots();
        var enrichedSpot1 = spots.stream()
                .filter(s -> s.wgId() == 123)
                .findFirst();

        assertThat(enrichedSpot1).isPresent();
        assertThat(enrichedSpot1.get().aiAnalysisEn()).isEqualTo("Analysis for spot 1");

        aiThread.join(5000);
    }

    @Test
    void shouldEnrichSpotsWithCurrentConditionsFromCache() throws Exception {
        // given
        var spot = createTestSpot(123, "Test Spot");
        var currentConditions = new CurrentConditions("2025-01-01 12:00", 15, 25, "NW", 10);

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot));
        when(currentConditionsService.fetchCurrentConditions(123)).thenReturn(Mono.just(currentConditions));

        aggregatorService.init();
        Thread.sleep(100);

        // when - fetch current conditions (simulates scheduled task)
        aggregatorService.fetchCurrentConditionsEveryOneMinute();

        // then - verify data is in cache
        @SuppressWarnings("unchecked")
        var currentConditionsCache = (Map<Integer, CurrentConditions>) ReflectionTestUtils.getField(aggregatorService, "currentConditions");
        assertThat(currentConditionsCache).containsKey(123);
        assertThat(currentConditionsCache.get(123)).isEqualTo(currentConditions);

        // and verify enrichment works - spot should have current conditions when retrieved
        var spots = aggregatorService.getSpots();
        assertThat(spots).hasSize(1);
        assertThat(spots.get(0).currentConditions()).isEqualTo(currentConditions);
    }

    private Spot createTestSpot(int wgId, String name) {
        var forecast = new ArrayList<Forecast>();
        var hourlyForecast = new ArrayList<Forecast>();
        return new Spot(
                name,
                "Poland",
                "https://windguru.cz/" + wgId,
                null,
                null,
                null,
                null,
                null,
                forecast,
                hourlyForecast,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
