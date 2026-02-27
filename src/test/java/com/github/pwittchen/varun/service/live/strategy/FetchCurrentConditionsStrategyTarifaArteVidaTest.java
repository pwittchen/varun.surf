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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

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

    /**
     * Creates a mock JavaScript response matching the Spotfav format.
     * The format uses HTML entities and wraps data in {"hours": [...]}
     */
    private String createMockJsResponse(double windSpeedMs, double gustMs, int windDirectionDeg, double tempC) {
        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        // Build JSON with HTML entities like the real response
        String jsonData = String.format(Locale.US,
                "{&quot;hours&quot;:[{&quot;time&quot;:&quot;%s&quot;,&quot;windSpeed&quot;:{&quot;sg&quot;:%.2f},&quot;gust&quot;:{&quot;sg&quot;:%.2f},&quot;windDirection&quot;:{&quot;sg&quot;:%d},&quot;airTemperature&quot;:{&quot;sg&quot;:%.1f}}]}",
                currentTime, windSpeedMs, gustMs, windDirectionDeg, tempC
        );
        return "const hourly_weather_forecast = JSON.parse((\"" + jsonData + "\").replaceAll('&amp;', '&'));";
    }

    @Test
    void shouldParseValidSpotfavJsonResponse() {
        // 6.69 m/s = ~13 knots, 12.86 m/s = ~25 knots
        String mockResponse = createMockJsResponse(6.69, 12.86, 85, 15.0);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/update/").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        String expectedDatePrefix = ZonedDateTime.now(ZoneId.of("Europe/Madrid"))
                .toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);

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
        // 9.26 m/s = ~18 knots, 14.40 m/s = ~28 knots
        String mockResponse = createMockJsResponse(9.26, 14.40, 5, 20.0);

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
        // 11.32 m/s = ~22 knots, 15.43 m/s = ~30 knots
        String mockResponse = createMockJsResponse(11.32, 15.43, 225, 18.0);

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
        // 18.01 m/s = ~35 knots, 23.15 m/s = ~45 knots
        String mockResponse = createMockJsResponse(18.01, 23.15, 90, 25.0);

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
        // 10.29 m/s = ~20 knots, 14.40 m/s = ~28 knots
        String mockResponse = createMockJsResponse(10.29, 14.40, 270, 22.0);

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
    void shouldHandleMissingForecastData() {
        String mockResponse = "const some_other_variable = 'value';";

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
        // 2.57 m/s = ~5 knots, 4.12 m/s = ~8 knots
        String mockResponse = createMockJsResponse(2.57, 4.12, 180, 28.0);

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
        // 7.72 m/s = ~15 knots, 11.32 m/s = ~22 knots
        String mockResponse = createMockJsResponse(7.72, 11.32, 45, 19.0);

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
        // 6.17 m/s = ~12 knots, 9.26 m/s = ~18 knots
        String mockResponse = createMockJsResponse(6.17, 9.26, 315, 17.0);

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
        // 8.23 m/s = ~16 knots, 12.35 m/s = ~24 knots
        String mockResponse = createMockJsResponse(8.23, 12.35, 135, 21.0);

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

    @Test
    void shouldConvertMetersPerSecondToKnots() {
        // 10 m/s = 19.44 knots (rounds to 19)
        // 15 m/s = 29.16 knots (rounds to 29)
        String mockResponse = createMockJsResponse(10.0, 15.0, 90, 20.0);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/update/").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.wind()).isEqualTo(19);
                    assertThat(conditions.gusts()).isEqualTo(29);
                })
                .verifyComplete();
    }

    @Test
    void shouldSelectClosestTimeEntry() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime pastTime = now.minusHours(2);
        LocalDateTime futureTime = now.plusHours(2);
        LocalDateTime closestTime = now.minusMinutes(30);

        String jsonData = String.format(Locale.US,
                "{&quot;hours&quot;:[" +
                        "{&quot;time&quot;:&quot;%s&quot;,&quot;windSpeed&quot;:{&quot;sg&quot;:5.0},&quot;gust&quot;:{&quot;sg&quot;:8.0},&quot;windDirection&quot;:{&quot;sg&quot;:90},&quot;airTemperature&quot;:{&quot;sg&quot;:15.0}}," +
                        "{&quot;time&quot;:&quot;%s&quot;,&quot;windSpeed&quot;:{&quot;sg&quot;:10.0},&quot;gust&quot;:{&quot;sg&quot;:15.0},&quot;windDirection&quot;:{&quot;sg&quot;:180},&quot;airTemperature&quot;:{&quot;sg&quot;:20.0}}," +
                        "{&quot;time&quot;:&quot;%s&quot;,&quot;windSpeed&quot;:{&quot;sg&quot;:15.0},&quot;gust&quot;:{&quot;sg&quot;:22.0},&quot;windDirection&quot;:{&quot;sg&quot;:270},&quot;airTemperature&quot;:{&quot;sg&quot;:25.0}}" +
                        "]}",
                pastTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                closestTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                futureTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
        String mockResponse = "const hourly_weather_forecast = JSON.parse((\"" + jsonData + "\").replaceAll('&amp;', '&'));";

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/update/").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    // Should pick the closest entry (10 m/s = 19 knots, 15 m/s = 29 knots, S direction)
                    assertThat(conditions.wind()).isEqualTo(19);
                    assertThat(conditions.gusts()).isEqualTo(29);
                    assertThat(conditions.direction()).isEqualTo("S");
                    assertThat(conditions.temp()).isEqualTo(20);
                })
                .verifyComplete();
    }
}