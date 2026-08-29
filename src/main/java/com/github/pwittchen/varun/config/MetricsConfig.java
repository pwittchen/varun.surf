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

    // On-demand generation. Both of these are counted per spot rather than per
    // sweep - there is no sweep any more - so the totals are also the LLM call
    // counts, which is what the bill is made of.

    @Bean
    public Counter aiAnalysisCounter(MeterRegistry registry) {
        return Counter.builder("varun.ondemand.ai.total")
                .description("Total number of on-demand AI analysis generations")
                .register(registry);
    }

    @Bean
    public Counter aiAnalysisSuccessCounter(MeterRegistry registry) {
        return Counter.builder("varun.ondemand.ai.success")
                .description("Number of successful on-demand AI analysis generations")
                .register(registry);
    }

    @Bean
    public Counter aiAnalysisFailureCounter(MeterRegistry registry) {
        return Counter.builder("varun.ondemand.ai.failure")
                .description("Number of failed on-demand AI analysis generations")
                .register(registry);
    }

    @Bean
    public Counter icmAnalysisCounter(MeterRegistry registry) {
        return Counter.builder("varun.ondemand.icm.total")
                .description("Total number of on-demand ICM meteogram readings")
                .register(registry);
    }

    @Bean
    public Counter icmAnalysisSuccessCounter(MeterRegistry registry) {
        return Counter.builder("varun.ondemand.icm.success")
                .description("Number of successful on-demand ICM meteogram readings")
                .register(registry);
    }

    @Bean
    public Counter icmAnalysisFailureCounter(MeterRegistry registry) {
        return Counter.builder("varun.ondemand.icm.failure")
                .description("Number of failed on-demand ICM meteogram readings")
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
    public Timer aiAnalysisTimer(MeterRegistry registry) {
        return Timer.builder("varun.ondemand.ai.duration")
                .description("Duration of on-demand AI analysis generations")
                .register(registry);
    }

    @Bean
    public Timer icmAnalysisTimer(MeterRegistry registry) {
        return Timer.builder("varun.ondemand.icm.duration")
                .description("Duration of on-demand ICM meteogram readings")
                .register(registry);
    }
}
