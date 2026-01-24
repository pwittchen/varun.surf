package com.github.pwittchen.varun.controller;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/")
public class MetricsController {

    private final MeterRegistry meterRegistry;

    @Value("${app.metrics.password:}")
    private String metricsPassword;

    public MetricsController(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @PostMapping("metrics/auth")
    public Mono<Map<String, Object>> authenticate(@RequestBody Map<String, String> body) {
        String password = body.get("password");
        if (metricsPassword.isEmpty() || metricsPassword.equals(password)) {
            return Mono.just(Map.of("authenticated", true));
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid password");
    }

    @GetMapping("metrics")
    public Mono<Map<String, Object>> metrics(
            @RequestHeader(value = "X-Metrics-Password", required = false) String password) {

        if (!metricsPassword.isEmpty() && !metricsPassword.equals(password)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid password");
        }

        Map<String, Object> result = new HashMap<>();

        // Application gauges
        result.put("gauges", getGauges());

        // Application counters
        result.put("counters", getCounters());

        // Application timers
        result.put("timers", getTimers());

        // JVM metrics
        result.put("jvm", getJvmMetrics());

        // HTTP client metrics
        result.put("httpClient", getHttpClientMetrics());

        // Timestamp
        result.put("timestamp", Instant.now().toString());

        return Mono.just(result);
    }

    private Map<String, Object> getGauges() {
        Map<String, Object> gauges = new HashMap<>();

        gauges.put("spotsTotal", getGaugeValue("varun.spots.total"));
        gauges.put("countriesTotal", getGaugeValue("varun.countries.total"));
        gauges.put("liveStationsActive", getGaugeValue("varun.live_stations.active"));
        gauges.put("forecastsCacheSize", getGaugeValue("varun.cache.forecasts.size"));
        gauges.put("conditionsCacheSize", getGaugeValue("varun.cache.conditions.size"));
        gauges.put("lastForecastFetch", getGaugeValue("varun.fetch.forecasts.last_timestamp"));
        gauges.put("lastConditionsFetch", getGaugeValue("varun.fetch.conditions.last_timestamp"));

        return gauges;
    }

    private Map<String, Object> getCounters() {
        Map<String, Object> counters = new HashMap<>();

        // Forecast counters
        counters.put("forecastsTotal", getCounterValue("varun.fetch.forecasts.total"));
        counters.put("forecastsSuccess", getCounterValue("varun.fetch.forecasts.success"));
        counters.put("forecastsFailure", getCounterValue("varun.fetch.forecasts.failure"));

        // Conditions counters
        counters.put("conditionsTotal", getCounterValue("varun.fetch.conditions.total"));
        counters.put("conditionsSuccess", getCounterValue("varun.fetch.conditions.success"));
        counters.put("conditionsFailure", getCounterValue("varun.fetch.conditions.failure"));

        // AI counters
        counters.put("aiTotal", getCounterValue("varun.fetch.ai.total"));
        counters.put("aiSuccess", getCounterValue("varun.fetch.ai.success"));
        counters.put("aiFailure", getCounterValue("varun.fetch.ai.failure"));

        // API request counters
        counters.put("apiSpotsRequests", getCounterValue("varun.api.spots.requests"));
        counters.put("apiSpotRequests", getCounterValue("varun.api.spot.requests"));

        return counters;
    }

    private Map<String, Object> getTimers() {
        Map<String, Object> timers = new HashMap<>();

        timers.put("forecastsDuration", getTimerStats("varun.fetch.forecasts.duration"));
        timers.put("conditionsDuration", getTimerStats("varun.fetch.conditions.duration"));
        timers.put("aiDuration", getTimerStats("varun.fetch.ai.duration"));

        return timers;
    }

    private Map<String, Object> getJvmMetrics() {
        Map<String, Object> jvm = new HashMap<>();

        // Memory
        double heapUsed = getGaugeValue("jvm.memory.used", "area", "heap");
        double heapMax = getGaugeValue("jvm.memory.max", "area", "heap");
        double nonHeapUsed = getGaugeValue("jvm.memory.used", "area", "nonheap");

        jvm.put("heapUsed", heapUsed);
        jvm.put("heapMax", heapMax);
        jvm.put("heapUsedPercent", heapMax > 0 ? (heapUsed / heapMax) * 100 : 0);
        jvm.put("nonHeapUsed", nonHeapUsed);

        // Threads
        jvm.put("threadsLive", getGaugeValue("jvm.threads.live"));
        jvm.put("threadsPeak", getGaugeValue("jvm.threads.peak"));
        jvm.put("threadsDaemon", getGaugeValue("jvm.threads.daemon"));

        // GC
        jvm.put("gcPauseCount", getCounterValue("jvm.gc.pause"));
        jvm.put("gcPauseTime", getTimerStats("jvm.gc.pause"));

        // CPU
        jvm.put("cpuUsage", getGaugeValue("process.cpu.usage") * 100);
        jvm.put("systemCpuUsage", getGaugeValue("system.cpu.usage") * 100);

        // Uptime
        jvm.put("uptimeSeconds", getGaugeValue("process.uptime"));

        return jvm;
    }

    private Map<String, Object> getHttpClientMetrics() {
        Map<String, Object> http = new HashMap<>();

        // Outgoing HTTP client metrics
        http.put("activeRequests", getGaugeValue("varun.http.client.active_requests"));
        http.put("totalRequests", getCounterValue("varun.http.client.requests.total"));
        http.put("successRequests", getCounterValue("varun.http.client.requests.success"));
        http.put("failedRequests", getCounterValue("varun.http.client.requests.failed"));
        http.put("requestDuration", getTimerStats("varun.http.client.request.duration"));
        http.put("connectionsAcquired", getCounterValue("varun.http.client.connections.acquired"));
        http.put("connectionsReleased", getCounterValue("varun.http.client.connections.released"));
        http.put("connectFailed", getCounterValue("varun.http.client.connect.failed"));
        http.put("dnsDuration", getTimerStats("varun.http.client.dns.duration"));
        http.put("connectDuration", getTimerStats("varun.http.client.connect.duration"));

        // Incoming HTTP server metrics (Spring WebFlux)
        http.put("serverRequests", getTimerStats("http.server.requests"));

        return http;
    }

    private double getGaugeValue(String name) {
        return meterRegistry.find(name)
                .gauges()
                .stream()
                .findFirst()
                .map(Gauge::value)
                .orElse(0.0);
    }

    private double getGaugeValue(String name, String tagKey, String tagValue) {
        return meterRegistry.find(name)
                .tag(tagKey, tagValue)
                .gauges()
                .stream()
                .mapToDouble(Gauge::value)
                .sum();
    }

    private double getCounterValue(String name) {
        return meterRegistry.find(name)
                .counters()
                .stream()
                .mapToDouble(Counter::count)
                .sum();
    }

    private Map<String, Object> getTimerStats(String name) {
        Map<String, Object> stats = new HashMap<>();

        List<Timer> timers = meterRegistry.find(name)
                .timers()
                .stream()
                .toList();

        if (timers.isEmpty()) {
            stats.put("count", 0L);
            stats.put("totalTimeMs", 0.0);
            stats.put("meanMs", 0.0);
            stats.put("maxMs", 0.0);
            return stats;
        }

        long totalCount = timers.stream().mapToLong(Timer::count).sum();
        double totalTimeMs = timers.stream()
                .mapToDouble(t -> t.totalTime(TimeUnit.MILLISECONDS))
                .sum();
        double maxMs = timers.stream()
                .mapToDouble(t -> t.max(TimeUnit.MILLISECONDS))
                .max()
                .orElse(0.0);

        stats.put("count", totalCount);
        stats.put("totalTimeMs", totalTimeMs);
        stats.put("meanMs", totalCount > 0 ? totalTimeMs / totalCount : 0.0);
        stats.put("maxMs", maxMs);

        return stats;
    }
}
