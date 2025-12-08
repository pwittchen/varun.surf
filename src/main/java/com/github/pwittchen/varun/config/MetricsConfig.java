package com.github.pwittchen.varun.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Configuration
public class MetricsConfig {

    @Bean
    public AtomicInteger spotsCount() {
        return new AtomicInteger(0);
    }

    @Bean
    public AtomicInteger countriesCount() {
        return new AtomicInteger(0);
    }

    @Bean
    public AtomicInteger liveStationsCount() {
        return new AtomicInteger(0);
    }

    @Bean
    public AtomicInteger forecastCacheSize() {
        return new AtomicInteger(0);
    }

    @Bean
    public AtomicInteger currentConditionsCacheSize() {
        return new AtomicInteger(0);
    }

    @Bean
    public AtomicLong lastForecastFetchTimestamp() {
        return new AtomicLong(0);
    }

    @Bean
    public AtomicLong lastConditionsFetchTimestamp() {
        return new AtomicLong(0);
    }

    @Bean
    public Gauge spotsGauge(MeterRegistry registry, AtomicInteger spotsCount) {
        return Gauge.builder("varun.spots.total", spotsCount, AtomicInteger::get)
                .description("Total number of kite spots")
                .register(registry);
    }

    @Bean
    public Gauge countriesGauge(MeterRegistry registry, AtomicInteger countriesCount) {
        return Gauge.builder("varun.countries.total", countriesCount, AtomicInteger::get)
                .description("Total number of countries with kite spots")
                .register(registry);
    }

    @Bean
    public Gauge liveStationsGauge(MeterRegistry registry, AtomicInteger liveStationsCount) {
        return Gauge.builder("varun.live_stations.active", liveStationsCount, AtomicInteger::get)
                .description("Number of active live weather stations")
                .register(registry);
    }

    @Bean
    public Gauge forecastCacheSizeGauge(MeterRegistry registry, AtomicInteger forecastCacheSize) {
        return Gauge.builder("varun.cache.forecasts.size", forecastCacheSize, AtomicInteger::get)
                .description("Number of spots with cached forecasts")
                .register(registry);
    }

    @Bean
    public Gauge currentConditionsCacheSizeGauge(MeterRegistry registry, AtomicInteger currentConditionsCacheSize) {
        return Gauge.builder("varun.cache.conditions.size", currentConditionsCacheSize, AtomicInteger::get)
                .description("Number of spots with cached current conditions")
                .register(registry);
    }

    @Bean
    public Gauge lastForecastFetchGauge(MeterRegistry registry, AtomicLong lastForecastFetchTimestamp) {
        return Gauge.builder("varun.fetch.forecasts.last_timestamp", lastForecastFetchTimestamp, AtomicLong::get)
                .description("Timestamp of last successful forecast fetch")
                .register(registry);
    }

    @Bean
    public Gauge lastConditionsFetchGauge(MeterRegistry registry, AtomicLong lastConditionsFetchTimestamp) {
        return Gauge.builder("varun.fetch.conditions.last_timestamp", lastConditionsFetchTimestamp, AtomicLong::get)
                .description("Timestamp of last successful conditions fetch")
                .register(registry);
    }

    @Bean
    public Counter forecastFetchCounter(MeterRegistry registry) {
        return Counter.builder("varun.fetch.forecasts.total")
                .description("Total number of forecast fetch operations")
                .register(registry);
    }

    @Bean
    public Counter forecastFetchSuccessCounter(MeterRegistry registry) {
        return Counter.builder("varun.fetch.forecasts.success")
                .description("Number of successful forecast fetch operations")
                .register(registry);
    }

    @Bean
    public Counter forecastFetchFailureCounter(MeterRegistry registry) {
        return Counter.builder("varun.fetch.forecasts.failure")
                .description("Number of failed forecast fetch operations")
                .register(registry);
    }

    @Bean
    public Counter conditionsFetchCounter(MeterRegistry registry) {
        return Counter.builder("varun.fetch.conditions.total")
                .description("Total number of current conditions fetch operations")
                .register(registry);
    }

    @Bean
    public Counter conditionsFetchSuccessCounter(MeterRegistry registry) {
        return Counter.builder("varun.fetch.conditions.success")
                .description("Number of successful current conditions fetch operations")
                .register(registry);
    }

    @Bean
    public Counter conditionsFetchFailureCounter(MeterRegistry registry) {
        return Counter.builder("varun.fetch.conditions.failure")
                .description("Number of failed current conditions fetch operations")
                .register(registry);
    }

    @Bean
    public Counter aiFetchCounter(MeterRegistry registry) {
        return Counter.builder("varun.fetch.ai.total")
                .description("Total number of AI analysis fetch operations")
                .register(registry);
    }

    @Bean
    public Counter aiFetchSuccessCounter(MeterRegistry registry) {
        return Counter.builder("varun.fetch.ai.success")
                .description("Number of successful AI analysis fetch operations")
                .register(registry);
    }

    @Bean
    public Counter aiFetchFailureCounter(MeterRegistry registry) {
        return Counter.builder("varun.fetch.ai.failure")
                .description("Number of failed AI analysis fetch operations")
                .register(registry);
    }

    @Bean
    public Timer forecastFetchTimer(MeterRegistry registry) {
        return Timer.builder("varun.fetch.forecasts.duration")
                .description("Duration of forecast fetch operations")
                .register(registry);
    }

    @Bean
    public Timer conditionsFetchTimer(MeterRegistry registry) {
        return Timer.builder("varun.fetch.conditions.duration")
                .description("Duration of current conditions fetch operations")
                .register(registry);
    }

    @Bean
    public Timer aiFetchTimer(MeterRegistry registry) {
        return Timer.builder("varun.fetch.ai.duration")
                .description("Duration of AI analysis fetch operations")
                .register(registry);
    }

    @Bean
    public Counter apiSpotsRequestCounter(MeterRegistry registry) {
        return Counter.builder("varun.api.spots.requests")
                .description("Number of requests to /api/v1/spots endpoint")
                .register(registry);
    }

    @Bean
    public Counter apiSpotByIdRequestCounter(MeterRegistry registry) {
        return Counter.builder("varun.api.spot.requests")
                .description("Number of requests to /api/v1/spots/{id} endpoint")
                .register(registry);
    }
}
