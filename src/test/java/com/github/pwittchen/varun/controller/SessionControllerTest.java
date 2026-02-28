package com.github.pwittchen.varun.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.google.common.truth.Truth.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "30s")
public class SessionControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldReturnOkStatus() {
        webTestClient.get()
                .uri("/api/v1/session")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("OK");
    }

    @Test
    void shouldSetSessionCookie() {
        var result = webTestClient.get()
                .uri("/api/v1/session")
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class);

        ResponseCookie cookie = result.getResponseCookies().getFirst("SESSION");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isNotEmpty();
    }

    @Test
    void shouldAllowApiCallsWithSessionCookie() {
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
}
