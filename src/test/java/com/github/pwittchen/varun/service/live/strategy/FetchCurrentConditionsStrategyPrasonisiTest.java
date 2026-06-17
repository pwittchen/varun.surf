package com.github.pwittchen.varun.service.live.strategy;

import com.github.pwittchen.varun.model.live.CurrentConditions;
import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

class FetchCurrentConditionsStrategyPrasonisiTest {

    private MockWebServer mockWebServer;
    private FetchCurrentConditionsStrategyPrasonisi strategy;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        strategy = new FetchCurrentConditionsStrategyPrasonisi(new OkHttpClient(), new Gson());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void shouldReturnTrueForPrasonisiWgId() {
        assertThat(strategy.canProcess(805332)).isTrue();
    }

    @Test
    void shouldReturnFalseForOtherWgIds() {
        assertThat(strategy.canProcess(48009)).isFalse();
        assertThat(strategy.canProcess(999999)).isFalse();
        assertThat(strategy.canProcess(726)).isFalse();
    }

    @Test
    void shouldNotBeFallbackStation() {
        assertThat(strategy.isFallbackStation()).isFalse();
    }

    @Test
    void shouldReturnCorrectUrl() {
        assertThat(strategy.getUrl(805332)).isEqualTo("https://www.prasonisi.com/SimpleAjax.php");
    }

    @Test
    void shouldParseValidJsonResponse() {
        String mockResponse =
                "{\"d\":\"307\",\"d_text\":\"NW\",\"v_kn\":\"16,0\",\"v_ms\":\"8,2\"," +
                        "\"vavg_kn\":\"13,6\",\"vavg_ms\":\"7,0\",\"max_kn\":\"22,4\",\"max_ms\":\"11,5\"}";

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/SimpleAjax.php").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions).isNotNull();
                    assertThat(conditions.wind()).isEqualTo(16);
                    assertThat(conditions.gusts()).isEqualTo(22);
                    assertThat(conditions.direction()).isEqualTo("NW");
                    assertThat(conditions.temp()).isEqualTo(0);
                    assertThat(conditions.date()).isNotEmpty();
                })
                .verifyComplete();
    }

    @Test
    void shouldSendPostRequestWithWindMonitorType() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"d\":\"90\",\"d_text\":\"E\",\"v_kn\":\"10,0\",\"max_kn\":\"15,0\"}")
                .setResponseCode(200));

        String url = mockWebServer.url("/SimpleAjax.php").toString();
        strategy.fetchCurrentConditions(url).block();

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("POST");
        assertThat(recordedRequest.getBody().readUtf8()).isEqualTo("type=windmonitor");
    }

    @Test
    void shouldRoundDecimalKnotValues() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"d\":\"180\",\"d_text\":\"S\",\"v_kn\":\"12,6\",\"max_kn\":\"18,4\"}")
                .setResponseCode(200));

        String url = mockWebServer.url("/SimpleAjax.php").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.wind()).isEqualTo(13);
                    assertThat(conditions.gusts()).isEqualTo(18);
                    assertThat(conditions.direction()).isEqualTo("S");
                })
                .verifyComplete();
    }

    @Test
    void shouldDeriveCardinalDirectionFromDegrees() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"d\":\"0\",\"d_text\":\"N\",\"v_kn\":\"10,0\",\"max_kn\":\"15,0\"}")
                .setResponseCode(200));

        String url = mockWebServer.url("/SimpleAjax.php").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> assertThat(conditions.direction()).isEqualTo("N"))
                .verifyComplete();
    }

    @Test
    void shouldHandleHttpError() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500));

        String url = mockWebServer.url("/SimpleAjax.php").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleEmptyResponseBody() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(""));

        String url = mockWebServer.url("/SimpleAjax.php").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleMalformedJson() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{invalid json")
                .setResponseCode(200));

        String url = mockWebServer.url("/SimpleAjax.php").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .expectError()
                .verify();
    }
}
