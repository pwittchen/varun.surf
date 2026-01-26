package com.github.pwittchen.varun.service;

import com.github.pwittchen.varun.service.metrics.MetricsHistoryService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MetricsHistoryServiceTest {

    private MetricsHistoryService service;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Search search;

    @BeforeEach
    void setUp() {
        lenient().when(meterRegistry.find(anyString())).thenReturn(search);
        lenient().when(search.tag(anyString(), anyString())).thenReturn(search);
        lenient().when(search.gauges()).thenReturn(Collections.emptyList());

        service = new MetricsHistoryService(meterRegistry);
    }

    @Test
    void shouldReturnEmptyHistoryInitially() {
        List<Map<String, Object>> history = service.getHistory();

        assertThat(history).isNotNull();
        assertThat(history).isEmpty();
    }

    @Test
    void shouldCollectMetricsSnapshot() {
        // Setup mocks for CPU
        Gauge cpuGauge = mock(Gauge.class);
        when(cpuGauge.value()).thenReturn(0.05);
        Search cpuSearch = mock(Search.class);
        when(meterRegistry.find("process.cpu.usage")).thenReturn(cpuSearch);
        when(cpuSearch.gauges()).thenReturn(List.of(cpuGauge));

        // System CPU
        Gauge systemCpuGauge = mock(Gauge.class);
        when(systemCpuGauge.value()).thenReturn(0.10);
        Search systemCpuSearch = mock(Search.class);
        when(meterRegistry.find("system.cpu.usage")).thenReturn(systemCpuSearch);
        when(systemCpuSearch.gauges()).thenReturn(List.of(systemCpuGauge));

        // Heap memory
        Gauge heapUsedGauge = mock(Gauge.class);
        when(heapUsedGauge.value()).thenReturn(100000000.0);
        Search heapUsedSearch = mock(Search.class);
        when(meterRegistry.find("jvm.memory.used")).thenReturn(heapUsedSearch);
        when(heapUsedSearch.tag("area", "heap")).thenReturn(heapUsedSearch);
        when(heapUsedSearch.gauges()).thenReturn(List.of(heapUsedGauge));

        Gauge heapMaxGauge = mock(Gauge.class);
        when(heapMaxGauge.value()).thenReturn(500000000.0);
        Search heapMaxSearch = mock(Search.class);
        when(meterRegistry.find("jvm.memory.max")).thenReturn(heapMaxSearch);
        when(heapMaxSearch.tag("area", "heap")).thenReturn(heapMaxSearch);
        when(heapMaxSearch.gauges()).thenReturn(List.of(heapMaxGauge));

        // Threads
        Gauge threadsLiveGauge = mock(Gauge.class);
        when(threadsLiveGauge.value()).thenReturn(50.0);
        Search threadsLiveSearch = mock(Search.class);
        when(meterRegistry.find("jvm.threads.live")).thenReturn(threadsLiveSearch);
        when(threadsLiveSearch.gauges()).thenReturn(List.of(threadsLiveGauge));

        Gauge threadsDaemonGauge = mock(Gauge.class);
        when(threadsDaemonGauge.value()).thenReturn(30.0);
        Search threadsDaemonSearch = mock(Search.class);
        when(meterRegistry.find("jvm.threads.daemon")).thenReturn(threadsDaemonSearch);
        when(threadsDaemonSearch.gauges()).thenReturn(List.of(threadsDaemonGauge));

        // Collect metrics
        service.collectMetrics();

        // Verify
        List<Map<String, Object>> history = service.getHistory();
        assertThat(history).hasSize(1);

        Map<String, Object> snapshot = history.get(0);
        assertThat(snapshot).containsKey("timestamp");
        assertThat(snapshot.get("cpuProcess")).isEqualTo(5.0); // 0.05 * 100
        assertThat(snapshot.get("cpuSystem")).isEqualTo(10.0); // 0.10 * 100
        assertThat(snapshot.get("heapUsed")).isEqualTo(100000000.0);
        assertThat(snapshot.get("heapMax")).isEqualTo(500000000.0);
        assertThat(snapshot.get("threadsLive")).isEqualTo(50);
        assertThat(snapshot.get("threadsDaemon")).isEqualTo(30);
    }

    @Test
    void shouldLimitHistoryToMaxPoints() {
        // Setup empty mocks (values will be 0)
        service = new MetricsHistoryService(meterRegistry);

        // Collect more than MAX_HISTORY_POINTS (60)
        for (int i = 0; i < 70; i++) {
            service.collectMetrics();
        }

        List<Map<String, Object>> history = service.getHistory();
        assertThat(history).hasSize(60); // MAX_HISTORY_POINTS
    }
}
