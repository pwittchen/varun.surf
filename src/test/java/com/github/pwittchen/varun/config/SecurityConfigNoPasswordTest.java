package com.github.pwittchen.varun.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestPropertySource(properties = {"app.analytics.password="})
public class SecurityConfigNoPasswordTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldAllowAccessToMetricsWithoutPasswordConfigured() {
        webTestClient.get()
                .uri("/api/v1/metrics")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldAllowAccessToMetricsHistoryWithoutPasswordConfigured() {
        webTestClient.get()
                .uri("/api/v1/metrics/history")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldAllowAccessToPublicEndpoints() {
        webTestClient.get()
                .uri("/api/v1/spots")
                .exchange()
                .expectStatus().isOk();
    }
}