package com.github.pwittchen.varun.service.live.strategy;

import com.github.pwittchen.varun.model.live.CurrentConditions;
import com.google.gson.Gson;
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

class FetchCurrentConditionsStrategyTurawaWundergroundTest {

    private MockWebServer mockWebServer;
    private FetchCurrentConditionsStrategyTurawaWunderground strategy;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        strategy = new FetchCurrentConditionsStrategyTurawaWunderground(new OkHttpClient(), new Gson(), "test-api-key");
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void shouldReturnTrueForTurawaSouthWgId() {
        assertThat(strategy.canProcess(SpotsJson.wgIdOf("Turawa Południe"))).isTrue();
    }

    @Test
    void shouldReturnFalseForOtherWgIds() {
        // the northern shore is served by the airmax station
        assertThat(strategy.canProcess(SpotsJson.wgIdOf("Turawa Północ"))).isFalse();
        assertThat(strategy.canProcess(859182)).isFalse();
        assertThat(strategy.canProcess(999999)).isFalse();
    }

    @Test
    void shouldNotBeFallbackStation() {
        assertThat(strategy.isFallbackStation()).isFalse();
    }

    @Test
    void shouldReturnCorrectUrl() {
        assertThat(strategy.getUrl(SpotsJson.wgIdOf("Turawa Południe"))).isEqualTo(
                "https://api.weather.com/v2/pws/observations/current"
                        + "?stationId=ISZCZE187&format=json&units=m&apiKey=test-api-key");
    }

    @Test
    void shouldParseValidResponse() {
        mockWebServer.enqueue(new MockResponse()
                .setBody(createResponse(135, 18.5, 27.8, 23))
                .setResponseCode(200));

        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url());

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions).isNotNull();
                    assertThat(conditions.date()).isEqualTo("2026-07-25 20:48:06");
                    assertThat(conditions.direction()).isEqualTo("SE");
                    assertThat(conditions.wind()).isEqualTo(10); // 18.5 km/h = 9.99 kts
                    assertThat(conditions.gusts()).isEqualTo(15); // 27.8 km/h = 15.01 kts
                    assertThat(conditions.temp()).isEqualTo(23);
                })
                .verifyComplete();
    }

    @Test
    void shouldConvertDegreesToCardinalDirections() {
        int[] degrees = {0, 45, 90, 135, 180, 225, 270, 315, 359};
        String[] expected = {"N", "NE", "E", "SE", "S", "SW", "W", "NW", "N"};

        for (int i = 0; i < degrees.length; i++) {
            mockWebServer.enqueue(new MockResponse()
                    .setBody(createResponse(degrees[i], 10, 12, 15))
                    .setResponseCode(200));

            String expectedDirection = expected[i];
            StepVerifier.create(strategy.fetchCurrentConditions(url()))
                    .assertNext(conditions -> assertThat(conditions.direction()).isEqualTo(expectedDirection))
                    .verifyComplete();
        }
    }

    @Test
    void shouldParseNegativeTemperature() {
        mockWebServer.enqueue(new MockResponse()
                .setBody(createResponse(180, 10, 12, -5))
                .setResponseCode(200));

        StepVerifier.create(strategy.fetchCurrentConditions(url()))
                .assertNext(conditions -> assertThat(conditions.temp()).isEqualTo(-5))
                .verifyComplete();
    }

    @Test
    void shouldHandleHttpError() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        StepVerifier.create(strategy.fetchCurrentConditions(url()))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleEmptyBody() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(204));

        StepVerifier.create(strategy.fetchCurrentConditions(url()))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleEmptyObservations() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"observations\":[]}")
                .setResponseCode(200));

        StepVerifier.create(strategy.fetchCurrentConditions(url()))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleMissingMetricSection() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"observations\":[{\"obsTimeLocal\":\"2026-07-25 20:48:06\",\"winddir\":135}]}")
                .setResponseCode(200));

        StepVerifier.create(strategy.fetchCurrentConditions(url()))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleNullWindSpeed() {
        String body = """
                {"observations":[{
                  "obsTimeLocal":"2026-07-25 20:48:06",
                  "winddir":135,
                  "metric":{"temp":23,"windSpeed":null,"windGust":4}
                }]}
                """;

        mockWebServer.enqueue(new MockResponse().setBody(body).setResponseCode(200));

        StepVerifier.create(strategy.fetchCurrentConditions(url()))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleMissingTimestamp() {
        String body = """
                {"observations":[{
                  "winddir":135,
                  "metric":{"temp":23,"windSpeed":4,"windGust":4}
                }]}
                """;

        mockWebServer.enqueue(new MockResponse().setBody(body).setResponseCode(200));

        StepVerifier.create(strategy.fetchCurrentConditions(url()))
                .expectError(RuntimeException.class)
                .verify();
    }

    private String url() {
        return mockWebServer.url("/v2/pws/observations/current").toString();
    }

    private String createResponse(int windDir, double windSpeedKmh, double windGustKmh, int tempC) {
        return """
                {
                  "observations": [
                    {
                      "stationID": "ISZCZE187",
                      "obsTimeUtc": "2026-07-25T18:48:06Z",
                      "obsTimeLocal": "2026-07-25 20:48:06",
                      "neighborhood": "Szczedrzyk",
                      "country": "PL",
                      "lon": 18.149062,
                      "lat": 50.706118,
                      "winddir": %d,
                      "humidity": 42,
                      "qcStatus": 1,
                      "metric": {
                        "temp": %d,
                        "windSpeed": %s,
                        "windGust": %s,
                        "pressure": 1011.85,
                        "precipRate": 0.00,
                        "elev": 52
                      }
                    }
                  ]
                }
                """.formatted(windDir, tempC, windSpeedKmh, windGustKmh);
    }
}
