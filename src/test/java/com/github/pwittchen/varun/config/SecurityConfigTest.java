package com.github.pwittchen.varun.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Base64;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@TestPropertySource(properties = {"app.metrics.password=testpassword123"})
public class SecurityConfigTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldAllowAccessToPublicEndpoints() {
        webTestClient.get()
                .uri("/api/v1/spots")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldAllowAccessToStatusEndpoint() {
        webTestClient.get()
                .uri("/api/v1/status")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldAllowAccessToHealthEndpoint() {
        webTestClient.get()
                .uri("/api/v1/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldRequireAuthenticationForMetricsEndpoint() {
        webTestClient.get()
                .uri("/api/v1/metrics")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldRequireAuthenticationForMetricsHistoryEndpoint() {
        webTestClient.get()
                .uri("/api/v1/metrics/history")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldAllowAccessToMetricsWithValidCredentials() {
        String credentials = Base64.getEncoder().encodeToString("metrics:testpassword123".getBytes());

        webTestClient.get()
                .uri("/api/v1/metrics")
                .header("Authorization", "Basic " + credentials)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldAllowAccessToMetricsHistoryWithValidCredentials() {
        String credentials = Base64.getEncoder().encodeToString("metrics:testpassword123".getBytes());

        webTestClient.get()
                .uri("/api/v1/metrics/history")
                .header("Authorization", "Basic " + credentials)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldRejectMetricsAccessWithInvalidCredentials() {
        String credentials = Base64.getEncoder().encodeToString("metrics:wrongpassword".getBytes());

        webTestClient.get()
                .uri("/api/v1/metrics")
                .header("Authorization", "Basic " + credentials)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldRejectMetricsAccessWithInvalidUsername() {
        String credentials = Base64.getEncoder().encodeToString("admin:testpassword123".getBytes());

        webTestClient.get()
                .uri("/api/v1/metrics")
                .header("Authorization", "Basic " + credentials)
                .exchange()
                .expectStatus().isUnauthorized();
    }
}