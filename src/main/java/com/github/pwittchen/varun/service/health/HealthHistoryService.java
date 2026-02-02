package com.github.pwittchen.varun.service.health;

import com.google.common.collect.EvictingQueue;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class HealthHistoryService {

    private static final int MAX_HISTORY_POINTS = 90;

    private final EvictingQueue<HealthCheckResult> history;
    private volatile boolean lastHealthStatus = true;

    public HealthHistoryService() {
        this.history = EvictingQueue.create(MAX_HISTORY_POINTS);
    }

    @PostConstruct
    public void init() {
        recordHealthCheck(true, 0);
    }

    @Scheduled(fixedRate = 60000)
    public void performHealthCheck() {
        long startTime = System.currentTimeMillis();
        boolean healthy = checkHealth();
        long latency = System.currentTimeMillis() - startTime;
        recordHealthCheck(healthy, latency);
    }

    private boolean checkHealth() {
        try {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void recordHealthCheck(boolean healthy, long latencyMs) {
        lastHealthStatus = healthy;
        synchronized (history) {
            history.add(new HealthCheckResult(
                    System.currentTimeMillis(),
                    healthy,
                    latencyMs
            ));
        }
    }

    public void recordFailure() {
        recordHealthCheck(false, 0);
    }

    public List<HealthCheckResult> getHistory() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    public HealthHistorySummary getSummary() {
        List<HealthCheckResult> results = getHistory();
        if (results.isEmpty()) {
            return new HealthHistorySummary(0, 0, 100.0, 0, 0);
        }

        long totalChecks = results.size();
        long successfulChecks = results.stream().filter(HealthCheckResult::healthy).count();
        double uptimePercentage = (successfulChecks * 100.0) / totalChecks;
        long avgLatency = (long) results.stream()
                .filter(HealthCheckResult::healthy)
                .mapToLong(HealthCheckResult::latencyMs)
                .average()
                .orElse(0);

        long oldestTimestamp = results.stream()
                .mapToLong(HealthCheckResult::timestamp)
                .min()
                .orElse(System.currentTimeMillis());

        return new HealthHistorySummary(
                totalChecks,
                successfulChecks,
                Math.round(uptimePercentage * 100.0) / 100.0,
                avgLatency,
                oldestTimestamp
        );
    }

    public boolean isCurrentlyHealthy() {
        return lastHealthStatus;
    }

    public record HealthHistorySummary(
            long totalChecks,
            long successfulChecks,
            double uptimePercentage,
            long avgLatencyMs,
            long oldestCheckTimestamp
    ) {}
}