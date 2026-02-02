package com.github.pwittchen.varun.service.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;

class HealthHistoryServiceTest {

    private HealthHistoryService service;

    @BeforeEach
    void setUp() {
        service = new HealthHistoryService();
    }

    @Test
    void shouldReturnEmptyHistoryInitially() {
        List<HealthCheckResult> history = service.getHistory();

        assertThat(history).isEmpty();
    }

    @Test
    void shouldRecordHealthCheck() {
        service.recordHealthCheck(true, 50);

        List<HealthCheckResult> history = service.getHistory();
        assertThat(history).hasSize(1);
        assertThat(history.get(0).healthy()).isTrue();
        assertThat(history.get(0).latencyMs()).isEqualTo(50);
    }

    @Test
    void shouldRecordFailure() {
        service.recordFailure();

        List<HealthCheckResult> history = service.getHistory();
        assertThat(history).hasSize(1);
        assertThat(history.get(0).healthy()).isFalse();
    }

    @Test
    void shouldTrackCurrentHealthStatus() {
        service.recordHealthCheck(true, 10);
        assertThat(service.isCurrentlyHealthy()).isTrue();

        service.recordHealthCheck(false, 0);
        assertThat(service.isCurrentlyHealthy()).isFalse();

        service.recordHealthCheck(true, 5);
        assertThat(service.isCurrentlyHealthy()).isTrue();
    }

    @Test
    void shouldCalculateSummaryCorrectly() {
        service.recordHealthCheck(true, 10);
        service.recordHealthCheck(true, 20);
        service.recordHealthCheck(false, 0);
        service.recordHealthCheck(true, 30);

        HealthHistoryService.HealthHistorySummary summary = service.getSummary();

        assertThat(summary.totalChecks()).isEqualTo(4);
        assertThat(summary.successfulChecks()).isEqualTo(3);
        assertThat(summary.uptimePercentage()).isEqualTo(75.0);
        assertThat(summary.avgLatencyMs()).isEqualTo(20); // (10+20+30)/3
    }

    @Test
    void shouldReturnFullUptimeWhenAllHealthy() {
        service.recordHealthCheck(true, 10);
        service.recordHealthCheck(true, 20);
        service.recordHealthCheck(true, 30);

        HealthHistoryService.HealthHistorySummary summary = service.getSummary();

        assertThat(summary.uptimePercentage()).isEqualTo(100.0);
    }

    @Test
    void shouldLimitHistoryTo90Entries() {
        for (int i = 0; i < 100; i++) {
            service.recordHealthCheck(true, i);
        }

        List<HealthCheckResult> history = service.getHistory();
        assertThat(history).hasSize(90);
    }

    @Test
    void shouldEvictOldestEntriesWhenFull() {
        for (int i = 0; i < 100; i++) {
            service.recordHealthCheck(true, i);
        }

        List<HealthCheckResult> history = service.getHistory();
        // First entry should have latency 10 (entries 0-9 were evicted)
        assertThat(history.get(0).latencyMs()).isEqualTo(10);
        assertThat(history.get(89).latencyMs()).isEqualTo(99);
    }
}