package com.github.pwittchen.varun.service.live.strategy;

import com.github.pwittchen.varun.service.live.CurrentConditionsService;
import com.github.pwittchen.varun.service.live.FetchCurrentConditions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;

/**
 * Integration test to verify that both primary and fallback Podersdorf strategies
 * are properly autowired by Spring and available to CurrentConditionsService.
 */
@SpringBootTest
class PodersdorfFallbackIntegrationTest {

    @Autowired
    private CurrentConditionsService currentConditionsService;

    @Autowired
    private List<FetchCurrentConditions> strategies;

    @Test
    void shouldHaveBothPodersdorfStrategies() {
        // Verify that we have at least 2 strategies (could be more for other spots)
        assertThat(strategies).isNotEmpty();

        // Find Podersdorf strategies (wgId 859182)
        List<FetchCurrentConditions> podersdorfStrategies = strategies.stream()
                .filter(s -> s.canProcess(859182))
                .toList();

        // Should have exactly 2 strategies: primary and fallback
        assertThat(podersdorfStrategies).hasSize(2);

        // Verify we have one primary and one fallback
        long primaryCount = podersdorfStrategies.stream()
                .filter(s -> !s.isFallbackStation())
                .count();
        long fallbackCount = podersdorfStrategies.stream()
                .filter(FetchCurrentConditions::isFallbackStation)
                .count();

        assertThat(primaryCount).isEqualTo(1);
        assertThat(fallbackCount).isEqualTo(1);
    }

    @Test
    void shouldHavePrimaryPodersdorfStrategy() {
        FetchCurrentConditions primaryStrategy = strategies.stream()
                .filter(s -> s.canProcess(859182))
                .filter(s -> !s.isFallbackStation())
                .findFirst()
                .orElse(null);

        assertThat(primaryStrategy).isNotNull();
        assertThat(primaryStrategy).isInstanceOf(FetchCurrentConditionsStrategyPodersdorf.class);
    }

    @Test
    void shouldHaveFallbackPodersdorfScpodoStrategy() {
        FetchCurrentConditions fallbackStrategy = strategies.stream()
                .filter(s -> s.canProcess(859182))
                .filter(FetchCurrentConditions::isFallbackStation)
                .findFirst()
                .orElse(null);

        assertThat(fallbackStrategy).isNotNull();
        assertThat(fallbackStrategy).isInstanceOf(FetchCurrentConditionsStrategyPodersdorfScpodo.class);
    }

    @Test
    void shouldHaveCurrentConditionsServiceConfigured() {
        assertThat(currentConditionsService).isNotNull();
    }
}
