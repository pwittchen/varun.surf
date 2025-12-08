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
    private final Counter aiFetchCounter;
    private final Counter aiFetchSuccessCounter;
    private final Counter aiFetchFailureCounter;
    private final Timer forecastFetchTimer;
    private final Timer conditionsFetchTimer;
    private final Timer aiFetchTimer;
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
            Counter aiFetchCounter,
            Counter aiFetchSuccessCounter,
            Counter aiFetchFailureCounter,
            Timer forecastFetchTimer,
            Timer conditionsFetchTimer,
            Timer aiFetchTimer,
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
        this.aiFetchCounter = aiFetchCounter;
        this.aiFetchSuccessCounter = aiFetchSuccessCounter;
        this.aiFetchFailureCounter = aiFetchFailureCounter;
        this.forecastFetchTimer = forecastFetchTimer;
        this.conditionsFetchTimer = conditionsFetchTimer;
        this.aiFetchTimer = aiFetchTimer;
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

    public void incrementAiFetchCounter() {
        aiFetchCounter.increment();
    }

    public void incrementAiFetchSuccessCounter() {
        aiFetchSuccessCounter.increment();
    }

    public void incrementAiFetchFailureCounter() {
        aiFetchFailureCounter.increment();
    }

    public void recordForecastFetchDuration(long startTimeNanos) {
        forecastFetchTimer.record(Duration.ofNanos(System.nanoTime() - startTimeNanos));
    }

    public void recordConditionsFetchDuration(long startTimeNanos) {
        conditionsFetchTimer.record(Duration.ofNanos(System.nanoTime() - startTimeNanos));
    }

    public void recordAiFetchDuration(long startTimeNanos) {
        aiFetchTimer.record(Duration.ofNanos(System.nanoTime() - startTimeNanos));
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