package com.github.pwittchen.varun.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class SpotsControllerMetrics {

    private final Counter apiSpotsRequestCounter;
    private final Counter apiSpotByIdRequestCounter;
    private final Counter apiWindRequestCounter;
    private final Counter apiForecastRequestCounter;

    public SpotsControllerMetrics(MeterRegistry registry) {
        this.apiSpotsRequestCounter = Counter
                .builder("varun.api.spots.requests")
                .description("Number of requests to /api/v1/spots endpoint")
                .register(registry);
        this.apiSpotByIdRequestCounter = Counter
                .builder("varun.api.spot.requests")
                .description("Number of requests to /api/v1/spots/{id} endpoint")
                .register(registry);
        this.apiWindRequestCounter = Counter
                .builder("varun.api.wind.requests")
                .description("Number of requests to /api/v1/wind endpoint")
                .register(registry);
        this.apiForecastRequestCounter = Counter
                .builder("varun.api.forecast.requests")
                .description("Number of requests to /api/v1/forecast/{wgId} endpoint")
                .register(registry);
    }

    public void incrementSpotsRequestCounter() {
        apiSpotsRequestCounter.increment();
    }

    public void incrementSpotByIdRequestCounter() {
        apiSpotByIdRequestCounter.increment();
    }

    public void incrementWindRequestCounter() {
        apiWindRequestCounter.increment();
    }

    public void incrementForecastRequestCounter() {
        apiForecastRequestCounter.increment();
    }
}