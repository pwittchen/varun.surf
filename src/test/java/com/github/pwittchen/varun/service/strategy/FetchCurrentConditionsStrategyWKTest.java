package com.github.pwittchen.varun.service.strategy;

import com.github.pwittchen.varun.model.CurrentConditions;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

class FetchCurrentConditionsStrategyWKTest {

    private MockWebServer mockWebServer;
    private FetchCurrentConditionsStrategyWK strategy;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        strategy = new FetchCurrentConditionsStrategyWK();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void shouldReturnTrueForSupportedWgIds() {
        assertThat(strategy.canProcess(126330)).isTrue();
        assertThat(strategy.canProcess(509469)).isTrue();
        assertThat(strategy.canProcess(500760)).isTrue();
        assertThat(strategy.canProcess(4165)).isTrue();
    }

    @Test
    void shouldReturnFalseForUnsupportedWgIds() {
        assertThat(strategy.canProcess(999999)).isFalse();
        assertThat(strategy.canProcess(123456)).isFalse();
    }

    @Test
    void shouldParseValidWiatrkadynyResponse() {
        String mockResponse = "2025-01-15 14:30 15.5 0 0 0 18.2 0 0 0 0 SW 0 0 0 0 0 0 0 0 0 0 0 0 0 0 12.3 0";
        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/wiatrkadyny.txt").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions).isNotNull();
                    assertThat(conditions.date()).isEqualTo("2025-01-15 14:30");
                    assertThat(conditions.temp()).isEqualTo(16);
                    assertThat(conditions.wind()).isEqualTo(12);
                    assertThat(conditions.direction()).isEqualTo("SW");
                    assertThat(conditions.gusts()).isEqualTo(18);
                })
                .verifyComplete();
    }

    @Test
    void shouldNormalizeWindDirection() {
        String mockResponse = "2025-01-15 14:30 15.5 0 0 0 18.2 0 0 0 0 NNE 0 0 0 0 0 0 0 0 0 0 0 0 0 0 12.3 0";
        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/wiatrkadyny.txt").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("NE");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleHttpError() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(404));

        String url = mockWebServer.url("/wiatrkadyny.txt").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleEmptyResponse() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("")
                .setResponseCode(200));

        String url = mockWebServer.url("/wiatrkadyny.txt").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .expectError()
                .verify();
    }

    @Test
    void shouldRoundDecimalValues() {
        String mockResponse = "2025-01-15 14:30 20.8 0 0 0 25.6 0 0 0 0 N 0 0 0 0 0 0 0 0 0 0 0 0 0 0 15.4 0";
        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/wiatrkadyny.txt").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.temp()).isEqualTo(21);
                    assertThat(conditions.wind()).isEqualTo(15);
                    assertThat(conditions.gusts()).isEqualTo(26);
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnCorrectUrlForWgId() {
        assertThat(strategy.getUrl(126330)).isEqualTo("https://www.wiatrkadyny.pl/wiatrkadyny.txt");
        assertThat(strategy.getUrl(509469)).isEqualTo("https://www.wiatrkadyny.pl/kuznica/wiatrkadyny.txt");
        assertThat(strategy.getUrl(500760)).isEqualTo("https://www.wiatrkadyny.pl/draga/wiatrkadyny.txt");
        assertThat(strategy.getUrl(4165)).isEqualTo("https://www.wiatrkadyny.pl/rewa/wiatrkadyny.txt");
    }
}