package com.github.pwittchen.varun.controller;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.Search;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
public class MetricsControllerTest {

    private MetricsController controller;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Search search;

    @BeforeEach
    void setUp() {
        controller = new MetricsController(meterRegistry);
        ReflectionTestUtils.setField(controller, "metricsPassword", "");

        // Setup default search behavior
        lenient().when(meterRegistry.find(anyString())).thenReturn(search);
        lenient().when(search.tag(anyString(), anyString())).thenReturn(search);
        lenient().when(search.gauges()).thenReturn(Collections.emptyList());
        lenient().when(search.counters()).thenReturn(Collections.emptyList());
        lenient().when(search.timers()).thenReturn(Collections.emptyList());
    }

    @Test
    void shouldReturnMetricsResponse() {
        Mono<Map<String, Object>> result = controller.metrics(null);

        StepVerifier.create(result)
                .assertNext(metrics -> {
                    assertThat(metrics).isNotNull();
                    assertThat(metrics).containsKey("gauges");
                    assertThat(metrics).containsKey("counters");
                    assertThat(metrics).containsKey("timers");
                    assertThat(metrics).containsKey("jvm");
                    assertThat(metrics).containsKey("httpClient");
                    assertThat(metrics).containsKey("timestamp");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnGaugesSection() {
        // Setup gauge mock
        Gauge spotsGauge = mock(Gauge.class);
        when(spotsGauge.value()).thenReturn(74.0);

        Search spotsSearch = mock(Search.class);
        when(meterRegistry.find("varun.spots.total")).thenReturn(spotsSearch);
        when(spotsSearch.gauges()).thenReturn(List.of(spotsGauge));

        Mono<Map<String, Object>> result = controller.metrics(null);

        StepVerifier.create(result)
                .assertNext(metrics -> {
                    assertThat(metrics).containsKey("gauges");
                    Map<String, Object> gauges = (Map<String, Object>) metrics.get("gauges");
                    assertThat(gauges).containsKey("spotsTotal");
                    assertThat(gauges.get("spotsTotal")).isEqualTo(74.0);
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnCountersSection() {
        // Setup counter mock
        Counter forecastCounter = mock(Counter.class);
        when(forecastCounter.count()).thenReturn(100.0);

        Search counterSearch = mock(Search.class);
        when(meterRegistry.find("varun.fetch.forecasts.total")).thenReturn(counterSearch);
        when(counterSearch.counters()).thenReturn(List.of(forecastCounter));

        Mono<Map<String, Object>> result = controller.metrics(null);

        StepVerifier.create(result)
                .assertNext(metrics -> {
                    assertThat(metrics).containsKey("counters");
                    Map<String, Object> counters = (Map<String, Object>) metrics.get("counters");
                    assertThat(counters).containsKey("forecastsTotal");
                    assertThat(counters.get("forecastsTotal")).isEqualTo(100.0);
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnTimersSection() {
        // Setup timer mock
        Timer forecastTimer = mock(Timer.class);
        when(forecastTimer.count()).thenReturn(50L);
        when(forecastTimer.totalTime(TimeUnit.MILLISECONDS)).thenReturn(5000.0);
        when(forecastTimer.max(TimeUnit.MILLISECONDS)).thenReturn(200.0);

        Search timerSearch = mock(Search.class);
        when(meterRegistry.find("varun.fetch.forecasts.duration")).thenReturn(timerSearch);
        when(timerSearch.timers()).thenReturn(List.of(forecastTimer));

        Mono<Map<String, Object>> result = controller.metrics(null);

        StepVerifier.create(result)
                .assertNext(metrics -> {
                    assertThat(metrics).containsKey("timers");
                    Map<String, Object> timers = (Map<String, Object>) metrics.get("timers");
                    assertThat(timers).containsKey("forecastsDuration");

                    Map<String, Object> forecastDuration = (Map<String, Object>) timers.get("forecastsDuration");
                    assertThat(forecastDuration.get("count")).isEqualTo(50L);
                    assertThat(forecastDuration.get("totalTimeMs")).isEqualTo(5000.0);
                    assertThat(forecastDuration.get("maxMs")).isEqualTo(200.0);
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnJvmSection() {
        Mono<Map<String, Object>> result = controller.metrics(null);

        StepVerifier.create(result)
                .assertNext(metrics -> {
                    assertThat(metrics).containsKey("jvm");
                    Map<String, Object> jvm = (Map<String, Object>) metrics.get("jvm");
                    assertThat(jvm).containsKey("heapUsed");
                    assertThat(jvm).containsKey("heapMax");
                    assertThat(jvm).containsKey("heapUsedPercent");
                    assertThat(jvm).containsKey("threadsLive");
                    assertThat(jvm).containsKey("cpuUsage");
                    assertThat(jvm).containsKey("uptimeSeconds");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnHttpClientSection() {
        Mono<Map<String, Object>> result = controller.metrics(null);

        StepVerifier.create(result)
                .assertNext(metrics -> {
                    assertThat(metrics).containsKey("httpClient");
                    Map<String, Object> http = (Map<String, Object>) metrics.get("httpClient");
                    assertThat(http).containsKey("activeRequests");
                    assertThat(http).containsKey("totalRequests");
                    assertThat(http).containsKey("successRequests");
                    assertThat(http).containsKey("failedRequests");
                    assertThat(http).containsKey("requestDuration");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnTimestampInIsoFormat() {
        Mono<Map<String, Object>> result = controller.metrics(null);

        StepVerifier.create(result)
                .assertNext(metrics -> {
                    assertThat(metrics).containsKey("timestamp");
                    String timestamp = (String) metrics.get("timestamp");
                    assertThat(timestamp).isNotNull();
                    assertThat(timestamp).matches("\\d{4}-\\d{2}-\\d{2}T.*");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleEmptyTimers() {
        Search emptyTimerSearch = mock(Search.class);
        when(meterRegistry.find("varun.fetch.forecasts.duration")).thenReturn(emptyTimerSearch);
        when(emptyTimerSearch.timers()).thenReturn(Collections.emptyList());

        Mono<Map<String, Object>> result = controller.metrics(null);

        StepVerifier.create(result)
                .assertNext(metrics -> {
                    Map<String, Object> timers = (Map<String, Object>) metrics.get("timers");
                    Map<String, Object> forecastDuration = (Map<String, Object>) timers.get("forecastsDuration");
                    assertThat(forecastDuration.get("count")).isEqualTo(0L);
                    assertThat(forecastDuration.get("meanMs")).isEqualTo(0.0);
                })
                .verifyComplete();
    }
}
