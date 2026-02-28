package com.github.pwittchen.varun.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Base64;

import static com.google.common.truth.Truth.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "30s")
@TestPropertySource(properties = {"app.analytics.password=testpassword123"})
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
    void shouldRequireAuthenticationForMetricsEndpoint() {
        String sessionCookie = getSessionCookie();

        webTestClient.get()
                .uri("/api/v1/metrics")
                .cookie("SESSION", sessionCookie)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldRequireAuthenticationForMetricsHistoryEndpoint() {
        String sessionCookie = getSessionCookie();

        webTestClient.get()
                .uri("/api/v1/metrics/history")
                .cookie("SESSION", sessionCookie)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldAllowAccessToMetricsWithValidCredentials() {
        String sessionCookie = getSessionCookie();
        String credentials = Base64.getEncoder().encodeToString("admin:testpassword123".getBytes());

        webTestClient.get()
                .uri("/api/v1/metrics")
                .cookie("SESSION", sessionCookie)
                .header("Authorization", "Basic " + credentials)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldAllowAccessToMetricsHistoryWithValidCredentials() {
        String sessionCookie = getSessionCookie();
        String credentials = Base64.getEncoder().encodeToString("admin:testpassword123".getBytes());

        webTestClient.get()
                .uri("/api/v1/metrics/history")
                .cookie("SESSION", sessionCookie)
                .header("Authorization", "Basic " + credentials)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldRejectMetricsAccessWithInvalidCredentials() {
        String sessionCookie = getSessionCookie();
        String credentials = Base64.getEncoder().encodeToString("metrics:wrongpassword".getBytes());

        webTestClient.get()
                .uri("/api/v1/metrics")
                .cookie("SESSION", sessionCookie)
                .header("Authorization", "Basic " + credentials)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldRejectMetricsAccessWithInvalidUsername() {
        String sessionCookie = getSessionCookie();
        String credentials = Base64.getEncoder().encodeToString("wronguser:testpassword123".getBytes());

        webTestClient.get()
                .uri("/api/v1/metrics")
                .cookie("SESSION", sessionCookie)
                .header("Authorization", "Basic " + credentials)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldRejectApiAccessWithoutSession() {
        webTestClient.get()
                .uri("/api/v1/spots")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldInitializeSessionViaEndpoint() {
        var result = webTestClient.get()
                .uri("/api/v1/session")
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class);

        ResponseCookie cookie = result.getResponseCookies().getFirst("SESSION");
        assertThat(cookie).isNotNull();

        webTestClient.get()
                .uri("/api/v1/spots")
                .cookie("SESSION", cookie.getValue())
                .exchange()
                .expectStatus().isOk();
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
                .uri("/api/v1/session")
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class);

        ResponseCookie cookie = result.getResponseCookies().getFirst("SESSION");
        assertThat(cookie).isNotNull();
        return cookie.getValue();
    }
}
