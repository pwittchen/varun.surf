package com.github.pwittchen.varun.controller;

import com.github.pwittchen.varun.service.AggregatorService;
import com.github.pwittchen.varun.service.health.HealthHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class StatusControllerTest {

    private StatusController controller;

    @Mock
    private AggregatorService aggregatorService;

    @Mock
    private HealthHistoryService healthHistoryService;

    @BeforeEach
    void setUp() {
        controller = new StatusController(aggregatorService, healthHistoryService);
        ReflectionTestUtils.setField(controller, "version", "test-version");
    }

    @Test
    void shouldReturnHealthStatusUp() {
        Mono<Map<String, String>> result = controller.health();

        StepVerifier.create(result)
                .assertNext(health -> {
                    assertThat(health).isNotNull();
                    assertThat(health).containsEntry("status", "UP");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnStatusWithSpotsCount() {
        when(aggregatorService.countSpots()).thenReturn(74);
        when(aggregatorService.countCountries()).thenReturn(15);
        when(aggregatorService.countLiveStations()).thenReturn(5);

        Mono<Map<String, Object>> result = controller.status();

        StepVerifier.create(result)
                .assertNext(status -> {
                    assertThat(status).isNotNull();
                    assertThat(status).containsEntry("status", "UP");
                    assertThat(status).containsEntry("spotsCount", 74);
                    assertThat(status).containsEntry("countriesCount", 15);
                    assertThat(status).containsEntry("liveStations", 5);
                    assertThat(status).containsKey("version");
                    assertThat(status).containsKey("uptime");
                    assertThat(status).containsKey("uptimeSeconds");
                    assertThat(status).containsKey("startTime");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnStatusWithZeroLiveStations() {
        when(aggregatorService.countSpots()).thenReturn(10);
        when(aggregatorService.countCountries()).thenReturn(2);
        when(aggregatorService.countLiveStations()).thenReturn(0);

        Mono<Map<String, Object>> result = controller.status();

        StepVerifier.create(result)
                .assertNext(status -> {
                    assertThat(status).isNotNull();
                    assertThat(status).containsEntry("status", "UP");
                    assertThat(status).containsEntry("liveStations", 0);
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnHealthHistory() {
        when(healthHistoryService.getHistory()).thenReturn(List.of());
        when(healthHistoryService.getSummary()).thenReturn(
                new HealthHistoryService.HealthHistorySummary(0, 0, 100.0, 0, System.currentTimeMillis())
        );
        when(healthHistoryService.isCurrentlyHealthy()).thenReturn(true);

        Mono<Map<String, Object>> result = controller.healthHistory();

        StepVerifier.create(result)
                .assertNext(history -> {
                    assertThat(history).isNotNull();
                    assertThat(history).containsKey("history");
                    assertThat(history).containsKey("summary");
                    assertThat(history).containsEntry("currentlyHealthy", true);
                })
                .verifyComplete();
    }
}
