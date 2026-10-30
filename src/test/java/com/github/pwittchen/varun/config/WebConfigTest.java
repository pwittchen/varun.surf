package com.github.pwittchen.varun.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;

class WebConfigTest {

    private final WebTestClient client = WebTestClient
            .bindToRouterFunction(new WebConfig().androidRouter())
            .build();

    @Test
    void shouldRedirectAndroidLinkToGooglePlay() {
        client.get().uri("/android")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.TEMPORARY_REDIRECT)
                .expectHeader().valueEquals("Location",
                        "https://play.google.com/store/apps/details?id=surf.varun.app");
    }

    @Test
    void shouldRedirectHeadRequestsToo() {
        client.head().uri("/android")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.TEMPORARY_REDIRECT);
    }
}
