package com.github.pwittchen.varun.service.live.strategy;

import com.github.pwittchen.varun.http.HttpClientProxy;
import com.github.pwittchen.varun.model.live.CurrentConditions;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

class FetchCurrentConditionsStrategyTurawaTest {

    private MockWebServer mockWebServer;
    private FetchCurrentConditionsStrategyTurawa strategy;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        strategy = new FetchCurrentConditionsStrategyTurawa(new HttpClientProxy());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void shouldReturnTrueForTurawaWgId() {
        assertThat(strategy.canProcess(726)).isTrue();
    }

    @Test
    void shouldReturnFalseForOtherWgIds() {
        assertThat(strategy.canProcess(859182)).isFalse();
        assertThat(strategy.canProcess(999999)).isFalse();
    }

    @Test
    void shouldParseValidTurawaResponse() {
        String mockResponse = """
                <html>
                <body>
                <div id='widget' name='widget' style='position: absolute; background-color: #F0F0FF; width: 110px; opacity: 0.7; filter: alpha(opacity=70);-webkit-border-radius: 20px;-moz-border-radius: 20px;border-radius: 20px;font-size:12px; font-family:Tahoma;' align='left'>
                    <div align='right' style='padding-top:10px; padding-right:10px; font-size: 9px;'>2025-11-07 15:44 <a style='cursor: pointer;' onClick='javascript:document.getElementById("widget").style.visibility="hidden"'>[x]</a></div>
                    <div><img src='https://openweathermap.org/img/w/01d.png'><div style='display: inline; vertical-align: middle; line-height: 50px; height: 50px; float: right; padding-right: 10px; font-size: 16px;'>14&deg;C</div></div>
                    <div><img src='https://airmax.pl/humidity.png' style='width: 30px; padding-left: 15px; padding-bottom: 10px;' align='absmiddle'><div style='display: inline; float: right; padding-right: 10px;'>60 %</div></div>
                    <div><img src='https://airmax.pl/pressure.png' style='width: 30px; padding-left: 15px; padding-bottom: 10px;' align='absmiddle'><div style='display: inline; float: right; padding-right: 10px;'>1016 hPa</div></div>
                    <div><img src='https://airmax.pl/wind_speed.png' style='width: 30px; padding-left: 15px; padding-bottom: 10px;' align='absmiddle'><div style='display: inline; float: right; padding-right: 10px;'>3.73 m/s</div></div>
                    <div><img src='https://airmax.pl/wind_rose.png' style='width: 30px; padding-left: 15px; padding-bottom: 10px;' align='absmiddle'><div style='display: inline; float: right; padding-right: 10px;'>164 &deg;</div></div>
                </div>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/kamery/turawa").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions).isNotNull();
                    assertThat(conditions.date()).isEqualTo("2025-11-07 15:44");
                    assertThat(conditions.direction()).isEqualTo("S"); // 164° rounds to S (closest to 180°)
                    assertThat(conditions.wind()).isEqualTo(7); // 3.73 m/s * 1.94384 = 7.25 knots, rounded to 7
                    assertThat(conditions.gusts()).isEqualTo(0); // No separate gust data in HTML
                    assertThat(conditions.temp()).isEqualTo(14);
                })
                .verifyComplete();
    }

    @Test
    void shouldConvertDegreesToCardinalDirectionNorth() {
        String mockResponse = createMockResponseWithDirection(0);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/kamery/turawa").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("N");
                })
                .verifyComplete();
    }

    @Test
    void shouldConvertDegreesToCardinalDirectionNorthEast() {
        String mockResponse = createMockResponseWithDirection(45);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/kamery/turawa").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("NE");
                })
                .verifyComplete();
    }

    @Test
    void shouldConvertDegreesToCardinalDirectionEast() {
        String mockResponse = createMockResponseWithDirection(90);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/kamery/turawa").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("E");
                })
                .verifyComplete();
    }

    @Test
    void shouldConvertDegreesToCardinalDirectionSouthEast() {
        String mockResponse = createMockResponseWithDirection(135);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/kamery/turawa").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("SE");
                })
                .verifyComplete();
    }

    @Test
    void shouldConvertDegreesToCardinalDirectionSouth() {
        String mockResponse = createMockResponseWithDirection(180);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/kamery/turawa").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("S");
                })
                .verifyComplete();
    }

    @Test
    void shouldConvertDegreesToCardinalDirectionSouthWest() {
        String mockResponse = createMockResponseWithDirection(225);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/kamery/turawa").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("SW");
                })
                .verifyComplete();
    }

    @Test
    void shouldConvertDegreesToCardinalDirectionWest() {
        String mockResponse = createMockResponseWithDirection(270);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/kamery/turawa").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("W");
                })
                .verifyComplete();
    }

    @Test
    void shouldConvertDegreesToCardinalDirectionNorthWest() {
        String mockResponse = createMockResponseWithDirection(315);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/kamery/turawa").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("NW");
                })
                .verifyComplete();
    }

    @Test
    void shouldConvertWindSpeedFromMsToKnots() {
        String mockResponse = """
                <html>
                <body>
                <div id='widget'>
                    <div align='right' style='padding-top:10px; padding-right:10px; font-size: 9px;'>2025-11-07 15:44 [x]</div>
                    <div style='display: inline; vertical-align: middle; line-height: 50px; height: 50px; float: right; padding-right: 10px; font-size: 16px;'>20&deg;C</div>
                    <div><img src='https://airmax.pl/wind_speed.png' style='width: 30px; padding-left: 15px; padding-bottom: 10px;' align='absmiddle'><div style='display: inline; float: right; padding-right: 10px;'>5.14 m/s</div></div>
                    <div><img src='https://airmax.pl/wind_rose.png' style='width: 30px; padding-left: 15px; padding-bottom: 10px;' align='absmiddle'><div style='display: inline; float: right; padding-right: 10px;'>90 &deg;</div></div>
                </div>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/kamery/turawa").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    // 5.14 m/s * 1.94384 = 9.99 knots, rounded to 10
                    assertThat(conditions.wind()).isEqualTo(10);
                })
                .verifyComplete();
    }

    @Test
    void shouldRoundDecimalValues() {
        String mockResponse = """
                <html>
                <body>
                <div id='widget'>
                    <div align='right' style='padding-top:10px; padding-right:10px; font-size: 9px;'>2025-11-07 15:44 [x]</div>
                    <div style='display: inline; vertical-align: middle; line-height: 50px; height: 50px; float: right; padding-right: 10px; font-size: 16px;'>18&deg;C</div>
                    <div><img src='https://airmax.pl/wind_speed.png' style='width: 30px; padding-left: 15px; padding-bottom: 10px;' align='absmiddle'><div style='display: inline; float: right; padding-right: 10px;'>4.63 m/s</div></div>
                    <div><img src='https://airmax.pl/wind_rose.png' style='width: 30px; padding-left: 15px; padding-bottom: 10px;' align='absmiddle'><div style='display: inline; float: right; padding-right: 10px;'>164 &deg;</div></div>
                </div>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/kamery/turawa").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    // 4.63 m/s * 1.94384 = 9.00 knots
                    assertThat(conditions.wind()).isEqualTo(9);
                    assertThat(conditions.temp()).isEqualTo(18);
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleHttpError() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500));

        String url = mockWebServer.url("/kamery/turawa").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleMissingTimestamp() {
        String mockResponse = """
                <html>
                <body>
                <div id='widget'>
                    <div style='display: inline; vertical-align: middle; line-height: 50px; height: 50px; float: right; padding-right: 10px; font-size: 16px;'>14&deg;C</div>
                    <div><img src='https://airmax.pl/wind_speed.png' style='width: 30px; padding-left: 15px; padding-bottom: 10px;' align='absmiddle'><div style='display: inline; float: right; padding-right: 10px;'>3.73 m/s</div></div>
                    <div><img src='https://airmax.pl/wind_rose.png' style='width: 30px; padding-left: 15px; padding-bottom: 10px;' align='absmiddle'><div style='display: inline; float: right; padding-right: 10px;'>164 &deg;</div></div>
                </div>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/kamery/turawa").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleMissingTemperature() {
        String mockResponse = """
                <html>
                <body>
                <div id='widget'>
                    <div align='right' style='padding-top:10px; padding-right:10px; font-size: 9px;'>2025-11-07 15:44 [x]</div>
                    <div><img src='https://airmax.pl/wind_speed.png' style='width: 30px; padding-left: 15px; padding-bottom: 10px;' align='absmiddle'><div style='display: inline; float: right; padding-right: 10px;'>3.73 m/s</div></div>
                    <div><img src='https://airmax.pl/wind_rose.png' style='width: 30px; padding-left: 15px; padding-bottom: 10px;' align='absmiddle'><div style='display: inline; float: right; padding-right: 10px;'>164 &deg;</div></div>
                </div>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/kamery/turawa").toString();
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
                <div id='widget'>
                    <div align='right' style='padding-top:10px; padding-right:10px; font-size: 9px;'>2025-11-07 15:44 [x]</div>
                    <div style='display: inline; vertical-align: middle; line-height: 50px; height: 50px; float: right; padding-right: 10px; font-size: 16px;'>14&deg;C</div>
                    <div><img src='https://airmax.pl/wind_rose.png' style='width: 30px; padding-left: 15px; padding-bottom: 10px;' align='absmiddle'><div style='display: inline; float: right; padding-right: 10px;'>164 &deg;</div></div>
                </div>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/kamery/turawa").toString();
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
                <div id='widget'>
                    <div align='right' style='padding-top:10px; padding-right:10px; font-size: 9px;'>2025-11-07 15:44 [x]</div>
                    <div style='display: inline; vertical-align: middle; line-height: 50px; height: 50px; float: right; padding-right: 10px; font-size: 16px;'>14&deg;C</div>
                    <div><img src='https://airmax.pl/wind_speed.png' style='width: 30px; padding-left: 15px; padding-bottom: 10px;' align='absmiddle'><div style='display: inline; float: right; padding-right: 10px;'>3.73 m/s</div></div>
                </div>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/kamery/turawa").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldReturnCorrectUrl() {
        assertThat(strategy.getUrl(726)).isEqualTo("https://airmax.pl/kamery/turawa");
    }

    @Test
    void shouldHandleEdgeCaseWindDirections() {
        // Test 359 degrees (should be N, not NW)
        String mockResponse = createMockResponseWithDirection(359);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/kamery/turawa").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("N");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleIntermediateWindDirections() {
        // Test 164 degrees (should be S since it's closer to 180° than 135°)
        String mockResponse = createMockResponseWithDirection(164);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/kamery/turawa").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("S");
                })
                .verifyComplete();
    }

    @Test
    void shouldParseNegativeTemperature() {
        String mockResponse = """
                <html>
                <body>
                <div id='widget'>
                    <div align='right' style='padding-top:10px; padding-right:10px; font-size: 9px;'>2025-11-07 15:44 [x]</div>
                    <div style='display: inline; vertical-align: middle; line-height: 50px; height: 50px; float: right; padding-right: 10px; font-size: 16px;'>-5&deg;C</div>
                    <div><img src='https://airmax.pl/wind_speed.png' style='width: 30px; padding-left: 15px; padding-bottom: 10px;' align='absmiddle'><div style='display: inline; float: right; padding-right: 10px;'>3.73 m/s</div></div>
                    <div><img src='https://airmax.pl/wind_rose.png' style='width: 30px; padding-left: 15px; padding-bottom: 10px;' align='absmiddle'><div style='display: inline; float: right; padding-right: 10px;'>180 &deg;</div></div>
                </div>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/kamery/turawa").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.temp()).isEqualTo(-5);
                })
                .verifyComplete();
    }

    @Test
    void shouldParseZeroTemperature() {
        String mockResponse = """
                <html>
                <body>
                <div id='widget'>
                    <div align='right' style='padding-top:10px; padding-right:10px; font-size: 9px;'>2025-11-07 15:44 [x]</div>
                    <div style='display: inline; vertical-align: middle; line-height: 50px; height: 50px; float: right; padding-right: 10px; font-size: 16px;'>0&deg;C</div>
                    <div><img src='https://airmax.pl/wind_speed.png' style='width: 30px; padding-left: 15px; padding-bottom: 10px;' align='absmiddle'><div style='display: inline; float: right; padding-right: 10px;'>3.73 m/s</div></div>
                    <div><img src='https://airmax.pl/wind_rose.png' style='width: 30px; padding-left: 15px; padding-bottom: 10px;' align='absmiddle'><div style='display: inline; float: right; padding-right: 10px;'>180 &deg;</div></div>
                </div>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/kamery/turawa").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.temp()).isEqualTo(0);
                })
                .verifyComplete();
    }

    private String createMockResponseWithDirection(int degrees) {
        return """
                <html>
                <body>
                <div id='widget'>
                    <div align='right' style='padding-top:10px; padding-right:10px; font-size: 9px;'>2025-11-07 15:44 [x]</div>
                    <div style='display: inline; vertical-align: middle; line-height: 50px; height: 50px; float: right; padding-right: 10px; font-size: 16px;'>14&deg;C</div>
                    <div><img src='https://airmax.pl/wind_speed.png' style='width: 30px; padding-left: 15px; padding-bottom: 10px;' align='absmiddle'><div style='display: inline; float: right; padding-right: 10px;'>3.73 m/s</div></div>
                    <div><img src='https://airmax.pl/wind_rose.png' style='width: 30px; padding-left: 15px; padding-bottom: 10px;' align='absmiddle'><div style='display: inline; float: right; padding-right: 10px;'>%d &deg;</div></div>
                </div>
                </body>
                </html>
                """.formatted(degrees);
    }
}