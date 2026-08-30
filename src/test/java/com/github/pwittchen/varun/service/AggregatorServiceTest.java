package com.github.pwittchen.varun.service;

import com.github.pwittchen.varun.exception.FetchingAiForecastAnalysisException;
import com.github.pwittchen.varun.exception.FetchingCurrentConditionsException;
import com.github.pwittchen.varun.exception.FetchingForecastException;
import com.github.pwittchen.varun.mapper.HourlyForecastMapper;
import com.github.pwittchen.varun.metrics.AggregatorServiceMetrics;
import com.github.pwittchen.varun.model.live.CurrentConditions;
import com.github.pwittchen.varun.model.map.Coordinates;
import com.github.pwittchen.varun.model.forecast.Forecast;
import com.github.pwittchen.varun.model.forecast.ForecastData;
import com.github.pwittchen.varun.model.forecast.ForecastModel;
import com.github.pwittchen.varun.model.spot.Spot;
import com.github.pwittchen.varun.data.spots.SpotsDataProvider;
import com.github.pwittchen.varun.service.ai.AiServiceEn;
import com.github.pwittchen.varun.service.ai.AiServicePl;
import com.github.pwittchen.varun.service.forecast.IcmForecastVisionService;
import com.github.pwittchen.varun.service.forecast.IcmGridMapper;
import com.github.pwittchen.varun.service.live.CurrentConditionsService;
import com.github.pwittchen.varun.service.forecast.ForecastService;
import com.github.pwittchen.varun.service.map.GoogleMapsService;
import com.github.pwittchen.varun.service.sponsors.SponsorsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AggregatorServiceTest {

    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(10);

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

    @Mock
    private IcmGridMapper icmGridMapper;

    private final HourlyForecastMapper hourlyForecastMapper = new HourlyForecastMapper();

    @Mock
    private IcmForecastVisionService icmForecastVisionService;

    @Mock
    private SponsorsService sponsorsService;

    @Mock
    private AggregatorServiceMetrics metricsService;

    private AggregatorService aggregatorService;

    @BeforeEach
    void setUp() {
        aggregatorService = new AggregatorService(
                spotsDataProvider,
                forecastService,
                currentConditionsService,
                aiServiceEn,
                aiServicePl,
                googleMapsService,
                icmGridMapper,
                hourlyForecastMapper,
                icmForecastVisionService,
                sponsorsService,
                metricsService
        );
    }

    /**
     * Work started by {@link AggregatorService#init()} hops across boundedElastic threads, so tests
     * have to wait for the effect they assert on rather than for a fixed amount of time. Fixed sleeps
     * are what used to make this class fail on loaded CI runners.
     */
    private static void awaitUntil(String description, BooleanSupplier condition) {
        var deadline = System.nanoTime() + AWAIT_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted while waiting for " + description, e);
            }
        }
        throw new AssertionError("timed out after " + AWAIT_TIMEOUT + " waiting for " + description);
    }

    private void awaitSpotsLoaded(int expectedSpotCount) {
        awaitUntil(
                expectedSpotCount + " spot(s) to be loaded",
                () -> spotsCache().size() == expectedSpotCount
        );
    }

    /**
     * Reads the cache directly instead of going through {@code getSpots()}, which would enrich spots
     * and trigger the very background fetches some of these tests verify.
     */
    @SuppressWarnings("unchecked")
    private Map<Integer, Spot> spotsCache() {
        return (Map<Integer, Spot>) ReflectionTestUtils.getField(aggregatorService, "spots");
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
    void shouldInitializeWithSpots() {
        // given
        var spot1 = createTestSpot(1, "Test Spot 1");
        var spot2 = createTestSpot(2, "Test Spot 2");
        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot1, spot2));

        // when
        aggregatorService.init();

        awaitSpotsLoaded(2);

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
    void shouldDisposeOnCleanup() {
        // given
        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(createTestSpot(1, "Test")));
        aggregatorService.init();
        awaitSpotsLoaded(1);

        // when
        aggregatorService.cleanup();

        // then - no exception should be thrown
    }

    @Test
    void shouldGetSpots() {
        // given
        var spot = createTestSpot(1, "Test Spot");
        var spotsMap = new java.util.concurrent.ConcurrentHashMap<Integer, Spot>();
        spotsMap.put(spot.wgId(), spot);
        ReflectionTestUtils.setField(aggregatorService, "spots", spotsMap);

        // when
        var result = aggregatorService.getSpots();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Test Spot");
    }

    @Test
    void shouldFetchForecastsSuccessfully() throws FetchingForecastException {
        // given
        var spot = createTestSpot(123, "Test Spot");
        var forecast = new Forecast("Mon 12:00", 10.0, 20.0, "N", 15.0, 0.0, 0, 0);

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot));
        when(forecastService.getForecastData(123)).thenReturn(Mono.just(new ForecastData(List.of(forecast), Map.of())));

        aggregatorService.init();
        awaitSpotsLoaded(1);

        // when
        aggregatorService.fetchForecastsEveryThreeHours();

        // then
        verify(forecastService).getForecastData(123);
    }

    @Test
    void shouldReturnHourlyForecastForSingleSpot() {
        // given
        var spot = createTestSpot(123, "Test Spot");
        var hourlyForecast = List.of(new Forecast("Mon 01 Jan 2025 01:00", 9.0, 11.0, "N", 14.0, 0.1, 0, 0));
        var dailyForecast = List.of(new Forecast("Today", 10.0, 12.0, "N", 15.0, 0.5, 0, 0));

        var spotsMap = new java.util.concurrent.ConcurrentHashMap<Integer, Spot>();
        spotsMap.put(spot.wgId(), spot);
        ReflectionTestUtils.setField(aggregatorService, "spots", spotsMap);

        @SuppressWarnings("unchecked")
        var forecastCache = (java.util.concurrent.ConcurrentMap<Integer, ForecastData>)
                ReflectionTestUtils.getField(aggregatorService, "forecastCache");
        forecastCache.put(123, new ForecastData(dailyForecast, Map.of(ForecastModel.GFS, hourlyForecast)));

        // when
        var result = aggregatorService.getSpotById(123);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().forecastHourly()).containsExactlyElementsOf(hourlyForecast);
        assertThat(result.get().forecast()).isEqualTo(spot.forecast());
    }

    /**
     * A pass covers the whole spot list and takes minutes, so one spot whose export fails must not
     * take the rest of the pass down with it - that used to discard every forecast already fetched
     * and hand a minutes-long retry to @Retryable.
     */
    @Test
    void shouldKeepTheRestOfThePassWhenOneSpotFails() throws FetchingForecastException {
        // given
        var failing = createTestSpot(123, "Failing Spot");
        var working = createTestSpot(456, "Working Spot");
        var daily = List.of(new Forecast("Today", 10.0, 12.0, "N", 15.0, 0.5, 0, 0));
        var hourly = List.of(new Forecast("Mon 01 Jan 2025 01:00", 9.0, 11.0, "N", 14.0, 0.1, 0, 0));
        var forecastData = new ForecastData(daily, Map.of(ForecastModel.GFS, hourly));

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(failing, working));
        when(forecastService.getForecastData(123)).thenReturn(Mono.error(new RuntimeException("API Error")));
        when(forecastService.getForecastData(456)).thenReturn(Mono.just(forecastData));

        aggregatorService.init();
        awaitSpotsLoaded(2);

        // when
        aggregatorService.fetchForecastsEveryThreeHours();

        // then
        var progress = aggregatorService.getForecastFetchProgress();
        assertThat(progress.inProgress()).isFalse();
        assertThat(progress.total()).isEqualTo(2);
        assertThat(progress.completed()).isEqualTo(2);
        assertThat(progress.fetched()).isEqualTo(1);
        assertThat(progress.failed()).isEqualTo(1);
        assertThat(progress.cached()).isEqualTo(1);
        assertThat(spotsCache().get(456).forecast()).isEqualTo(daily);
        // one attempt in the pass, one in the retry that follows it
        verify(forecastService, times(2)).getForecastData(123);
    }

    /**
     * A handful of exports time out on any given pass. Retrying them once the pass is through is
     * what keeps those spots from carrying a three-hour-old forecast - or none at all, on a freshly
     * started instance - until the next sweep.
     */
    @Test
    void shouldRetrySpotsThatFailedOnceThePassIsThrough() throws FetchingForecastException {
        // given
        var flaky = createTestSpot(123, "Flaky Spot");
        var working = createTestSpot(456, "Working Spot");
        var daily = List.of(new Forecast("Today", 10.0, 12.0, "N", 15.0, 0.5, 0, 0));
        var forecastData = new ForecastData(daily, Map.of());

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(flaky, working));
        when(forecastService.getForecastData(123))
                .thenReturn(Mono.error(new RuntimeException("API Error")))
                .thenReturn(Mono.just(forecastData));
        when(forecastService.getForecastData(456)).thenReturn(Mono.just(forecastData));

        aggregatorService.init();
        awaitSpotsLoaded(2);

        // when
        aggregatorService.fetchForecastsEveryThreeHours();

        // then - the retry recovers the spot, and the pass counts it as fetched rather than failed
        var progress = aggregatorService.getForecastFetchProgress();
        assertThat(progress.total()).isEqualTo(2);
        assertThat(progress.completed()).isEqualTo(2);
        assertThat(progress.fetched()).isEqualTo(2);
        assertThat(progress.failed()).isEqualTo(0);
        assertThat(progress.cached()).isEqualTo(2);
        assertThat(spotsCache().get(123).forecast()).isEqualTo(daily);
        verify(forecastService, times(2)).getForecastData(123);
    }

    /**
     * The retry is an attempt, not a promise: a spot that answers with nothing the second time
     * stops being a failure without becoming a fetch, so the counters still add up to the pass.
     */
    @Test
    void shouldCountARetriedSpotThatReturnsNothingAsEmpty() throws FetchingForecastException {
        // given
        var flaky = createTestSpot(123, "Flaky Spot");

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(flaky));
        when(forecastService.getForecastData(123))
                .thenReturn(Mono.error(new RuntimeException("API Error")))
                .thenReturn(Mono.just(new ForecastData(List.of(), Map.of())));

        aggregatorService.init();
        awaitSpotsLoaded(1);

        // when
        aggregatorService.fetchForecastsEveryThreeHours();

        // then
        var progress = aggregatorService.getForecastFetchProgress();
        assertThat(progress.completed()).isEqualTo(1);
        assertThat(progress.fetched()).isEqualTo(0);
        assertThat(progress.empty()).isEqualTo(1);
        assertThat(progress.failed()).isEqualTo(0);
        assertThat(progress.cached()).isEqualTo(0);
    }

    /**
     * The frontend reads this while a pass runs, which is when the numbers are least settled.
     */
    @Test
    void shouldReportForecastFetchProgressAfterASuccessfulPass() throws FetchingForecastException {
        // given
        var spot = createTestSpot(123, "Test Spot");
        var daily = List.of(new Forecast("Today", 10.0, 12.0, "N", 15.0, 0.5, 0, 0));
        var forecastData = new ForecastData(daily, Map.of());

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot));
        when(forecastService.getForecastData(123)).thenReturn(Mono.just(forecastData));

        aggregatorService.init();
        awaitSpotsLoaded(1);

        // when
        aggregatorService.fetchForecastsEveryThreeHours();

        // then
        var progress = aggregatorService.getForecastFetchProgress();
        assertThat(progress.inProgress()).isFalse();
        assertThat(progress.total()).isEqualTo(1);
        assertThat(progress.completed()).isEqualTo(1);
        assertThat(progress.fetched()).isEqualTo(1);
        assertThat(progress.empty()).isEqualTo(0);
        assertThat(progress.failed()).isEqualTo(0);
        assertThat(progress.cached()).isEqualTo(1);
        assertThat(progress.startedAt()).isGreaterThan(0);
        assertThat(progress.finishedAt()).isGreaterThanOrEqualTo(progress.startedAt());
    }

    /**
     * A spot whose export comes back with nothing is neither a success nor a failure: nothing is
     * cached for it, and the pass carries on.
     */
    @Test
    void shouldCountSpotsThatReturnNoForecastAsEmpty() throws FetchingForecastException {
        // given
        var spot = createTestSpot(123, "Test Spot");

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot));
        when(forecastService.getForecastData(123)).thenReturn(Mono.just(new ForecastData(List.of(), Map.of())));

        aggregatorService.init();
        awaitSpotsLoaded(1);

        // when
        aggregatorService.fetchForecastsEveryThreeHours();

        // then
        var progress = aggregatorService.getForecastFetchProgress();
        assertThat(progress.completed()).isEqualTo(1);
        assertThat(progress.empty()).isEqualTo(1);
        assertThat(progress.fetched()).isEqualTo(0);
        assertThat(progress.failed()).isEqualTo(0);
        assertThat(progress.cached()).isEqualTo(0);
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
    void shouldFetchCurrentConditionsSuccessfully() throws FetchingCurrentConditionsException {
        // given
        var spot = createTestSpot(123, "Test Spot");
        var currentConditions = new CurrentConditions("2025-01-01 12:00", 15, 25, "NW", 10);

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot));
        when(currentConditionsService.fetchCurrentConditions(123)).thenReturn(Mono.just(currentConditions));

        aggregatorService.init();
        awaitSpotsLoaded(1);

        // when
        aggregatorService.fetchCurrentConditionsEveryOneMinute();

        // then
        verify(currentConditionsService).fetchCurrentConditions(123);
    }

    @Test
    void shouldHandlePartialFailuresInCurrentConditionsFetch() throws FetchingCurrentConditionsException {
        // given
        var spot1 = createTestSpot(123, "Test Spot 1");
        var spot2 = createTestSpot(456, "Test Spot 2");
        var currentConditions = new CurrentConditions("2025-01-01 12:00", 15, 25, "NW", 10);

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot1, spot2));
        when(currentConditionsService.fetchCurrentConditions(123)).thenReturn(Mono.just(currentConditions));
        when(currentConditionsService.fetchCurrentConditions(456)).thenReturn(Mono.error(new RuntimeException("Failed")));

        aggregatorService.init();
        awaitSpotsLoaded(2);

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
    void shouldNotGenerateAiAnalysisWhenFeatureIsDisabled() {
        // given
        var spot = createTestSpot(123, "Test Spot");
        ReflectionTestUtils.setField(aggregatorService, "aiForecastAnalysisEnabled", false);

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot));

        aggregatorService.init();
        awaitSpotsLoaded(1);

        // when
        var analysis = aggregatorService.generateAiAnalysis(123, "en").block();

        // then
        assertThat(analysis).isNull();
        verify(aiServiceEn, never()).fetchAiAnalysis(any(), any());
    }

    @Test
    void shouldNotGenerateAiAnalysisForUnknownSpot() {
        // given
        ReflectionTestUtils.setField(aggregatorService, "aiForecastAnalysisEnabled", true);
        when(spotsDataProvider.getSpots()).thenReturn(Flux.empty());

        aggregatorService.init();
        awaitSpotsLoaded(0);

        // when
        var analysis = aggregatorService.generateAiAnalysis(999, "en").block();

        // then
        assertThat(analysis).isNull();
        verify(aiServiceEn, never()).fetchAiAnalysis(any(), any());
    }

    @Test
    void shouldGenerateAiAnalysisOnDemandAndCacheIt() {
        // given
        var spot = createTestSpot(123, "Test Spot");
        ReflectionTestUtils.setField(aggregatorService, "aiForecastAnalysisEnabled", true);

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot));
        when(aiServiceEn.fetchAiAnalysis(any(), any())).thenReturn(Mono.just("AI analysis result"));

        aggregatorService.init();
        awaitSpotsLoaded(1);

        // when
        var analysis = aggregatorService.generateAiAnalysis(123, "en").block();

        // then
        assertThat(analysis).isEqualTo("AI analysis result");
        assertThat(aggregatorService.hasValidAiAnalysis(123, "en")).isTrue();

        @SuppressWarnings("unchecked")
        var cache = (Map<Integer, String>) ReflectionTestUtils.getField(aggregatorService, "aiAnalysisEn");
        assertThat(cache.get(123)).isEqualTo("AI analysis result");

        // and the spot carries it
        assertThat(aggregatorService.getSpots().get(0).aiAnalysisEn()).isEqualTo("AI analysis result");
    }

    @Test
    void shouldServeCachedAiAnalysisWithoutCallingTheModelAgain() {
        // given
        var spot = createTestSpot(123, "Test Spot");
        ReflectionTestUtils.setField(aggregatorService, "aiForecastAnalysisEnabled", true);

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot));
        when(aiServiceEn.fetchAiAnalysis(any(), any())).thenReturn(Mono.just("AI analysis result"));

        aggregatorService.init();
        awaitSpotsLoaded(1);

        // when - asked for twice within the day
        aggregatorService.generateAiAnalysis(123, "en").block();
        var second = aggregatorService.generateAiAnalysis(123, "en").block();

        // then - the second one is answered from the cache
        assertThat(second).isEqualTo("AI analysis result");
        verify(aiServiceEn, times(1)).fetchAiAnalysis(any(), any());
    }

    @Test
    void shouldGeneratePolishAnalysisWithThePolishService() {
        // given
        var spot = createTestSpot(123, "Test Spot");
        ReflectionTestUtils.setField(aggregatorService, "aiForecastAnalysisEnabled", true);

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot));
        when(aiServicePl.fetchAiAnalysis(any(), any())).thenReturn(Mono.just("Analiza AI"));

        aggregatorService.init();
        awaitSpotsLoaded(1);

        // when
        var analysis = aggregatorService.generateAiAnalysis(123, "pl").block();

        // then
        assertThat(analysis).isEqualTo("Analiza AI");
        assertThat(aggregatorService.hasValidAiAnalysis(123, "pl")).isTrue();
        assertThat(aggregatorService.hasValidAiAnalysis(123, "en")).isFalse();
        verify(aiServiceEn, never()).fetchAiAnalysis(any(), any());
        assertThat(aggregatorService.getSpots().get(0).aiAnalysisPl()).isEqualTo("Analiza AI");
    }

    @Test
    void shouldNotCacheAnEmptyAiAnalysis() {
        // given
        var spot = createTestSpot(123, "Test Spot");
        ReflectionTestUtils.setField(aggregatorService, "aiForecastAnalysisEnabled", true);

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot));
        when(aiServiceEn.fetchAiAnalysis(any(), any())).thenReturn(Mono.just(""));

        aggregatorService.init();
        awaitSpotsLoaded(1);

        // when
        var analysis = aggregatorService.generateAiAnalysis(123, "en").block();

        // then - nothing to show means nothing to hold, so the button comes back
        assertThat(analysis).isNull();
        assertThat(aggregatorService.hasValidAiAnalysis(123, "en")).isFalse();
    }

    @Test
    void shouldRecoverFromAFailedAiAnalysisGeneration() {
        // given
        var spot = createTestSpot(123, "Test Spot");
        ReflectionTestUtils.setField(aggregatorService, "aiForecastAnalysisEnabled", true);

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot));
        when(aiServiceEn.fetchAiAnalysis(any(), any()))
                .thenReturn(Mono.error(new RuntimeException("model unavailable")));

        aggregatorService.init();
        awaitSpotsLoaded(1);

        // when
        var analysis = aggregatorService.generateAiAnalysis(123, "en").block();

        // then - the failure is swallowed, and nothing is cached
        assertThat(analysis).isNull();
        assertThat(aggregatorService.hasValidAiAnalysis(123, "en")).isFalse();
    }

    @Test
    void shouldStopServingAnExpiredAiAnalysis() {
        // given
        var spot = createTestSpot(123, "Test Spot");
        ReflectionTestUtils.setField(aggregatorService, "aiForecastAnalysisEnabled", true);

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot));
        when(aiServiceEn.fetchAiAnalysis(any(), any())).thenReturn(Mono.just("AI analysis result"));

        aggregatorService.init();
        awaitSpotsLoaded(1);
        aggregatorService.generateAiAnalysis(123, "en").block();

        // when - the analysis is backdated past its 24 hour lifetime
        @SuppressWarnings("unchecked")
        var createdAt = (Map<Integer, Long>)
                ReflectionTestUtils.getField(aggregatorService, "aiAnalysisEnCreatedAt");
        createdAt.put(123, System.currentTimeMillis() - Duration.ofHours(25).toMillis());

        // then - the spot stops carrying it even before the eviction sweep runs
        assertThat(aggregatorService.hasValidAiAnalysis(123, "en")).isFalse();
        assertThat(aggregatorService.getSpots().get(0).aiAnalysisEn()).isNull();

        // and the sweep clears it out
        aggregatorService.evictExpiredOnDemandData();

        @SuppressWarnings("unchecked")
        var cache = (Map<Integer, String>) ReflectionTestUtils.getField(aggregatorService, "aiAnalysisEn");
        assertThat(cache).doesNotContainKey(123);
        assertThat(createdAt).doesNotContainKey(123);
    }

    @Test
    void shouldHandleEmptySpotsList() throws FetchingForecastException {
        // given
        when(spotsDataProvider.getSpots()).thenReturn(Flux.empty());

        aggregatorService.init();
        awaitSpotsLoaded(0);

        // when
        aggregatorService.fetchForecastsEveryThreeHours();

        // then
        verify(forecastService, never()).getForecastData(anyInt());
        assertThat(aggregatorService.getSpots()).isEmpty();
    }

    @Test
    void shouldFilterEmptyCurrentConditions() throws FetchingCurrentConditionsException {
        // given
        var spot = createTestSpot(123, "Test Spot");
        var emptyConditions = new CurrentConditions(null, 0, 0, null, 0);

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot));
        when(currentConditionsService.fetchCurrentConditions(123)).thenReturn(Mono.just(emptyConditions));

        aggregatorService.init();
        awaitSpotsLoaded(1);

        // when
        aggregatorService.fetchCurrentConditionsEveryOneMinute();

        // then
        verify(currentConditionsService).fetchCurrentConditions(123);
    }

    @Test
    void shouldGenerateBothLanguagesIndependently() {
        // given
        var spot = createTestSpot(123, "Test Spot");
        ReflectionTestUtils.setField(aggregatorService, "aiForecastAnalysisEnabled", true);

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot));
        when(aiServiceEn.fetchAiAnalysis(any(), any())).thenReturn(Mono.just("English AI analysis"));
        when(aiServicePl.fetchAiAnalysis(any(), any())).thenReturn(Mono.just("Polska analiza AI"));

        aggregatorService.init();
        awaitSpotsLoaded(1);

        // when - each language is asked for separately, as two button presses would
        aggregatorService.generateAiAnalysis(123, "en").block();
        aggregatorService.generateAiAnalysis(123, "pl").block();

        // then
        @SuppressWarnings("unchecked")
        var cacheEn = (Map<Integer, String>) ReflectionTestUtils.getField(aggregatorService, "aiAnalysisEn");
        @SuppressWarnings("unchecked")
        var cachePl = (Map<Integer, String>) ReflectionTestUtils.getField(aggregatorService, "aiAnalysisPl");

        assertThat(cacheEn.get(123)).isEqualTo("English AI analysis");
        assertThat(cachePl.get(123)).isEqualTo("Polska analiza AI");

        var enriched = aggregatorService.getSpots().get(0);
        assertThat(enriched.aiAnalysisEn()).isEqualTo("English AI analysis");
        assertThat(enriched.aiAnalysisPl()).isEqualTo("Polska analiza AI");
    }

    @Test
    void shouldCarryTheGenerationTimestampOfEachAnalysis() {
        // given
        var spot = createTestSpot(123, "Test Spot");
        ReflectionTestUtils.setField(aggregatorService, "aiForecastAnalysisEnabled", true);

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot));
        when(aiServiceEn.fetchAiAnalysis(any(), any())).thenReturn(Mono.just("English AI analysis"));
        when(aiServicePl.fetchAiAnalysis(any(), any())).thenReturn(Mono.just("Polska analiza AI"));

        aggregatorService.init();
        awaitSpotsLoaded(1);

        // Truncated down to the millisecond the stored timestamp is taken at, so a
        // clock with finer resolution than the stored value cannot make this fail.
        var before = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        // when
        aggregatorService.generateAiAnalysisInAllLanguages(123, "en").block();

        // then - an instant the browser can format, taken at generation time
        var enriched = aggregatorService.getSpots().get(0);
        assertThat(Instant.parse(enriched.aiAnalysisEnCreatedAt())).isAfterOrEqualTo(before);
        assertThat(Instant.parse(enriched.aiAnalysisPlCreatedAt())).isAfterOrEqualTo(before);
    }

    @Test
    void shouldNotCarryAGenerationTimestampWithoutAnAnalysis() {
        // given
        var spot = createTestSpot(123, "Test Spot");

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot));

        aggregatorService.init();
        awaitSpotsLoaded(1);

        // then
        var enriched = aggregatorService.getSpots().get(0);
        assertThat(enriched.aiAnalysisEnCreatedAt()).isNull();
        assertThat(enriched.aiAnalysisPlCreatedAt()).isNull();
    }

    @Test
    void shouldGenerateBothLanguagesFromOnePress() {
        // given
        var spot = createTestSpot(123, "Test Spot");
        ReflectionTestUtils.setField(aggregatorService, "aiForecastAnalysisEnabled", true);

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot));
        when(aiServiceEn.fetchAiAnalysis(any(), any())).thenReturn(Mono.just("English AI analysis"));
        when(aiServicePl.fetchAiAnalysis(any(), any())).thenReturn(Mono.just("Polska analiza AI"));

        aggregatorService.init();
        awaitSpotsLoaded(1);

        // when - one press, made while reading the page in Polish
        var analysis = aggregatorService.generateAiAnalysisInAllLanguages(123, "pl").block();

        // then - the reader gets Polish, and the language switch has English waiting
        assertThat(analysis).isEqualTo("Polska analiza AI");
        assertThat(aggregatorService.hasValidAiAnalysis(123, "pl")).isTrue();
        assertThat(aggregatorService.hasValidAiAnalysis(123, "en")).isTrue();

        var enriched = aggregatorService.getSpots().get(0);
        assertThat(enriched.aiAnalysisPl()).isEqualTo("Polska analiza AI");
        assertThat(enriched.aiAnalysisEn()).isEqualTo("English AI analysis");
    }

    @Test
    void shouldNotRewriteALanguageAlreadyCachedWhenGeneratingBoth() {
        // given - English was written by an earlier press
        var spot = createTestSpot(123, "Test Spot");
        ReflectionTestUtils.setField(aggregatorService, "aiForecastAnalysisEnabled", true);

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot));
        when(aiServiceEn.fetchAiAnalysis(any(), any())).thenReturn(Mono.just("English AI analysis"));
        when(aiServicePl.fetchAiAnalysis(any(), any())).thenReturn(Mono.just("Polska analiza AI"));

        aggregatorService.init();
        awaitSpotsLoaded(1);
        aggregatorService.generateAiAnalysis(123, "en").block();

        // when
        var analysis = aggregatorService.generateAiAnalysisInAllLanguages(123, "en").block();

        // then - only the missing language costs a call
        assertThat(analysis).isEqualTo("English AI analysis");
        verify(aiServiceEn, times(1)).fetchAiAnalysis(any(), any());
        verify(aiServicePl, times(1)).fetchAiAnalysis(any(), any());
    }

    @Test
    void shouldServeTheRequestedLanguageWhenTheOtherOneFails() {
        // given - the Polish generation is the one that breaks
        var spot = createTestSpot(123, "Test Spot");
        ReflectionTestUtils.setField(aggregatorService, "aiForecastAnalysisEnabled", true);

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot));
        when(aiServiceEn.fetchAiAnalysis(any(), any())).thenReturn(Mono.just("English AI analysis"));
        when(aiServicePl.fetchAiAnalysis(any(), any()))
                .thenReturn(Mono.error(new RuntimeException("model unavailable")));

        aggregatorService.init();
        awaitSpotsLoaded(1);

        // when
        var analysis = aggregatorService.generateAiAnalysisInAllLanguages(123, "en").block();

        // then - the reader still gets what they asked for, and Polish stays missing
        // so its own button comes back rather than showing them English
        assertThat(analysis).isEqualTo("English AI analysis");
        assertThat(aggregatorService.hasValidAiAnalysis(123, "en")).isTrue();
        assertThat(aggregatorService.hasValidAiAnalysis(123, "pl")).isFalse();
    }

    @Test
    void shouldReportNothingWhenTheRequestedLanguageFails() {
        // given - the language being read is the one that breaks
        var spot = createTestSpot(123, "Test Spot");
        ReflectionTestUtils.setField(aggregatorService, "aiForecastAnalysisEnabled", true);

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot));
        when(aiServiceEn.fetchAiAnalysis(any(), any()))
                .thenReturn(Mono.error(new RuntimeException("model unavailable")));
        when(aiServicePl.fetchAiAnalysis(any(), any())).thenReturn(Mono.just("Polska analiza AI"));

        aggregatorService.init();
        awaitSpotsLoaded(1);

        // when
        var analysis = aggregatorService.generateAiAnalysisInAllLanguages(123, "en").block();

        // then - an empty answer, which the endpoint turns into a 503, even though
        // the Polish one was written and is kept
        assertThat(analysis).isNull();
        assertThat(aggregatorService.hasValidAiAnalysis(123, "en")).isFalse();
        assertThat(aggregatorService.hasValidAiAnalysis(123, "pl")).isTrue();
    }

    @Test
    void shouldShareOneGenerationBetweenConcurrentCallers() throws Exception {
        // given - the model takes long enough for a second caller to arrive mid-flight
        var spot = createTestSpot(123, "Test Spot");
        ReflectionTestUtils.setField(aggregatorService, "aiForecastAnalysisEnabled", true);

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot));
        when(aiServiceEn.fetchAiAnalysis(any(), any()))
                .thenReturn(Mono.delay(Duration.ofMillis(300)).map(_ -> "AI analysis result"));

        aggregatorService.init();
        awaitSpotsLoaded(1);

        // when - two callers ask for the same spot at the same time
        var first = aggregatorService.generateAiAnalysis(123, "en");
        var second = aggregatorService.generateAiAnalysis(123, "en");

        var results = Flux.merge(first, second).collectList().block(Duration.ofSeconds(5));

        // then - both are served, but the model was only asked once
        assertThat(results).containsExactly("AI analysis result", "AI analysis result");
        verify(aiServiceEn, times(1)).fetchAiAnalysis(any(), any());
    }

    @Test
    void shouldEnrichSpotsWithCurrentConditionsFromCache() throws Exception {
        // given
        var spot = createTestSpot(123, "Test Spot");
        var currentConditions = new CurrentConditions("2025-01-01 12:00", 15, 25, "NW", 10);

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot));
        when(currentConditionsService.fetchCurrentConditions(123)).thenReturn(Mono.just(currentConditions));

        aggregatorService.init();
        awaitSpotsLoaded(1);

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

    @Test
    void shouldCountLiveStationsWithNonEmptyConditions() {
        // given
        var spot1 = createTestSpot(123, "Test Spot 1");
        var spot2 = createTestSpot(124, "Test Spot 2");
        var spot3 = createTestSpot(125, "Test Spot 3");

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot1, spot2, spot3));

        var liveConditions = new CurrentConditions("2025-01-01T12:00:00", 15, 20, "S", 10);
        var emptyConditions = new CurrentConditions("2025-01-01T12:00:00", 0, 0, "", 0);

        when(currentConditionsService.fetchCurrentConditions(123)).thenReturn(Mono.just(liveConditions));
        when(currentConditionsService.fetchCurrentConditions(124)).thenReturn(Mono.just(emptyConditions));
        when(currentConditionsService.fetchCurrentConditions(125)).thenReturn(Mono.just(liveConditions));

        aggregatorService.init();
        awaitSpotsLoaded(3);

        // when
        aggregatorService.fetchCurrentConditionsEveryOneMinute();

        // then
        var liveStationsCount = aggregatorService.countLiveStations();
        assertThat(liveStationsCount).isEqualTo(2);
    }

    @Test
    void shouldCountZeroLiveStationsWhenAllConditionsAreEmpty() {
        // given
        var spot1 = createTestSpot(123, "Test Spot 1");
        var spot2 = createTestSpot(124, "Test Spot 2");

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot1, spot2));

        var emptyConditions = new CurrentConditions("2025-01-01T12:00:00", 0, 0, "", 0);

        when(currentConditionsService.fetchCurrentConditions(123)).thenReturn(Mono.just(emptyConditions));
        when(currentConditionsService.fetchCurrentConditions(124)).thenReturn(Mono.just(emptyConditions));

        aggregatorService.init();
        awaitSpotsLoaded(2);

        // when
        aggregatorService.fetchCurrentConditionsEveryOneMinute();

        // then
        var liveStationsCount = aggregatorService.countLiveStations();
        assertThat(liveStationsCount).isEqualTo(0);
    }

    @Test
    void shouldCountZeroLiveStationsWhenNoConditionsExist() {
        // given
        var spot1 = createTestSpot(123, "Test Spot 1");
        var spot2 = createTestSpot(124, "Test Spot 2");

        var spotsMap = new java.util.concurrent.ConcurrentHashMap<Integer, Spot>();
        spotsMap.put(spot1.wgId(), spot1);
        spotsMap.put(spot2.wgId(), spot2);
        ReflectionTestUtils.setField(aggregatorService, "spots", spotsMap);

        // when
        var liveStationsCount = aggregatorService.countLiveStations();

        // then
        assertThat(liveStationsCount).isEqualTo(0);
    }

    @Test
    void shouldReturnAverageForecastWhenModelKeyIsAverage() {
        // given
        var spot = createTestSpot(123, "Test Spot");
        var gfsHourly = List.of(new Forecast("Mon 01 Jan 2025 12:00", 10.0, 15.0, "N", 20.0, 0.0, 0, 0));
        var ifsHourly = List.of(new Forecast("Mon 01 Jan 2025 12:00", 14.0, 20.0, "N", 22.0, 1.0, 0, 0));

        var spotsMap = new java.util.concurrent.ConcurrentHashMap<Integer, Spot>();
        spotsMap.put(spot.wgId(), spot);
        ReflectionTestUtils.setField(aggregatorService, "spots", spotsMap);

        @SuppressWarnings("unchecked")
        var forecastCache = (java.util.concurrent.ConcurrentMap<Integer, ForecastData>)
                ReflectionTestUtils.getField(aggregatorService, "forecastCache");
        forecastCache.put(123, new ForecastData(List.of(), Map.of(
                ForecastModel.GFS, gfsHourly,
                ForecastModel.IFS, ifsHourly
        )));

        // when
        var result = aggregatorService.getSpotById(123, "average");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().forecastHourly()).hasSize(1);
        assertThat(result.get().forecastHourly().get(0).wind()).isEqualTo(12.0);
        assertThat(result.get().forecastHourly().get(0).gusts()).isEqualTo(17.5);
    }

    @Test
    void shouldDelegateToForecastModelWhenModelKeyIsNotAverage() {
        // given
        var spot = createTestSpot(123, "Test Spot");
        var gfsHourly = List.of(new Forecast("Mon 01 Jan 2025 12:00", 10.0, 15.0, "N", 20.0, 0.0, 0, 0));

        var spotsMap = new java.util.concurrent.ConcurrentHashMap<Integer, Spot>();
        spotsMap.put(spot.wgId(), spot);
        ReflectionTestUtils.setField(aggregatorService, "spots", spotsMap);

        @SuppressWarnings("unchecked")
        var forecastCache = (java.util.concurrent.ConcurrentMap<Integer, ForecastData>)
                ReflectionTestUtils.getField(aggregatorService, "forecastCache");
        forecastCache.put(123, new ForecastData(List.of(), Map.of(ForecastModel.GFS, gfsHourly)));

        // when
        var result = aggregatorService.getSpotById(123, "gfs");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().forecastHourly()).containsExactlyElementsOf(gfsHourly);
    }

    @Test
    void shouldIncludeAverageInAvailableModelsWhenTwoOrMoreModelsExist() {
        // given
        var spot = createTestSpot(123, "Test Spot");
        var gfsHourly = List.of(new Forecast("Mon 01 Jan 2025 12:00", 10.0, 15.0, "N", 20.0, 0.0, 0, 0));
        var ifsHourly = List.of(new Forecast("Mon 01 Jan 2025 12:00", 14.0, 20.0, "N", 22.0, 1.0, 0, 0));

        var spotsMap = new java.util.concurrent.ConcurrentHashMap<Integer, Spot>();
        spotsMap.put(spot.wgId(), spot);
        ReflectionTestUtils.setField(aggregatorService, "spots", spotsMap);

        @SuppressWarnings("unchecked")
        var forecastCache = (java.util.concurrent.ConcurrentMap<Integer, ForecastData>)
                ReflectionTestUtils.getField(aggregatorService, "forecastCache");
        forecastCache.put(123, new ForecastData(List.of(), Map.of(
                ForecastModel.GFS, gfsHourly,
                ForecastModel.IFS, ifsHourly
        )));
        markModelDiscoveryAsCompleted(123);

        // when
        var result = aggregatorService.getSpotById(123);

        // then
        assertThat(result).isPresent();
        var modelKeys = result.get().availableModels().stream()
                .map(m -> m.key())
                .toList();
        assertThat(modelKeys).contains("average");
    }

    @Test
    void shouldExposeOnlyDefaultModelWhenModelDiscoveryHasNotFinishedYet() {
        // given - a spot that already carries an on-demand ICM forecast, opened
        // before model discovery has run
        var spot = createTestSpot(123, "Test Spot");
        var gfsHourly = List.of(new Forecast("Mon 01 Jan 2025 12:00", 10.0, 15.0, "N", 20.0, 0.0, 0, 0));
        var icmHourly = List.of(new Forecast("Mon 01 Jan 2025 12:00", 12.0, 18.0, "N", 21.0, 0.0, 0, 0));

        var spotsMap = new java.util.concurrent.ConcurrentHashMap<Integer, Spot>();
        spotsMap.put(spot.wgId(), spot);
        ReflectionTestUtils.setField(aggregatorService, "spots", spotsMap);

        @SuppressWarnings("unchecked")
        var forecastCache = (java.util.concurrent.ConcurrentMap<Integer, ForecastData>)
                ReflectionTestUtils.getField(aggregatorService, "forecastCache");
        forecastCache.put(123, new ForecastData(List.of(), Map.of(
                ForecastModel.GFS, gfsHourly,
                ForecastModel.ICM_METEO, icmHourly
        )));
        markIcmForecastAsFresh(123);

        // when
        var result = aggregatorService.getSpotById(123);

        // then
        assertThat(result).isPresent();
        var modelKeys = result.get().availableModels().stream()
                .map(m -> m.key())
                .toList();
        assertThat(modelKeys).containsExactly("gfs");
    }

    @Test
    void shouldExposeAllModelsOnceModelDiscoveryHasFinished() {
        // given
        var spot = createTestSpot(123, "Test Spot");
        var gfsHourly = List.of(new Forecast("Mon 01 Jan 2025 12:00", 10.0, 15.0, "N", 20.0, 0.0, 0, 0));
        var icmHourly = List.of(new Forecast("Mon 01 Jan 2025 12:00", 12.0, 18.0, "N", 21.0, 0.0, 0, 0));

        var spotsMap = new java.util.concurrent.ConcurrentHashMap<Integer, Spot>();
        spotsMap.put(spot.wgId(), spot);
        ReflectionTestUtils.setField(aggregatorService, "spots", spotsMap);

        @SuppressWarnings("unchecked")
        var forecastCache = (java.util.concurrent.ConcurrentMap<Integer, ForecastData>)
                ReflectionTestUtils.getField(aggregatorService, "forecastCache");
        forecastCache.put(123, new ForecastData(List.of(), Map.of(
                ForecastModel.GFS, gfsHourly,
                ForecastModel.ICM_METEO, icmHourly
        )));
        markModelDiscoveryAsCompleted(123);
        markIcmForecastAsFresh(123);

        // when
        var result = aggregatorService.getSpotById(123);

        // then
        assertThat(result).isPresent();
        var modelKeys = result.get().availableModels().stream()
                .map(m -> m.key())
                .toList();
        assertThat(modelKeys).containsExactly("gfs", "icm", "average");
    }

    // The ICM model is published only while its on-demand result is still valid, so
    // a test seeding one straight into the forecast cache has to date it as well.
    private void markIcmForecastAsFresh(int spotId) {
        @SuppressWarnings("unchecked")
        var createdAt = (java.util.concurrent.ConcurrentMap<Integer, Long>)
                ReflectionTestUtils.getField(aggregatorService, "icmForecastCreatedAt");
        createdAt.put(spotId, System.currentTimeMillis());
    }

    private void markModelDiscoveryAsCompleted(int spotId) {
        @SuppressWarnings("unchecked")
        var timestamps = (java.util.concurrent.ConcurrentMap<Integer, Long>)
                ReflectionTestUtils.getField(aggregatorService, "hourlyForecastCacheTimestamps");
        timestamps.put(spotId, System.currentTimeMillis());
    }

    @Test
    void shouldNotIncludeAverageInAvailableModelsWhenOnlyOneModelExists() {
        // given
        var spot = createTestSpot(123, "Test Spot");
        var gfsHourly = List.of(new Forecast("Mon 01 Jan 2025 12:00", 10.0, 15.0, "N", 20.0, 0.0, 0, 0));

        var spotsMap = new java.util.concurrent.ConcurrentHashMap<Integer, Spot>();
        spotsMap.put(spot.wgId(), spot);
        ReflectionTestUtils.setField(aggregatorService, "spots", spotsMap);

        @SuppressWarnings("unchecked")
        var forecastCache = (java.util.concurrent.ConcurrentMap<Integer, ForecastData>)
                ReflectionTestUtils.getField(aggregatorService, "forecastCache");
        forecastCache.put(123, new ForecastData(List.of(), Map.of(ForecastModel.GFS, gfsHourly)));

        // when
        var result = aggregatorService.getSpotById(123);

        // then
        assertThat(result).isPresent();
        var modelKeys = result.get().availableModels().stream()
                .map(m -> m.key())
                .toList();
        assertThat(modelKeys).doesNotContain("average");
    }

    @Test
    void shouldFetchCoordinatesOnStartupWithoutAnyApiCall() {
        // given
        var spot = createTestSpotWithLocation(123, "Test Spot");
        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot));
        when(googleMapsService.getCoordinates(any(Spot.class)))
                .thenReturn(Mono.just(new Coordinates(54.0, 19.0)));

        // when - nobody calls getSpots(), only the application starts
        aggregatorService.init();

        // then
        @SuppressWarnings("unchecked")
        var coordinates = (java.util.concurrent.ConcurrentMap<Integer, Coordinates>)
                ReflectionTestUtils.getField(aggregatorService, "locationCoordinates");
        awaitUntil("coordinates to be resolved during warm-up", () -> coordinates.containsKey(123));

        verify(googleMapsService).getCoordinates(any(Spot.class));
        assertThat(coordinates).containsKey(123);
    }

    @Test
    void shouldResolveIcmUrlOnStartupWithoutAnyApiCall() {
        // given
        var spot = createTestSpotWithLocation(123, "Test Spot");
        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot));
        when(googleMapsService.getCoordinates(any(Spot.class)))
                .thenReturn(Mono.just(new Coordinates(54.0, 19.0)));
        when(icmGridMapper.toIcmUrl(54.0, 19.0, "Poland")).thenReturn(Optional.of("https://meteo.pl/icm"));

        // when - nobody calls getSpots(), only the application starts
        aggregatorService.init();

        // then
        @SuppressWarnings("unchecked")
        var icmUrls = (java.util.concurrent.ConcurrentMap<Integer, String>)
                ReflectionTestUtils.getField(aggregatorService, "icmUrls");
        awaitUntil("ICM URL to be resolved during warm-up", () -> icmUrls.containsKey(123));

        verify(icmGridMapper).toIcmUrl(54.0, 19.0, "Poland");
        assertThat(icmUrls).containsEntry(123, "https://meteo.pl/icm");
    }

    @Test
    void shouldServeCachedIcmUrlWithoutResolvingTheGridWhileServingSpots() {
        // given
        var spot = createTestSpot(123, "Test Spot");
        var spotsMap = new java.util.concurrent.ConcurrentHashMap<Integer, Spot>();
        spotsMap.put(spot.wgId(), spot);
        ReflectionTestUtils.setField(aggregatorService, "spots", spotsMap);

        @SuppressWarnings("unchecked")
        var coordinates = (java.util.concurrent.ConcurrentMap<Integer, Coordinates>)
                ReflectionTestUtils.getField(aggregatorService, "locationCoordinates");
        coordinates.put(123, new Coordinates(54.0, 19.0));

        @SuppressWarnings("unchecked")
        var icmUrls = (java.util.concurrent.ConcurrentMap<Integer, String>)
                ReflectionTestUtils.getField(aggregatorService, "icmUrls");
        icmUrls.put(123, "https://meteo.pl/icm");

        // when
        var result = aggregatorService.getSpots();

        // then - the grid is never validated over HTTP while a request is being served
        assertThat(result).hasSize(1);
        assertThat(result.get(0).icmUrl()).isEqualTo("https://meteo.pl/icm");
        verify(icmGridMapper, never()).toIcmUrl(anyDouble(), anyDouble(), any());
    }

    @Test
    void shouldNotGenerateIcmForecastWhenVisionIsDisabled() {
        // given
        var spot = createTestSpot(123, "Test Spot");
        var spotsMap = new java.util.concurrent.ConcurrentHashMap<Integer, Spot>();
        spotsMap.put(spot.wgId(), spot);
        ReflectionTestUtils.setField(aggregatorService, "spots", spotsMap);
        ReflectionTestUtils.setField(aggregatorService, "icmVisionEnabled", false);

        // when
        var generated = aggregatorService.generateIcmForecast(123).block();

        // then
        assertThat(generated).isFalse();
        verify(icmForecastVisionService, never()).extractForecastFromMeteogram(any());
    }

    @Test
    void shouldGenerateIcmForecastOnDemandAndCacheIt() {
        // given
        var spot = createTestSpot(123, "Test Spot");
        var icmForecast = List.of(new Forecast("Mon 01 Jan 2025 12:00", 12.0, 18.0, "N", 15.0, 0.0, 0, 0));

        var spotsMap = new java.util.concurrent.ConcurrentHashMap<Integer, Spot>();
        spotsMap.put(spot.wgId(), spot);
        ReflectionTestUtils.setField(aggregatorService, "spots", spotsMap);
        ReflectionTestUtils.setField(aggregatorService, "icmVisionEnabled", true);

        @SuppressWarnings("unchecked")
        var coordinates = (java.util.concurrent.ConcurrentMap<Integer, Coordinates>)
                ReflectionTestUtils.getField(aggregatorService, "locationCoordinates");
        coordinates.put(123, new Coordinates(54.0, 19.0));

        when(icmGridMapper.isCountrySupported("Poland")).thenReturn(true);
        when(icmGridMapper.toIcmUrl(54.0, 19.0, "Poland")).thenReturn(Optional.of("https://meteo.pl/icm"));
        when(icmForecastVisionService.extractForecastFromMeteogram("https://meteo.pl/icm"))
                .thenReturn(Optional.of(icmForecast));

        // when
        var generated = aggregatorService.generateIcmForecast(123).block();

        // then
        assertThat(generated).isTrue();
        assertThat(aggregatorService.hasValidIcmForecast(123)).isTrue();

        @SuppressWarnings("unchecked")
        var forecastCache = (java.util.concurrent.ConcurrentMap<Integer, ForecastData>)
                ReflectionTestUtils.getField(aggregatorService, "forecastCache");
        assertThat(forecastCache.get(123).hourly(ForecastModel.ICM_METEO)).isEqualTo(icmForecast);

        // and the on-demand discovery of the remaining models is not blocked by a fresh timestamp
        @SuppressWarnings("unchecked")
        var timestamps = (java.util.concurrent.ConcurrentMap<Integer, Long>)
                ReflectionTestUtils.getField(aggregatorService, "hourlyForecastCacheTimestamps");
        assertThat(timestamps).doesNotContainKey(123);
    }

    @Test
    void shouldNotReadTheMeteogramTwiceWithinItsLifetime() {
        // given
        var spot = createTestSpot(123, "Test Spot");
        var icmForecast = List.of(new Forecast("Mon 01 Jan 2025 12:00", 12.0, 18.0, "N", 15.0, 0.0, 0, 0));

        var spotsMap = new java.util.concurrent.ConcurrentHashMap<Integer, Spot>();
        spotsMap.put(spot.wgId(), spot);
        ReflectionTestUtils.setField(aggregatorService, "spots", spotsMap);
        ReflectionTestUtils.setField(aggregatorService, "icmVisionEnabled", true);

        @SuppressWarnings("unchecked")
        var coordinates = (java.util.concurrent.ConcurrentMap<Integer, Coordinates>)
                ReflectionTestUtils.getField(aggregatorService, "locationCoordinates");
        coordinates.put(123, new Coordinates(54.0, 19.0));

        when(icmGridMapper.isCountrySupported("Poland")).thenReturn(true);
        when(icmGridMapper.toIcmUrl(54.0, 19.0, "Poland")).thenReturn(Optional.of("https://meteo.pl/icm"));
        when(icmForecastVisionService.extractForecastFromMeteogram("https://meteo.pl/icm"))
                .thenReturn(Optional.of(icmForecast));

        // when
        aggregatorService.generateIcmForecast(123).block();
        var second = aggregatorService.generateIcmForecast(123).block();

        // then
        assertThat(second).isTrue();
        verify(icmForecastVisionService, times(1)).extractForecastFromMeteogram(any());
    }

    @Test
    void shouldSkipIcmForecastForCountriesOutsideTheIcmGrid() {
        // given
        var spot = createTestSpot(123, "Test Spot");
        var spotsMap = new java.util.concurrent.ConcurrentHashMap<Integer, Spot>();
        spotsMap.put(spot.wgId(), spot);
        ReflectionTestUtils.setField(aggregatorService, "spots", spotsMap);
        ReflectionTestUtils.setField(aggregatorService, "icmVisionEnabled", true);
        when(icmGridMapper.isCountrySupported("Poland")).thenReturn(false);

        // when
        var generated = aggregatorService.generateIcmForecast(123).block();

        // then
        assertThat(generated).isFalse();
        verify(icmForecastVisionService, never()).extractForecastFromMeteogram(any());
    }

    @Test
    void shouldDropAnExpiredIcmForecastFromTheModelList() {
        // given
        var spot = createTestSpot(123, "Test Spot");
        var icmForecast = List.of(new Forecast("Mon 01 Jan 2025 12:00", 12.0, 18.0, "N", 15.0, 0.0, 0, 0));

        var spotsMap = new java.util.concurrent.ConcurrentHashMap<Integer, Spot>();
        spotsMap.put(spot.wgId(), spot);
        ReflectionTestUtils.setField(aggregatorService, "spots", spotsMap);
        ReflectionTestUtils.setField(aggregatorService, "icmVisionEnabled", true);

        @SuppressWarnings("unchecked")
        var coordinates = (java.util.concurrent.ConcurrentMap<Integer, Coordinates>)
                ReflectionTestUtils.getField(aggregatorService, "locationCoordinates");
        coordinates.put(123, new Coordinates(54.0, 19.0));

        when(icmGridMapper.isCountrySupported("Poland")).thenReturn(true);
        when(icmGridMapper.toIcmUrl(54.0, 19.0, "Poland")).thenReturn(Optional.of("https://meteo.pl/icm"));
        when(icmForecastVisionService.extractForecastFromMeteogram("https://meteo.pl/icm"))
                .thenReturn(Optional.of(icmForecast));

        aggregatorService.generateIcmForecast(123).block();

        // when - backdated past its 24 hour lifetime
        @SuppressWarnings("unchecked")
        var createdAt = (java.util.concurrent.ConcurrentMap<Integer, Long>)
                ReflectionTestUtils.getField(aggregatorService, "icmForecastCreatedAt");
        createdAt.put(123, System.currentTimeMillis() - Duration.ofHours(25).toMillis());

        assertThat(aggregatorService.hasValidIcmForecast(123)).isFalse();

        aggregatorService.evictExpiredOnDemandData();

        // then - the forecast itself is gone, and so is the entry in the dropdown
        @SuppressWarnings("unchecked")
        var forecastCache = (java.util.concurrent.ConcurrentMap<Integer, ForecastData>)
                ReflectionTestUtils.getField(aggregatorService, "forecastCache");
        assertThat(forecastCache.get(123).hourly(ForecastModel.ICM_METEO)).isEmpty();
        assertThat(createdAt).doesNotContainKey(123);
    }

    @Test
    void shouldKeepIcmForecastWhenRefreshingGfsForecasts() throws Exception {
        // given
        var spot = createTestSpot(123, "Test Spot");
        var icmForecast = List.of(new Forecast("Mon 01 Jan 2025 12:00", 12.0, 18.0, "N", 15.0, 0.0, 0, 0));
        var gfsDaily = List.of(new Forecast("Mon", 10.0, 20.0, "N", 15.0, 0.0, 0, 0));

        when(spotsDataProvider.getSpots()).thenReturn(Flux.just(spot));
        when(forecastService.getForecastData(123))
                .thenReturn(Mono.just(new ForecastData(gfsDaily, Map.of())));

        aggregatorService.init();
        awaitSpotsLoaded(1);

        @SuppressWarnings("unchecked")
        var forecastCache = (java.util.concurrent.ConcurrentMap<Integer, ForecastData>)
                ReflectionTestUtils.getField(aggregatorService, "forecastCache");
        forecastCache.put(123, new ForecastData(List.of(), Map.of(ForecastModel.ICM_METEO, icmForecast)));

        // when
        aggregatorService.fetchForecastsEveryThreeHours();

        // then
        assertThat(forecastCache.get(123).hourly(ForecastModel.ICM_METEO)).isEqualTo(icmForecast);
        assertThat(forecastCache.get(123).daily()).isEqualTo(gfsDaily);
    }

    @Test
    void shouldServeTheWindTimelineOverTheRequestedSpan() {
        // given a forecast that runs twenty days out
        @SuppressWarnings("unchecked")
        var forecastCache = (java.util.concurrent.ConcurrentMap<Integer, ForecastData>)
                ReflectionTestUtils.getField(aggregatorService, "forecastCache");
        forecastCache.put(123, new ForecastData(List.of(), Map.of(ForecastModel.GFS, hourlyRun(20 * 24))));

        // then the default span is the five days a phone-sized slider can address
        assertThat(aggregatorService.getWindTimeline().hours()).hasSize(5 * 24);

        // and a wider screen gets as many hours as it asks for
        assertThat(aggregatorService.getWindTimeline(10 * 24).hours()).hasSize(10 * 24);

        // however much that is - the grid is capped at the sixteen days the forecast runs
        assertThat(aggregatorService.getWindTimeline(40 * 24).hours()).hasSize(16 * 24);
    }

    /**
     * Hourly forecasts on whole hours starting at the current one, in the shape the
     * timeline mapper parses.
     */
    private List<Forecast> hourlyRun(int hours) {
        var formatter = java.time.format.DateTimeFormatter
                .ofPattern("EEE dd MMM yyyy HH:mm", java.util.Locale.ENGLISH);
        var start = java.time.LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        var forecasts = new ArrayList<Forecast>(hours);
        for (int hour = 0; hour < hours; hour++) {
            forecasts.add(new Forecast(start.plusHours(hour).format(formatter), 12, 16, "NW", 15, 0, 0, 1013));
        }
        return forecasts;
    }

    private Spot createTestSpotWithLocation(int wgId, String name) {
        var spot = createTestSpot(wgId, name);
        return new Spot(
                spot.name(),
                spot.country(),
                spot.windguruUrl(),
                null,
                null,
                null,
                null,
                "https://maps.google.com/@54.0,19.0",
                null,
                new ArrayList<>(),
                spot.forecast(),
                spot.forecastHourly(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private Spot createTestSpot(int wgId, String name) {
        var forecast = new ArrayList<Forecast>();
        var hourlyForecast = new ArrayList<Forecast>();
        return new Spot(
                name,
                "Poland",
                "https://windguru.cz/" + wgId,
                null, // windguruFallbackUrl
                null,
                null,
                null,
                null,
                null,
                new ArrayList<>(),
                forecast,
                hourlyForecast,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
