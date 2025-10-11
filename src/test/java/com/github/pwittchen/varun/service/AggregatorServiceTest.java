package com.github.pwittchen.varun.service;

import com.github.pwittchen.varun.exception.FetchingAiForecastAnalysisException;
import com.github.pwittchen.varun.exception.FetchingCurrentConditionsException;
import com.github.pwittchen.varun.exception.FetchingForecastException;
import com.github.pwittchen.varun.model.CurrentConditions;
import com.github.pwittchen.varun.model.Forecast;
import com.github.pwittchen.varun.model.ForecastData;
import com.github.pwittchen.varun.model.Spot;
import com.github.pwittchen.varun.provider.SpotsDataProvider;
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
    private AiService aiService;

    private AggregatorService aggregatorService;

    @BeforeEach
    void setUp() {
        aggregatorService = new AggregatorService(
                spotsDataProvider,
                forecastService,
                currentConditionsService,
                aiService
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
        when(forecastService.getForecastData(123)).thenReturn(Mono.just(new ForecastData(List.of(forecast), List.of())));

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
        ReflectionTestUtils.setField(
                aggregatorService,
                "forecastCache",
                Map.of(123, new ForecastData(dailyForecast, hourlyForecast))
        );

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
        aggregatorService.fetchAiAnalysisEveryEightHours();

        // then
        verify(aiService, never()).fetchAiAnalysis(any());
    }

    @Test
    void shouldFetchAiAnalysisWhenEnabled() throws FetchingForecastException, InterruptedException {
        // given
        var spot = createTestSpot(123, "Test Spot");
        ReflectionTestUtils.setField(aggregatorService, "aiForecastAnalysisEnabled", true);

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot));
        when(aiService.fetchAiAnalysis(any())).thenReturn(Mono.just("AI analysis result"));

        aggregatorService.init();
        Thread.sleep(100);

        // when
        aggregatorService.fetchAiAnalysisEveryEightHours();

        // then
        verify(aiService).fetchAiAnalysis(any());
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
        when(aiService.fetchAiAnalysis(any())).thenReturn(Mono.just(""));

        aggregatorService.init();
        Thread.sleep(100);

        // when
        aggregatorService.fetchAiAnalysisEveryEightHours();

        // then
        verify(aiService).fetchAiAnalysis(any());
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
                null
        );
    }
}
