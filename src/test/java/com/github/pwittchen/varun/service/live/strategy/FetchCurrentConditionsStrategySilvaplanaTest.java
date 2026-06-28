package com.github.pwittchen.varun.service.live.strategy;

import com.github.pwittchen.varun.model.live.CurrentConditions;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

class FetchCurrentConditionsStrategySilvaplanaTest {

    private static final String MOCK_HTML = """
            <div class="lmw-weather-today">
              <div class="lmw-width-3-5">
                <div class="lmw-weather-today-name">Sonntag, 28.6.2026 (09:50:00)</div>
                <div class="lmw-weather-today-temp">16.6 °C<br>&nbsp;</div>
              </div>
              <div class="lmw-width-2-5 lmw-text-center">
                <div class="lmw-width-1-1">
                  <div class="lmw-weather-today-desc">Windspitzen</div>
                  <div class="lmw-weather-today-wind">
                    5 km/h <br><small>(2.5 kn)</small>
                  </div>
                </div>
              </div>
            </div>
            <div class="lmw-weather-details lmw-grid lmw-grid-collapse">
              <div class="lmw-width-1-4">
                <div class="lmw-weather-today-pressure">Luftdruck: 1023.1 hPa</div>
              </div>
              <div class="lmw-width-1-4">
                <div class="lmw-weather-today-wind">Windrichtung: SO (135°)</div>
              </div>
              <div class="lmw-width-1-4">
                <div class="lmw-weather-today-wind">Mittelwind: 3 km/h (1 Bft)</div>
              </div>
            </div>
            """;

    private MockWebServer mockWebServer;
    private FetchCurrentConditionsStrategySilvaplana strategy;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        strategy = new FetchCurrentConditionsStrategySilvaplana(new OkHttpClient());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void shouldReturnTrueForSilvaplanaWgId() {
        assertThat(strategy.canProcess(1584)).isTrue();
    }

    @Test
    void shouldReturnFalseForOtherWgIds() {
        assertThat(strategy.canProcess(48009)).isFalse();
        assertThat(strategy.canProcess(805332)).isFalse();
        assertThat(strategy.canProcess(726)).isFalse();
    }

    @Test
    void shouldNotBeFallbackStation() {
        assertThat(strategy.isFallbackStation()).isFalse();
    }

    @Test
    void shouldReturnCorrectUrl() {
        assertThat(strategy.getUrl(1584)).isEqualTo("https://www.kitesailing.ch/spot/webcam");
    }

    @Test
    void shouldParseLiveWeatherWidget() {
        mockWebServer.enqueue(new MockResponse().setBody(MOCK_HTML).setResponseCode(200));

        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(
                mockWebServer.url("/spot/webcam").toString());

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions).isNotNull();
                    // Mittelwind 3 km/h -> 2 kn, Windspitzen 5 km/h -> 3 kn
                    assertThat(conditions.wind()).isEqualTo(2);
                    assertThat(conditions.gusts()).isEqualTo(3);
                    assertThat(conditions.direction()).isEqualTo("SE");
                    assertThat(conditions.temp()).isEqualTo(17);
                    assertThat(conditions.date()).isEqualTo("2026-06-28 09:50:00");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleHttpError() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(
                mockWebServer.url("/spot/webcam").toString());

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleResponseWithoutWindData() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("<html><body>no weather widget here</body></html>")
                .setResponseCode(200));

        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(
                mockWebServer.url("/spot/webcam").toString());

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }
}
