package com.github.pwittchen.varun.controller;

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
public class AppController {

    private final Instant startTime = Instant.now();

    @Value("${spring.application.version:unknown}")
    private String version;

    @GetMapping("health")
    public Mono<Map<String, String>> health() {
        return Mono.just(Map.of("status", "UP"));
    }

    @GetMapping("uptime")
    public Mono<Map<String, Object>> uptime() {
        Duration uptime = Duration.between(startTime, Instant.now());
        long days = uptime.toDays();
        long hours = uptime.toHoursPart();
        long minutes = uptime.toMinutesPart();
        long seconds = uptime.toSecondsPart();

        String formattedUptime = String.format("%dd %dh %dm %ds", days, hours, minutes, seconds);

        return Mono.just(Map.of(
            "uptime", formattedUptime,
            "uptimeSeconds", uptime.toSeconds(),
            "startTime", startTime.toString()
        ));
    }

    @GetMapping("version")
    public Mono<Map<String, String>> version() {
        return Mono.just(Map.of("version", version));
    }
}
