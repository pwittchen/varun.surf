package com.github.pwittchen.varun.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.google.common.truth.Truth.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "120s")
public class SessionAuthenticationFilterTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldRejectApiAccessWithoutSession() {
        webTestClient.get()
                .uri("/api/v1/spots")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldRejectStatusAccessWithoutSession() {
        webTestClient.get()
                .uri("/api/v1/status")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldAllowHealthWithoutSession() {
        webTestClient.get()
                .uri("/api/v1/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldAllowActuatorWithoutSession() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldAllowLlmsIndexWithoutSession() {
        webTestClient.get()
                .uri("/llms/spots.md")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldAllowLlmsCountriesWithoutSession() {
        webTestClient.get()
                .uri("/llms/countries.md")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldReturn404ForUnknownSpotIdInLlmsWithoutSession() {
        webTestClient.get()
                .uri("/llms/spots/1.md")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void shouldReturn404ForUnknownCountrySlugInLlmsWithoutSession() {
        webTestClient.get()
                .uri("/llms/countries/atlantis.md")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void shouldAllowApiAccessWithValidSession() {
        String sessionCookie = getSessionCookie();

        webTestClient.get()
                .uri("/api/v1/spots")
                .cookie("SESSION", sessionCookie)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldAllowStatusAccessWithValidSession() {
        String sessionCookie = getSessionCookie();

        webTestClient.get()
                .uri("/api/v1/status")
                .cookie("SESSION", sessionCookie)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldRejectApiAccessWithTamperedCookie() {
        String sessionCookie = getSessionCookie();
        String signature = sessionCookie.substring(sessionCookie.indexOf('.') + 1);

        webTestClient.get()
                .uri("/api/v1/spots")
                .cookie("SESSION", "9999999999." + signature)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldRejectApiAccessWithGarbageCookie() {
        webTestClient.get()
                .uri("/api/v1/spots")
                .cookie("SESSION", "definitely-not-a-token")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void shouldIssueHttpOnlyCookieScopedToTheWholeSite() {
        var result = webTestClient.get()
                .uri("/")
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class);

        ResponseCookie cookie = result.getResponseCookies().getFirst("SESSION");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        // plain http in tests, so the cookie must not be marked Secure or the
        // browser would drop it
        assertThat(cookie.isSecure()).isFalse();
    }

    @Test
    void shouldNotReissueCookieForVisitorAlreadyHoldingAFreshOne() {
        String sessionCookie = getSessionCookie();

        var result = webTestClient.get()
                .uri("/")
                .cookie("SESSION", sessionCookie)
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class);

        assertThat(result.getResponseCookies().getFirst("SESSION")).isNull();
    }

    @Test
    void shouldInitializeSessionOnPageVisit() {
        var result = webTestClient.get()
                .uri("/")
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
