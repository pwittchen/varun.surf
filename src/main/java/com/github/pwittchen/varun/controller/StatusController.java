package com.github.pwittchen.varun.controller;

import com.github.pwittchen.varun.model.status.Uptime;
import com.github.pwittchen.varun.service.AggregatorService;
import com.github.pwittchen.varun.service.health.HealthCheckResult;
import com.github.pwittchen.varun.service.health.HealthHistoryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/")
public class StatusController {

    private final Instant startTime = Instant.now();
    private final AggregatorService aggregatorService;
    private final HealthHistoryService healthHistoryService;

    @Value("${spring.application.version:unknown}")
    private String version;

    public StatusController(AggregatorService aggregatorService, HealthHistoryService healthHistoryService) {
        this.aggregatorService = aggregatorService;
        this.healthHistoryService = healthHistoryService;
    }

    @GetMapping("health")
    public Mono<Map<String, String>> health() {
        return Mono.just(Map.of("status", "UP"));
    }

    @GetMapping("status")
    public Mono<Map<String, Object>> status() {
        Uptime uptime = getUptime();
        int spotsCount = aggregatorService.countSpots();
        int countriesCount = aggregatorService.countCountries();
        int liveStations = aggregatorService.countLiveStations();
        return Mono.just(Map.of(
                "status", "UP",
                "version", version,
                "uptime", uptime.formatted(),
                "uptimeSeconds", uptime.seconds(),
                "startTime", startTime.toString(),
                "spotsCount", spotsCount,
                "countriesCount", countriesCount,
                "liveStations", liveStations
        ));
    }

    @GetMapping("status/history")
    public Mono<Map<String, Object>> healthHistory() {
        List<HealthCheckResult> history = healthHistoryService.getHistory();
        HealthHistoryService.HealthHistorySummary summary = healthHistoryService.getSummary();

        Map<String, Object> result = new HashMap<>();
        result.put("history", history);
        result.put("summary", Map.of(
                "totalChecks", summary.totalChecks(),
                "successfulChecks", summary.successfulChecks(),
                "uptimePercentage", summary.uptimePercentage(),
                "avgLatencyMs", summary.avgLatencyMs(),
                "oldestCheckTimestamp", summary.oldestCheckTimestamp()
        ));
        result.put("currentlyHealthy", healthHistoryService.isCurrentlyHealthy());

        return Mono.just(result);
    }

    private Uptime getUptime() {
        Duration uptime = Duration.between(startTime, Instant.now());
        long days = uptime.toDays();
        long hours = uptime.toHoursPart();
        long minutes = uptime.toMinutesPart();
        long seconds = uptime.toSecondsPart();
        String formattedUptime = String.format("%dd %dh %dm %ds", days, hours, minutes, seconds);
        return new Uptime(seconds, formattedUptime);
    }
}
