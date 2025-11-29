package com.github.pwittchen.varun.controller;

import com.github.pwittchen.varun.model.status.Uptime;
import com.github.pwittchen.varun.service.AggregatorService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/")
public class StatusController {

    private final Instant startTime = Instant.now();
    private final AggregatorService aggregatorService;

    @Value("${spring.application.version:unknown}")
    private String version;

    public StatusController(AggregatorService aggregatorService) {
        this.aggregatorService = aggregatorService;
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
        return Mono.just(Map.of(
                "status", "UP",
                "version", version,
                "uptime", uptime.formatted(),
                "uptimeSeconds", uptime.seconds(),
                "startTime", startTime.toString(),
                "spotsCount", spotsCount,
                "countriesCount", countriesCount
        ));
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
