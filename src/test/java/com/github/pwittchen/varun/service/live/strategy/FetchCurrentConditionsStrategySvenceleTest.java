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

class FetchCurrentConditionsStrategySvenceleTest {

    private MockWebServer mockWebServer;
    private FetchCurrentConditionsStrategySvencele strategy;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        strategy = new FetchCurrentConditionsStrategySvencele(new OkHttpClient());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void shouldReturnTrueForSvenceleWgId() {
        assertThat(strategy.canProcess(1025272)).isTrue();
    }

    @Test
    void shouldReturnFalseForOtherWgIds() {
        assertThat(strategy.canProcess(1068590)).isFalse();
        assertThat(strategy.canProcess(726)).isFalse();
        assertThat(strategy.canProcess(999999)).isFalse();
    }

    @Test
    void shouldParseValidHolfuyHtmlResponse() {
        String mockResponse = createMockHtmlResponse(5.4, 8.1, "SE", -4.0);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/en/weather/1515").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions).isNotNull();
                    assertThat(conditions.direction()).isEqualTo("SE");
                    assertThat(conditions.wind()).isEqualTo(5); // 5.4 knots rounded to 5
                    assertThat(conditions.gusts()).isEqualTo(8); // 8.1 knots rounded to 8
                    assertThat(conditions.temp()).isEqualTo(-4);
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleWindSpeedInKnots() {
        // Wind speed already in knots - no conversion needed
        String mockResponse = createMockHtmlResponse(10.5, 15.3, "NE", 5.0);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/en/weather/1515").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.wind()).isEqualTo(11); // 10.5 knots rounded to 11
                    assertThat(conditions.gusts()).isEqualTo(15); // 15.3 knots rounded to 15
                })
                .verifyComplete();
    }

    @Test
    void shouldNormalizeCardinalDirectionN() {
        String mockResponse = createMockHtmlResponse(3.0, 5.0, "N", 10.0);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/en/weather/1515").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("N");
                })
                .verifyComplete();
    }

    @Test
    void shouldNormalizeCardinalDirectionNE() {
        String mockResponse = createMockHtmlResponse(3.0, 5.0, "NE", 10.0);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/en/weather/1515").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("NE");
                })
                .verifyComplete();
    }

    @Test
    void shouldNormalizeCardinalDirectionE() {
        String mockResponse = createMockHtmlResponse(3.0, 5.0, "E", 10.0);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/en/weather/1515").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("E");
                })
                .verifyComplete();
    }

    @Test
    void shouldNormalizeCardinalDirectionSE() {
        String mockResponse = createMockHtmlResponse(3.0, 5.0, "SE", 10.0);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/en/weather/1515").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("SE");
                })
                .verifyComplete();
    }

    @Test
    void shouldNormalizeCardinalDirectionS() {
        String mockResponse = createMockHtmlResponse(3.0, 5.0, "S", 10.0);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/en/weather/1515").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("S");
                })
                .verifyComplete();
    }

    @Test
    void shouldNormalizeCardinalDirectionSW() {
        String mockResponse = createMockHtmlResponse(3.0, 5.0, "SW", 10.0);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/en/weather/1515").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("SW");
                })
                .verifyComplete();
    }

    @Test
    void shouldNormalizeCardinalDirectionW() {
        String mockResponse = createMockHtmlResponse(3.0, 5.0, "W", 10.0);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/en/weather/1515").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("W");
                })
                .verifyComplete();
    }

    @Test
    void shouldNormalizeCardinalDirectionNW() {
        String mockResponse = createMockHtmlResponse(3.0, 5.0, "NW", 10.0);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/en/weather/1515").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("NW");
                })
                .verifyComplete();
    }

    @Test
    void shouldNormalizeSSEtoSE() {
        String mockResponse = createMockHtmlResponse(3.0, 5.0, "SSE", 10.0);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/en/weather/1515").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("SE");
                })
                .verifyComplete();
    }

    @Test
    void shouldNormalizeESEtoSE() {
        String mockResponse = createMockHtmlResponse(3.0, 5.0, "ESE", 10.0);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/en/weather/1515").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("SE");
                })
                .verifyComplete();
    }

    @Test
    void shouldNormalizeNNEtoNE() {
        String mockResponse = createMockHtmlResponse(3.0, 5.0, "NNE", 10.0);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/en/weather/1515").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("NE");
                })
                .verifyComplete();
    }

    @Test
    void shouldNormalizeWSWtoSW() {
        String mockResponse = createMockHtmlResponse(3.0, 5.0, "WSW", 10.0);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/en/weather/1515").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("SW");
                })
                .verifyComplete();
    }

    @Test
    void shouldRoundDecimalValues() {
        String mockResponse = createMockHtmlResponse(2.6, 4.3, "S", 18.7);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/en/weather/1515").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.wind()).isEqualTo(3); // 2.6 rounded to 3
                    assertThat(conditions.gusts()).isEqualTo(4); // 4.3 rounded to 4
                    assertThat(conditions.temp()).isEqualTo(19); // 18.7 rounded to 19
                })
                .verifyComplete();
    }

    @Test
    void shouldSetTimestampToCurrentTime() {
        String mockResponse = createMockHtmlResponse(3.0, 5.0, "S", 10.0);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/en/weather/1515").toString();
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

        String url = mockWebServer.url("/en/weather/1515").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleNullResponseBody() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(""));

        String url = mockWebServer.url("/en/weather/1515").toString();
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

        String url = mockWebServer.url("/en/weather/1515").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldReturnCorrectUrl() {
        assertThat(strategy.getUrl(1025272)).isEqualTo("https://holfuy.com/en/weather/1515");
    }

    @Test
    void shouldHandleZeroWindSpeed() {
        String mockResponse = createMockHtmlResponse(0.0, 0.0, "E", 15.0);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/en/weather/1515").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.wind()).isEqualTo(0);
                    assertThat(conditions.gusts()).isEqualTo(0);
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleNegativeTemperature() {
        String mockResponse = createMockHtmlResponse(7.8, 10.3, "SW", -8.2);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/en/weather/1515").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.temp()).isEqualTo(-8);
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

        String url = mockWebServer.url("/en/weather/1515").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    // Last speed value is 5.4
                    assertThat(conditions.wind()).isEqualTo(5);
                    // Last gust value is 8.1
                    assertThat(conditions.gusts()).isEqualTo(8);
                    // Last direction is SE
                    assertThat(conditions.direction()).isEqualTo("SE");
                    // Last temp is -4.0
                    assertThat(conditions.temp()).isEqualTo(-4);
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleVeryHighWindSpeeds() {
        String mockResponse = createMockHtmlResponse(25.3, 35.7, "W", 2.0);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/en/weather/1515").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.wind()).isEqualTo(25); // 25.3 knots
                    assertThat(conditions.gusts()).isEqualTo(36); // 35.7 knots
                })
                .verifyComplete();
    }

    private String createMockHtmlResponse(double speed, double gust, String direction, double temp) {
        // Match real Holfuy HTML structure
        return """
                <html>
                <body>
                <table class="hour_table">
                <tr style="height:1.9em; font-size:larger;">
                    <td class="h_header" style=" font-size:smaller; padding-top:5px;">Speed</td>
                    <td style="background:#00ff19;">%s</td>
                </tr>
                <tr>
                    <td class="h_header">Gust <span style="font-size: smaller;"> (knots)</span></td>
                    <td bgcolor="#b1ff00">%s</td>
                </tr>
                <tr>
                    <td class="h_header">Direction</td>
                    <td>%s</td>
                </tr>
                <tr>
                    <td class="h_header">Temp. <span style="font-size: smaller;"> (°C)</span></td>
                    <td bgcolor="#ffff92">%s</td>
                </tr>
                </table>
                </body>
                </html>
                """.formatted(speed, gust, direction, temp);
    }

    private String createMockHtmlResponseWithMultipleValues() {
        return """
                <html>
                <body>
                <table class="hour_table">
                <tr style="height:1.9em; font-size:larger;">
                    <td class="h_header" style=" font-size:smaller; padding-top:5px;">Speed</td>
                    <td style="background:#00ff19;">4.9</td>
                    <td style="background:#00ff4c;">5.1</td>
                    <td style="background:#00ff00;">5.4</td>
                </tr>
                <tr>
                    <td class="h_header">Gust <span style="font-size: smaller;"> (knots)</span></td>
                    <td bgcolor="#b1ff00">7.8</td>
                    <td bgcolor="#32ff00">6.8</td>
                    <td bgcolor="#98ff00">8.1</td>
                </tr>
                <tr>
                    <td class="h_header">Direction</td>
                    <td>SSE</td>
                    <td>SE</td>
                    <td>SE</td>
                </tr>
                <tr>
                    <td class="h_header">Temp. <span style="font-size: smaller;"> (°C)</span></td>
                    <td bgcolor="#ffff92">-5.3</td>
                    <td bgcolor="#ffff94">-5.2</td>
                    <td bgcolor="#ffffae">-4.0</td>
                </tr>
                </table>
                </body>
                </html>
                """;
    }

    @Test
    void shouldHandleTwoDirectionRows() {
        // Real Holfuy pages have TWO Direction rows:
        // 1. One with arrow icons (style="padding-top:5px;")
        // 2. One with text cardinal directions
        String mockResponse = """
                <html>
                <body>
                <table class="hour_table">
                <tr style="height:1.9em; font-size:larger;">
                    <td class="h_header" style=" font-size:smaller; padding-top:5px;">Speed</td>
                    <td style="background:#00ff19;">8.6</td>
                </tr>
                <tr>
                    <td class="h_header">Gust <span style="font-size: smaller;"> (knots)</span></td>
                    <td bgcolor="#b1ff00">14.6</td>
                </tr>
                <tr>
                    <td class="h_header" style="padding-top:5px;">Direction</td>
                    <td class="h_img w_dir" style="background:whitesmoke;"><i class="fa fa-long-arrow-down rotate-130"></i></td>
                </tr>
                <tr>
                    <td class="h_header">Direction</td>
                    <td>SE</td>
                </tr>
                <tr>
                    <td class="h_header">Temp. <span style="font-size: smaller;"> (°C)</span></td>
                    <td bgcolor="#ffff92">-5.3</td>
                </tr>
                </table>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/en/weather/1515").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.wind()).isEqualTo(9); // 8.6 rounded
                    assertThat(conditions.gusts()).isEqualTo(15); // 14.6 rounded
                    assertThat(conditions.direction()).isEqualTo("SE");
                    assertThat(conditions.temp()).isEqualTo(-5);
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleDashesInDirection() {
        // Test that dashes (missing data) are skipped when extracting directions
        String mockResponse = """
                <html>
                <body>
                <table class="hour_table">
                <tr style="height:1.9em; font-size:larger;">
                    <td class="h_header" style=" font-size:smaller; padding-top:5px;">Speed</td>
                    <td>-</td>
                    <td style="background:#00ff19;">7.6</td>
                </tr>
                <tr>
                    <td class="h_header">Gust <span style="font-size: smaller;"> (knots)</span></td>
                    <td>-</td>
                    <td bgcolor="#b1ff00">11.9</td>
                </tr>
                <tr>
                    <td class="h_header">Direction</td>
                    <td>-</td>
                    <td>ESE</td>
                </tr>
                <tr>
                    <td class="h_header">Temp. <span style="font-size: smaller;"> (°C)</span></td>
                    <td>-</td>
                    <td bgcolor="#ffff92">-10.8</td>
                </tr>
                </table>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/en/weather/1515").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.wind()).isEqualTo(8); // 7.6 rounded
                    assertThat(conditions.gusts()).isEqualTo(12); // 11.9 rounded
                    assertThat(conditions.direction()).isEqualTo("SE"); // ESE normalized to SE
                    assertThat(conditions.temp()).isEqualTo(-11); // -10.8 rounded
                })
                .verifyComplete();
    }
}
