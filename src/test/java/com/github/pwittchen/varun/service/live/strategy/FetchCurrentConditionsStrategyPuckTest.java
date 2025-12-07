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

class FetchCurrentConditionsStrategyPuckTest {

    private MockWebServer mockWebServer;
    private FetchCurrentConditionsStrategyPuck strategy;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        strategy = new FetchCurrentConditionsStrategyPuck(new OkHttpClient(), new Gson());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void shouldReturnTrueForPuckWgId() {
        assertThat(strategy.canProcess(48009)).isTrue();
    }

    @Test
    void shouldReturnFalseForOtherWgIds() {
        assertThat(strategy.canProcess(126330)).isFalse();
        assertThat(strategy.canProcess(726)).isFalse();
        assertThat(strategy.canProcess(999999)).isFalse();
    }

    @Test
    void shouldParseValidJsonResponse() {
        String mockResponse = """
                {
                    "timeUTC": "2025,12,7,14,30,0",
                    "temp": "17.9",
                    "wspeed": "7.2",
                    "wgust": "11.3",
                    "bearing": "247"
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/puck/realtimegauges.txt").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions).isNotNull();
                    assertThat(conditions.date()).isEqualTo("2025-12-07 14:30:00");
                    assertThat(conditions.wind()).isEqualTo(7);
                    assertThat(conditions.gusts()).isEqualTo(11);
                    assertThat(conditions.direction()).isEqualTo("SW");
                    assertThat(conditions.temp()).isEqualTo(18);
                })
                .verifyComplete();
    }

    @Test
    void shouldConvertDegreesToCardinalDirectionNorth() {
        String mockResponse = createMockResponseWithDirection(0);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/puck/realtimegauges.txt").toString();
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

        String url = mockWebServer.url("/puck/realtimegauges.txt").toString();
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

        String url = mockWebServer.url("/puck/realtimegauges.txt").toString();
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

        String url = mockWebServer.url("/puck/realtimegauges.txt").toString();
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

        String url = mockWebServer.url("/puck/realtimegauges.txt").toString();
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

        String url = mockWebServer.url("/puck/realtimegauges.txt").toString();
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

        String url = mockWebServer.url("/puck/realtimegauges.txt").toString();
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

        String url = mockWebServer.url("/puck/realtimegauges.txt").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("NW");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleEdgeCaseWindDirection359() {
        String mockResponse = createMockResponseWithDirection(359);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/puck/realtimegauges.txt").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("N");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleZeroWindSpeed() {
        String mockResponse = """
                {
                    "timeUTC": "2025,12,7,14,30,0",
                    "temp": "15.0",
                    "wspeed": "0.0",
                    "wgust": "0.0",
                    "bearing": "90"
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/puck/realtimegauges.txt").toString();
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
        String mockResponse = """
                {
                    "timeUTC": "2025,12,7,14,30,0",
                    "temp": "-5.5",
                    "wspeed": "10.0",
                    "wgust": "15.0",
                    "bearing": "180"
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/puck/realtimegauges.txt").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.temp()).isEqualTo(-5);
                })
                .verifyComplete();
    }

    @Test
    void shouldRoundDecimalValues() {
        String mockResponse = """
                {
                    "timeUTC": "2025,12,7,14,30,0",
                    "temp": "18.6",
                    "wspeed": "12.4",
                    "wgust": "18.7",
                    "bearing": "180"
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/puck/realtimegauges.txt").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.wind()).isEqualTo(12);
                    assertThat(conditions.gusts()).isEqualTo(19);
                    assertThat(conditions.temp()).isEqualTo(19);
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleHttpError() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500));

        String url = mockWebServer.url("/puck/realtimegauges.txt").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldReturnCorrectUrl() {
        assertThat(strategy.getUrl(48009)).isEqualTo("https://www.wiatrkadyny.pl/puck/realtimegauges.txt");
    }

    @Test
    void shouldParseTimestampCorrectly() {
        String mockResponse = """
                {
                    "timeUTC": "2025,1,15,8,5,30",
                    "temp": "10.0",
                    "wspeed": "5.0",
                    "wgust": "8.0",
                    "bearing": "90"
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/puck/realtimegauges.txt").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.date()).isEqualTo("2025-01-15 08:05:30");
                })
                .verifyComplete();
    }

    private String createMockResponseWithDirection(int degrees) {
        return """
                {
                    "timeUTC": "2025,12,7,14,30,0",
                    "temp": "15.0",
                    "wspeed": "10.0",
                    "wgust": "15.0",
                    "bearing": "%d"
                }
                """.formatted(degrees);
    }
}
