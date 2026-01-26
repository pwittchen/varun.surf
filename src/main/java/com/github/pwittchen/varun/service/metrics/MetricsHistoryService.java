package com.github.pwittchen.varun.service.metrics;

import com.google.common.collect.EvictingQueue;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MetricsHistoryService {

    private static final int MAX_HISTORY_POINTS = 60; // 5 minutes at 5-second intervals

    private final MeterRegistry meterRegistry;
    private final EvictingQueue<MetricsSnapshot> history;

    public MetricsHistoryService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.history = EvictingQueue.create(MAX_HISTORY_POINTS);
    }

    @PostConstruct
    public void init() {
        // Collect initial data point
        collectMetrics();
    }

    @Scheduled(fixedRate = 5000)
    public void collectMetrics() {
        double cpuProcess = getGaugeValue("process.cpu.usage") * 100;
        double cpuSystem = getGaugeValue("system.cpu.usage") * 100;
        double heapUsed = getGaugeValue("jvm.memory.used", "area", "heap");
        double heapMax = getGaugeValue("jvm.memory.max", "area", "heap");
        int threadsLive = (int) getGaugeValue("jvm.threads.live");
        int threadsDaemon = (int) getGaugeValue("jvm.threads.daemon");
        long timestamp = System.currentTimeMillis();

        synchronized (history) {
            history.add(new MetricsSnapshot(
                    timestamp,
                    cpuProcess,
                    cpuSystem,
                    heapUsed,
                    heapMax,
                    threadsLive,
                    threadsDaemon
            ));
        }
    }

    public List<Map<String, Object>> getHistory() {
        List<Map<String, Object>> result = new ArrayList<>();
        synchronized (history) {
            for (MetricsSnapshot snapshot : history) {
                Map<String, Object> data = new HashMap<>();
                data.put("timestamp", snapshot.timestamp());
                data.put("cpuProcess", snapshot.cpuProcess());
                data.put("cpuSystem", snapshot.cpuSystem());
                data.put("heapUsed", snapshot.heapUsed());
                data.put("heapMax", snapshot.heapMax());
                data.put("threadsLive", snapshot.threadsLive());
                data.put("threadsDaemon", snapshot.threadsDaemon());
                result.add(data);
            }
        }
        return result;
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

    private record MetricsSnapshot(
            long timestamp,
            double cpuProcess,
            double cpuSystem,
            double heapUsed,
            double heapMax,
            int threadsLive,
            int threadsDaemon
    ) {}
}
