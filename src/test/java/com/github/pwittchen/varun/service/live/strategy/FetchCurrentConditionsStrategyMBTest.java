package com.github.pwittchen.varun.service.live.strategy;

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

class FetchCurrentConditionsStrategyMBTest {

    private MockWebServer mockWebServer;
    private FetchCurrentConditionsStrategyMB strategy;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        strategy = new FetchCurrentConditionsStrategyMB();
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
    void shouldParseValidStationDataResponse() {
        String mockResponse = """
                <html>
                <body>
                <div>Some content</div>
                #&lt;struct StationData id=13563918, epoch=1732806483, hsource=&quot;H1612&quot;, humidity=0, pressure=0.0, psource=&quot;H1612&quot;, rsource=&quot;H1612&quot;, station=&quot;zar&quot;, temperature=-1.0, tsource=&quot;H1612&quot;, winddir=207, windgusts=3.9, windspeed=3.1, wsource=&quot;H1612&quot;&gt;
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/stacje/zar").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions).isNotNull();
                    assertThat(conditions.date()).isEqualTo("2024-11-28 16:08:03");
                    assertThat(conditions.direction()).isEqualTo("SW"); // 207° is SW
                    assertThat(conditions.wind()).isEqualTo(6); // 3.1 m/s * 1.94384 = 6.03 knots, rounded to 6
                    assertThat(conditions.gusts()).isEqualTo(8); // 3.9 m/s * 1.94384 = 7.58 knots, rounded to 8
                    assertThat(conditions.temp()).isEqualTo(-1);
                })
                .verifyComplete();
    }

    @Test
    void shouldConvertWindSpeedFromMsToKnots() {
        String mockResponse = """
                <html>
                <body>
                #&lt;struct StationData id=13563918, epoch=1732809683, hsource=&quot;H1612&quot;, humidity=0, pressure=0.0, psource=&quot;H1612&quot;, rsource=&quot;H1612&quot;, station=&quot;zar&quot;, temperature=10.0, tsource=&quot;H1612&quot;, winddir=90, windgusts=10.3, windspeed=5.14, wsource=&quot;H1612&quot;&gt;
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/stacje/zar").toString();
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
        String mockResponse = createMockResponseWithDirection(0);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/stacje/zar").toString();
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

        String url = mockWebServer.url("/stacje/zar").toString();
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

        String url = mockWebServer.url("/stacje/zar").toString();
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

        String url = mockWebServer.url("/stacje/zar").toString();
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

        String url = mockWebServer.url("/stacje/zar").toString();
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

        String url = mockWebServer.url("/stacje/zar").toString();
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

        String url = mockWebServer.url("/stacje/zar").toString();
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

        String url = mockWebServer.url("/stacje/zar").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("NW");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleEdgeCaseWindDirections() {
        // Test 359 degrees (should be N, not NW)
        String mockResponse = createMockResponseWithDirection(359);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/stacje/zar").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("N");
                })
                .verifyComplete();
    }

    @Test
    void shouldParseNegativeTemperature() {
        String mockResponse = """
                <html>
                <body>
                #&lt;struct StationData id=13563918, epoch=1732809683, hsource=&quot;H1612&quot;, humidity=0, pressure=0.0, psource=&quot;H1612&quot;, rsource=&quot;H1612&quot;, station=&quot;zar&quot;, temperature=-15.5, tsource=&quot;H1612&quot;, winddir=180, windgusts=5.0, windspeed=3.0, wsource=&quot;H1612&quot;&gt;
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/stacje/zar").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.temp()).isEqualTo(-15);
                })
                .verifyComplete();
    }

    @Test
    void shouldParseZeroTemperature() {
        String mockResponse = """
                <html>
                <body>
                #&lt;struct StationData id=13563918, epoch=1732809683, hsource=&quot;H1612&quot;, humidity=0, pressure=0.0, psource=&quot;H1612&quot;, rsource=&quot;H1612&quot;, station=&quot;zar&quot;, temperature=0.0, tsource=&quot;H1612&quot;, winddir=180, windgusts=5.0, windspeed=3.0, wsource=&quot;H1612&quot;&gt;
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/stacje/zar").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.temp()).isEqualTo(0);
                })
                .verifyComplete();
    }

    @Test
    void shouldRoundDecimalValues() {
        String mockResponse = """
                <html>
                <body>
                #&lt;struct StationData id=13563918, epoch=1732809683, hsource=&quot;H1612&quot;, humidity=0, pressure=0.0, psource=&quot;H1612&quot;, rsource=&quot;H1612&quot;, station=&quot;zar&quot;, temperature=18.6, tsource=&quot;H1612&quot;, winddir=180, windgusts=4.63, windspeed=2.57, wsource=&quot;H1612&quot;&gt;
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/stacje/zar").toString();
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
    void shouldFormatTimestampCorrectly() {
        // Epoch 1732809683 = 2024-11-28 17:01:23 CET
        String mockResponse = """
                <html>
                <body>
                #&lt;struct StationData id=13563918, epoch=1732809683, hsource=&quot;H1612&quot;, humidity=0, pressure=0.0, psource=&quot;H1612&quot;, rsource=&quot;H1612&quot;, station=&quot;zar&quot;, temperature=10.0, tsource=&quot;H1612&quot;, winddir=180, windgusts=5.0, windspeed=3.0, wsource=&quot;H1612&quot;&gt;
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/stacje/zar").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.date()).isEqualTo("2024-11-28 17:01:23");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleHttpError() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500));

        String url = mockWebServer.url("/stacje/zar").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleMissingStationData() {
        String mockResponse = """
                <html>
                <body>
                <div>Some content without StationData</div>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/stacje/zar").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleIncompleteStationData() {
        String mockResponse = """
                <html>
                <body>
                #&lt;struct StationData id=13563918, epoch=1732809683&gt;
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/stacje/zar").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldReturnCorrectUrl() {
        assertThat(strategy.getUrl(1068590)).isEqualTo("https://pogoda.cc/pl/stacje/zar");
    }

    @Test
    void shouldParseDecimalWindSpeed() {
        String mockResponse = """
                <html>
                <body>
                #&lt;struct StationData id=13563918, epoch=1732809683, hsource=&quot;H1612&quot;, humidity=0, pressure=0.0, psource=&quot;H1612&quot;, rsource=&quot;H1612&quot;, station=&quot;zar&quot;, temperature=15.0, tsource=&quot;H1612&quot;, winddir=90, windgusts=7.25, windspeed=4.5, wsource=&quot;H1612&quot;&gt;
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/stacje/zar").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    // 4.5 m/s * 1.94384 = 8.75 knots, rounded to 9
                    assertThat(conditions.wind()).isEqualTo(9);
                    // 7.25 m/s * 1.94384 = 14.09 knots, rounded to 14
                    assertThat(conditions.gusts()).isEqualTo(14);
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleZeroWindSpeed() {
        String mockResponse = """
                <html>
                <body>
                #&lt;struct StationData id=13563918, epoch=1732809683, hsource=&quot;H1612&quot;, humidity=0, pressure=0.0, psource=&quot;H1612&quot;, rsource=&quot;H1612&quot;, station=&quot;zar&quot;, temperature=15.0, tsource=&quot;H1612&quot;, winddir=90, windgusts=0.0, windspeed=0.0, wsource=&quot;H1612&quot;&gt;
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/stacje/zar").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.wind()).isEqualTo(0);
                    assertThat(conditions.gusts()).isEqualTo(0);
                })
                .verifyComplete();
    }

    private String createMockResponseWithDirection(int degrees) {
        return """
                <html>
                <body>
                #&lt;struct StationData id=13563918, epoch=1732809683, hsource=&quot;H1612&quot;, humidity=0, pressure=0.0, psource=&quot;H1612&quot;, rsource=&quot;H1612&quot;, station=&quot;zar&quot;, temperature=10.0, tsource=&quot;H1612&quot;, winddir=%d, windgusts=5.0, windspeed=3.0, wsource=&quot;H1612&quot;&gt;
                </body>
                </html>
                """.formatted(degrees);
    }
}