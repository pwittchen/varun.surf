package com.github.pwittchen.varun.service.health;

public record HealthCheckResult(
        long timestamp,
        boolean healthy,
        long latencyMs
) {}