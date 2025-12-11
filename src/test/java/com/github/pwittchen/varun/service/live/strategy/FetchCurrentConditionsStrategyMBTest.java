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

class FetchCurrentConditionsStrategyMBTest {

    private MockWebServer mockWebServer;
    private FetchCurrentConditionsStrategyMB strategy;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        strategy = new FetchCurrentConditionsStrategyMB(new OkHttpClient());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void shouldReturnTrueForMBWgId() {
        assertThat(strategy.canProcess(1068590)).isTrue();
    }

    @Test
    void shouldReturnFalseForOtherWgIds() {
        assertThat(strategy.canProcess(859182)).isFalse();
        assertThat(strategy.canProcess(726)).isFalse();
        assertThat(strategy.canProcess(999999)).isFalse();
    }

    @Test
    void shouldParseValidHolfuyHtmlResponse() {
        String mockResponse = createMockHtmlResponse(5.3, 7.2, 34, 3.9);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/en/weather/1612").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions).isNotNull();
                    assertThat(conditions.direction()).isEqualTo("NE"); // 34° is NE
                    assertThat(conditions.wind()).isEqualTo(10); // 5.3 m/s * 1.94384 = 10.3 knots, rounded to 10
                    assertThat(conditions.gusts()).isEqualTo(14); // 7.2 m/s * 1.94384 = 14.0 knots, rounded to 14
                    assertThat(conditions.temp()).isEqualTo(4);
                })
                .verifyComplete();
    }

    @Test
    void shouldConvertWindSpeedFromMsToKnots() {
        String mockResponse = createMockHtmlResponse(5.14, 10.3, 90, 10.0);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/en/weather/1612").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    // 5.14 m/s * 1.94384 = 9.99 knots, rounded to 10
                    assertThat(conditions.wind()).isEqualTo(10);
                    // 10.3 m/s * 1.94384 = 20.02 knots, rounded to 20
                    assertThat(conditions.gusts()).isEqualTo(20);
                })
                .verifyComplete();
    }

    @Test
    void shouldConvertDegreesToCardinalDirectionNorth() {
        String mockResponse = createMockHtmlResponse(3.0, 5.0, 0, 10.0);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/en/weather/1612").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("N");
                })
                .verifyComplete();
    }

    @Test
    void shouldConvertDegreesToCardinalDirectionNorthEast() {
        String mockResponse = createMockHtmlResponse(3.0, 5.0, 45, 10.0);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/en/weather/1612").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("NE");
                })
                .verifyComplete();
    }

    @Test
    void shouldConvertDegreesToCardinalDirectionEast() {
        String mockResponse = createMockHtmlResponse(3.0, 5.0, 90, 10.0);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/en/weather/1612").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("E");
                })
                .verifyComplete();
    }

    @Test
    void shouldConvertDegreesToCardinalDirectionSouthWest() {
        String mockResponse = createMockHtmlResponse(3.0, 5.0, 225, 10.0);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/en/weather/1612").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("SW");
                })
                .verifyComplete();
    }

    @Test
    void shouldRoundDecimalValues() {
        String mockResponse = createMockHtmlResponse(2.57, 4.63, 180, 18.6);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/en/weather/1612").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    // 2.57 m/s * 1.94384 = 4.99 knots, rounded to 5
                    assertThat(conditions.wind()).isEqualTo(5);
                    // 4.63 m/s * 1.94384 = 9.00 knots
                    assertThat(conditions.gusts()).isEqualTo(9);
                    assertThat(conditions.temp()).isEqualTo(19);
                })
                .verifyComplete();
    }

    @Test
    void shouldSetTimestampToCurrentTime() {
        String mockResponse = createMockHtmlResponse(3.0, 5.0, 180, 10.0);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/en/weather/1612").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    // Timestamp should be in the expected format
                    assertThat(conditions.date()).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleHttpError() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500));

        String url = mockWebServer.url("/en/weather/1612").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleMissingSpeedRow() {
        String mockResponse = "<html><body>No data</body></html>";

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/en/weather/1612").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldReturnCorrectUrl() {
        assertThat(strategy.getUrl(1068590)).isEqualTo("https://holfuy.com/en/weather/1612");
    }

    @Test
    void shouldHandleZeroWindSpeed() {
        String mockResponse = createMockHtmlResponse(0.0, 0.0, 90, 15.0);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/en/weather/1612").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.wind()).isEqualTo(0);
                    assertThat(conditions.gusts()).isEqualTo(0);
                })
                .verifyComplete();
    }

    @Test
    void shouldExtractLastValueFromMultipleColumns() {
        // Test that we correctly extract the LAST value from multiple columns
        String mockResponse = createMockHtmlResponseWithMultipleValues();

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/en/weather/1612").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    // Last speed value is 5.3
                    assertThat(conditions.wind()).isEqualTo(10); // 5.3 * 1.94384 = 10.3
                    // Last gust value is 7.2
                    assertThat(conditions.gusts()).isEqualTo(14); // 7.2 * 1.94384 = 14.0
                    // Last direction is 34°
                    assertThat(conditions.direction()).isEqualTo("NE");
                    // Last temp is 3.9
                    assertThat(conditions.temp()).isEqualTo(4);
                })
                .verifyComplete();
    }

    private String createMockHtmlResponse(double speed, double gust, int directionDeg, double temp) {
        String directionCardinal = directionDeg < 23 ? "N" :
                                   directionDeg < 68 ? "NE" :
                                   directionDeg < 113 ? "E" :
                                   directionDeg < 158 ? "SE" :
                                   directionDeg < 203 ? "S" :
                                   directionDeg < 248 ? "SW" :
                                   directionDeg < 293 ? "W" :
                                   directionDeg < 338 ? "NW" : "N";

        // Match real Holfuy HTML structure exactly
        return """
                <html>
                <body>
                <table class="hour_table">
                <tr style="height:1.9em; font-size:larger;">
                    <td class="h_header" style=" font-size:smaller; padding-top:5px;">Speed</td>
                    <td style="background:#00ff19;">%s</td>
                </tr>
                <tr>
                    <td class="h_header">Gust <span style="font-size: smaller;"> (m/s)</span></td>
                    <td bgcolor="#b1ff00">%s</td>
                </tr>
                <tr>
                    <td class="h_header">Direction<br><span style="font-size: smaller;">Deg.</span></td>
                    <td>%s<br>%d°</td>
                </tr>
                <tr>
                    <td class="h_header">Temp. <span style="font-size: smaller;"> (°C)</span></td>
                    <td bgcolor="#ffff92">%s</td>
                </tr>
                </table>
                </body>
                </html>
                """.formatted(speed, gust, directionCardinal, directionDeg, temp);
    }

    private String createMockHtmlResponseWithMultipleValues() {
        return """
                <html>
                <body>
                <table class="hour_table">
                <tr style="height:1.9em; font-size:larger;">
                    <td class="h_header" style=" font-size:smaller; padding-top:5px;">Speed</td>
                    <td style="background:#00ff19;">4.7</td>
                    <td style="background:#00ff4c;">4.2</td>
                    <td style="background:#00ff00;">5.3</td>
                </tr>
                <tr>
                    <td class="h_header">Gust <span style="font-size: smaller;"> (m/s)</span></td>
                    <td bgcolor="#b1ff00">7.8</td>
                    <td bgcolor="#32ff00">5.8</td>
                    <td bgcolor="#98ff00">7.2</td>
                </tr>
                <tr>
                    <td class="h_header">Direction<br><span style="font-size: smaller;">Deg.</span></td>
                    <td>NNE<br>33°</td>
                    <td>NE<br>35°</td>
                    <td>NE<br>34°</td>
                </tr>
                <tr>
                    <td class="h_header">Temp. <span style="font-size: smaller;"> (°C)</span></td>
                    <td bgcolor="#ffff92">5.3</td>
                    <td bgcolor="#ffff94">5.2</td>
                    <td bgcolor="#ffffae">3.9</td>
                </tr>
                </table>
                </body>
                </html>
                """;
    }
}
