package com.github.pwittchen.varun.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.google.common.truth.Truth.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "30s")
public class SecurityConfigTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldAllowAccessToPublicEndpoints() {
        String sessionCookie = getSessionCookie();

        webTestClient.get()
                .uri("/api/v1/spots")
                .cookie("SESSION", sessionCookie)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldAllowAccessToStatusEndpoint() {
        String sessionCookie = getSessionCookie();

        webTestClient.get()
                .uri("/api/v1/status")
                .cookie("SESSION", sessionCookie)
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
    void shouldAllowAccessToMetricsEndpointWithoutPassword() {
        String sessionCookie = getSessionCookie();

        webTestClient.get()
                .uri("/api/v1/metrics")
                .cookie("SESSION", sessionCookie)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldAllowAccessToMetricsHistoryEndpointWithoutPassword() {
        String sessionCookie = getSessionCookie();

        webTestClient.get()
                .uri("/api/v1/metrics/history")
                .cookie("SESSION", sessionCookie)
                .exchange()
                .expectStatus().isOk();
    }

    // The logs dashboard is gone: there is no endpoint left to authenticate against,
    // with or without a password.
    @Test
    void shouldNotServeLogs() {
        String sessionCookie = getSessionCookie();

        webTestClient.get()
                .uri("/api/v1/logs")
                .cookie("SESSION", sessionCookie)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void shouldNotServeLogsPage() {
        webTestClient.get()
                .uri("/logs")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void shouldNotAskForBasicAuth() {
        webTestClient.get()
                .uri("/api/v1/spots")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().doesNotExist("WWW-Authenticate");
    }

    @Test
    void shouldRejectApiAccessWithoutSession() {
        webTestClient.get()
                .uri("/api/v1/spots")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldRejectMetricsWithoutSession() {
        webTestClient.get()
                .uri("/api/v1/metrics")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    private String getSessionCookie() {
        var result = webTestClient.get()
                .uri("/")
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class);

        ResponseCookie cookie = result.getResponseCookies().getFirst("SESSION");
        assertThat(cookie).isNotNull();
        return cookie.getValue();
    }
}
