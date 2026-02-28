package com.github.pwittchen.varun.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.google.common.truth.Truth.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "30s")
@TestPropertySource(properties = {"app.analytics.password="})
public class SecurityConfigNoPasswordTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldAllowAccessToMetricsWithoutPasswordConfigured() {
        String sessionCookie = getSessionCookie();

        webTestClient.get()
                .uri("/api/v1/metrics")
                .cookie("SESSION", sessionCookie)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldAllowAccessToMetricsHistoryWithoutPasswordConfigured() {
        String sessionCookie = getSessionCookie();

        webTestClient.get()
                .uri("/api/v1/metrics/history")
                .cookie("SESSION", sessionCookie)
                .exchange()
                .expectStatus().isOk();
    }

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
    void shouldRejectApiAccessWithoutSession() {
        webTestClient.get()
                .uri("/api/v1/spots")
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
