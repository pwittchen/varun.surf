package com.github.pwittchen.varun.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class AggregatorServiceMetrics {

    private final Counter forecastFetchCounter;
    private final Counter forecastFetchSuccessCounter;
    private final Counter forecastFetchFailureCounter;
    private final Counter conditionsFetchCounter;
    private final Counter conditionsFetchSuccessCounter;
    private final Counter conditionsFetchFailureCounter;
    private final Counter aiAnalysisCounter;
    private final Counter aiAnalysisSuccessCounter;
    private final Counter aiAnalysisFailureCounter;
    private final Timer forecastFetchTimer;
    private final Timer conditionsFetchTimer;
    private final Timer aiAnalysisTimer;
    private final AtomicInteger spotsCount;
    private final AtomicInteger countriesCount;
    private final AtomicInteger liveStationsCount;
    private final AtomicInteger forecastCacheSize;
    private final AtomicInteger currentConditionsCacheSize;
    private final AtomicLong lastForecastFetchTimestamp;
    private final AtomicLong lastConditionsFetchTimestamp;

    public AggregatorServiceMetrics(
            Counter forecastFetchCounter,
            Counter forecastFetchSuccessCounter,
            Counter forecastFetchFailureCounter,
            Counter conditionsFetchCounter,
            Counter conditionsFetchSuccessCounter,
            Counter conditionsFetchFailureCounter,
            Counter aiAnalysisCounter,
            Counter aiAnalysisSuccessCounter,
            Counter aiAnalysisFailureCounter,
            Timer forecastFetchTimer,
            Timer conditionsFetchTimer,
            Timer aiAnalysisTimer,
            AtomicInteger spotsCount,
            AtomicInteger countriesCount,
            AtomicInteger liveStationsCount,
            AtomicInteger forecastCacheSize,
            AtomicInteger currentConditionsCacheSize,
            AtomicLong lastForecastFetchTimestamp,
            AtomicLong lastConditionsFetchTimestamp) {
        this.forecastFetchCounter = forecastFetchCounter;
        this.forecastFetchSuccessCounter = forecastFetchSuccessCounter;
        this.forecastFetchFailureCounter = forecastFetchFailureCounter;
        this.conditionsFetchCounter = conditionsFetchCounter;
        this.conditionsFetchSuccessCounter = conditionsFetchSuccessCounter;
        this.conditionsFetchFailureCounter = conditionsFetchFailureCounter;
        this.aiAnalysisCounter = aiAnalysisCounter;
        this.aiAnalysisSuccessCounter = aiAnalysisSuccessCounter;
        this.aiAnalysisFailureCounter = aiAnalysisFailureCounter;
        this.forecastFetchTimer = forecastFetchTimer;
        this.conditionsFetchTimer = conditionsFetchTimer;
        this.aiAnalysisTimer = aiAnalysisTimer;
        this.spotsCount = spotsCount;
        this.countriesCount = countriesCount;
        this.liveStationsCount = liveStationsCount;
        this.forecastCacheSize = forecastCacheSize;
        this.currentConditionsCacheSize = currentConditionsCacheSize;
        this.lastForecastFetchTimestamp = lastForecastFetchTimestamp;
        this.lastConditionsFetchTimestamp = lastConditionsFetchTimestamp;
    }

    public void incrementForecastFetchCounter() {
        forecastFetchCounter.increment();
    }

    public void incrementForecastFetchSuccessCounter() {
        forecastFetchSuccessCounter.increment();
    }

    public void incrementForecastFetchFailureCounter() {
        forecastFetchFailureCounter.increment();
    }

    public void incrementConditionsFetchCounter() {
        conditionsFetchCounter.increment();
    }

    public void incrementConditionsFetchSuccessCounter() {
        conditionsFetchSuccessCounter.increment();
    }

    public void incrementConditionsFetchFailureCounter() {
        conditionsFetchFailureCounter.increment();
    }

    public void incrementAiAnalysisCounter() {
        aiAnalysisCounter.increment();
    }

    public void incrementAiAnalysisSuccessCounter() {
        aiAnalysisSuccessCounter.increment();
    }

    public void incrementAiAnalysisFailureCounter() {
        aiAnalysisFailureCounter.increment();
    }

    public void recordForecastFetchDuration(long startTimeNanos) {
        forecastFetchTimer.record(Duration.ofNanos(System.nanoTime() - startTimeNanos));
    }

    public void recordConditionsFetchDuration(long startTimeNanos) {
        conditionsFetchTimer.record(Duration.ofNanos(System.nanoTime() - startTimeNanos));
    }

    /**
     * On-demand generations are timed with Reactor's {@code elapsed()}, which
     * already hands over the wall-clock milliseconds a subscription took, so unlike
     * the sweep timers above these take a duration rather than a start stamp.
     */
    public void recordAiAnalysisDuration(long durationMs) {
        aiAnalysisTimer.record(Duration.ofMillis(durationMs));
    }

    public void updateLastForecastFetchTimestamp() {
        lastForecastFetchTimestamp.set(System.currentTimeMillis());
    }

    public void updateLastConditionsFetchTimestamp() {
        lastConditionsFetchTimestamp.set(System.currentTimeMillis());
    }

    public void updateGauges(int spots, int countries, int liveStations, int forecastCache, int conditionsCache) {
        spotsCount.set(spots);
        countriesCount.set(countries);
        liveStationsCount.set(liveStations);
        forecastCacheSize.set(forecastCache);
        currentConditionsCacheSize.set(conditionsCache);
    }
}