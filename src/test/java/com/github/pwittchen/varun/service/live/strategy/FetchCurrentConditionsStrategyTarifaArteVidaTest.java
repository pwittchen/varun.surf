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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static com.google.common.truth.Truth.assertThat;

class FetchCurrentConditionsStrategyTarifaArteVidaTest {

    private MockWebServer mockWebServer;
    private FetchCurrentConditionsStrategyTarifaArteVida strategy;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        strategy = new FetchCurrentConditionsStrategyTarifaArteVida(new OkHttpClient());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void shouldReturnTrueForTarifaArteVidaWgId() {
        assertThat(strategy.canProcess(48775)).isTrue();
    }

    @Test
    void shouldReturnFalseForOtherWgIds() {
        assertThat(strategy.canProcess(207008)).isFalse();
        assertThat(strategy.canProcess(999999)).isFalse();
    }

    @Test
    void shouldParseValidSpotfavResponse() {
        String mockResponse = """
                <html>
                <body>
                <div id='current-wind-data'>
                <div class='wind-direction'>
                <div class='wind-direction-icon'>
                <i class='fa fa-long-arrow-alt-down' id='wind-direction'></i>
                </div>
                <div id='report_wind_direction_deg'>
                e
                </div>
                <div id='report_wind_direction'>
                85 º
                </div>
                </div>
                <div class='wind-speed'>
                13 knots
                </div>
                <div class='wind-speed-gust-helper-text'>
                speed
                </div>
                <div class='wind-gust'>
                25 knots
                </div>
                <div class='wind-speed-gust-helper-text'>
                gusts
                </div>
                <div class='temperature'>
                15 º C
                </div>
                </div>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/update/").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        String expectedDatePrefix = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions).isNotNull();
                    assertThat(conditions.date()).startsWith(expectedDatePrefix);
                    assertThat(conditions.direction()).isEqualTo("E");
                    assertThat(conditions.wind()).isEqualTo(13);
                    assertThat(conditions.gusts()).isEqualTo(25);
                    assertThat(conditions.temp()).isEqualTo(15);
                })
                .verifyComplete();
    }

    @Test
    void shouldParseNorthWindDirection() {
        String mockResponse = """
                <html>
                <body>
                <div id='current-wind-data'>
                <div id='report_wind_direction'>
                5 º
                </div>
                <div class='wind-speed'>
                18 knots
                </div>
                <div class='wind-gust'>
                28 knots
                </div>
                <div class='temperature'>
                20 º C
                </div>
                </div>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/update/").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("N");
                    assertThat(conditions.wind()).isEqualTo(18);
                    assertThat(conditions.gusts()).isEqualTo(28);
                })
                .verifyComplete();
    }

    @Test
    void shouldParseSouthWestWindDirection() {
        String mockResponse = """
                <html>
                <body>
                <div id='current-wind-data'>
                <div id='report_wind_direction'>
                225 º
                </div>
                <div class='wind-speed'>
                22 knots
                </div>
                <div class='wind-gust'>
                30 knots
                </div>
                <div class='temperature'>
                18 º C
                </div>
                </div>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/update/").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("SW");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleStrongLevante() {
        String mockResponse = """
                <html>
                <body>
                <div id='current-wind-data'>
                <div id='report_wind_direction'>
                90 º
                </div>
                <div class='wind-speed'>
                35 knots
                </div>
                <div class='wind-gust'>
                45 knots
                </div>
                <div class='temperature'>
                25 º C
                </div>
                </div>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/update/").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("E");
                    assertThat(conditions.wind()).isEqualTo(35);
                    assertThat(conditions.gusts()).isEqualTo(45);
                    assertThat(conditions.temp()).isEqualTo(25);
                })
                .verifyComplete();
    }

    @Test
    void shouldHandlePonienteWind() {
        String mockResponse = """
                <html>
                <body>
                <div id='current-wind-data'>
                <div id='report_wind_direction'>
                270 º
                </div>
                <div class='wind-speed'>
                20 knots
                </div>
                <div class='wind-gust'>
                28 knots
                </div>
                <div class='temperature'>
                22 º C
                </div>
                </div>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/update/").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("W");
                    assertThat(conditions.wind()).isEqualTo(20);
                    assertThat(conditions.gusts()).isEqualTo(28);
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleHttpError() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500));

        String url = mockWebServer.url("/update/").toString();
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

        String url = mockWebServer.url("/update/").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleMissingWindSpeed() {
        String mockResponse = """
                <html>
                <body>
                <div id='current-wind-data'>
                <div id='report_wind_direction'>
                85 º
                </div>
                <div class='wind-gust'>
                25 knots
                </div>
                <div class='temperature'>
                15 º C
                </div>
                </div>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/update/").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleMissingWindDirection() {
        String mockResponse = """
                <html>
                <body>
                <div id='current-wind-data'>
                <div class='wind-speed'>
                13 knots
                </div>
                <div class='wind-gust'>
                25 knots
                </div>
                <div class='temperature'>
                15 º C
                </div>
                </div>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/update/").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleLightWind() {
        String mockResponse = """
                <html>
                <body>
                <div id='current-wind-data'>
                <div id='report_wind_direction'>
                180 º
                </div>
                <div class='wind-speed'>
                5 knots
                </div>
                <div class='wind-gust'>
                8 knots
                </div>
                <div class='temperature'>
                28 º C
                </div>
                </div>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/update/").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("S");
                    assertThat(conditions.wind()).isEqualTo(5);
                    assertThat(conditions.gusts()).isEqualTo(8);
                    assertThat(conditions.temp()).isEqualTo(28);
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnCorrectUrl() {
        assertThat(strategy.getUrl(48775)).isEqualTo("https://www.spotfav.com/public/meteo/weatherflow-4eee927b185476763900001b/update/");
    }

    @Test
    void shouldHandleNorthEastDirection() {
        String mockResponse = """
                <html>
                <body>
                <div id='current-wind-data'>
                <div id='report_wind_direction'>
                45 º
                </div>
                <div class='wind-speed'>
                15 knots
                </div>
                <div class='wind-gust'>
                22 knots
                </div>
                <div class='temperature'>
                19 º C
                </div>
                </div>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/update/").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("NE");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleNorthWestDirection() {
        String mockResponse = """
                <html>
                <body>
                <div id='current-wind-data'>
                <div id='report_wind_direction'>
                315 º
                </div>
                <div class='wind-speed'>
                12 knots
                </div>
                <div class='wind-gust'>
                18 knots
                </div>
                <div class='temperature'>
                17 º C
                </div>
                </div>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/update/").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("NW");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleSouthEastDirection() {
        String mockResponse = """
                <html>
                <body>
                <div id='current-wind-data'>
                <div id='report_wind_direction'>
                135 º
                </div>
                <div class='wind-speed'>
                16 knots
                </div>
                <div class='wind-gust'>
                24 knots
                </div>
                <div class='temperature'>
                21 º C
                </div>
                </div>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/update/").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("SE");
                })
                .verifyComplete();
    }
}
