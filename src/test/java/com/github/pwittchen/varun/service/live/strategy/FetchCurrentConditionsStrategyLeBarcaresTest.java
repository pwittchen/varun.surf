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

class FetchCurrentConditionsStrategyLeBarcaresTest {

    private static final int LE_BARCARES_WG_ID = 1146458;
    private MockWebServer mockWebServer;
    private FetchCurrentConditionsStrategyLeBarcares strategy;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        strategy = new FetchCurrentConditionsStrategyLeBarcares(new OkHttpClient());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void shouldReturnTrueForCorrectWgId() {
        assertThat(strategy.canProcess(LE_BARCARES_WG_ID)).isTrue();
    }

    @Test
    void shouldReturnFalseForOtherWgIds() {
        assertThat(strategy.canProcess(999999)).isFalse();
        assertThat(strategy.canProcess(48009)).isFalse();
        assertThat(strategy.canProcess(0)).isFalse();
    }

    @Test
    void shouldParseValidResponseWithCompleteData() {
        // Realistic mock response based on actual winds-up.com page structure
        String mockHtml = """
                <!DOCTYPE html>
                <html>
                <head><title>Wind Station</title></head>
                <body>
                <script>
                    var chart = {
                        series: [{
                            name: 'Wind',
                            data: [{x:1774682280000,y:31,o:"NO",color:"#EC6E0F",img:"//img.winds-up.com/maps/new/anemo_30-NO.gif",}]
                        }, {
                            name: 'Range',
                            data: [{x:1774682280000,low:28,high:35,},{x:1774682400000,low:25,high:36,}]
                        }]
                    };
                </script>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(mockHtml));

        String url = mockWebServer.url("/spot/58").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.date()).isEqualTo("2026-03-28 08:18:00");
                    assertThat(conditions.wind()).isEqualTo(31);
                    assertThat(conditions.gusts()).isEqualTo(35);
                    assertThat(conditions.direction()).isEqualTo("NW");
                    assertThat(conditions.temp()).isEqualTo(0); // No temperature data
                })
                .verifyComplete();
    }

    @Test
    void shouldParseResponseWithEastDirection() {
        String mockHtml = """
                <script>
                    data: [{x:1774682280000,y:25,o:"E",color:"#EC6E0F"}]
                    data: [{x:1774682280000,low:22,high:30,}]
                </script>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(mockHtml));

        String url = mockWebServer.url("/spot/58").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.wind()).isEqualTo(25);
                    assertThat(conditions.gusts()).isEqualTo(30);
                    assertThat(conditions.direction()).isEqualTo("E");
                })
                .verifyComplete();
    }

    @Test
    void shouldParseResponseWithSouthWestDirection() {
        String mockHtml = """
                <script>
                    data: [{x:1774682280000,y:18,o:"SO",img:"test.gif"}]
                    data: [{x:1774682280000,low:15,high:22,}]
                </script>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(mockHtml));

        String url = mockWebServer.url("/spot/58").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.wind()).isEqualTo(18);
                    assertThat(conditions.gusts()).isEqualTo(22);
                    assertThat(conditions.direction()).isEqualTo("SW");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleResponseWithoutGustData() {
        String mockHtml = """
                <script>
                    data: [{x:1774682280000,y:20,o:"N",color:"#EC6E0F"}]
                </script>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(mockHtml));

        String url = mockWebServer.url("/spot/58").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.wind()).isEqualTo(20);
                    assertThat(conditions.gusts()).isEqualTo(20); // Defaults to wind speed
                    assertThat(conditions.direction()).isEqualTo("N");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleHttpError() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        String url = mockWebServer.url("/spot/58").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                                throwable.getMessage().contains("Failed to fetch current conditions"))
                .verify();
    }

    @Test
    void shouldHandleNullResponseBody() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(""));

        String url = mockWebServer.url("/spot/58").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                                throwable.getMessage().contains("Current wind data not found"))
                .verify();
    }

    @Test
    void shouldHandleMissingWindData() {
        String mockHtml = """
                <html>
                <body>No wind data here</body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(mockHtml));

        String url = mockWebServer.url("/spot/58").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .expectErrorMatches(throwable ->
                        throwable instanceof RuntimeException &&
                                throwable.getMessage().contains("Current wind data not found"))
                .verify();
    }

    @Test
    void shouldNormalizeDirectionCorrectly() {
        // Test various direction formats
        String[][] testCases = {
                {"NO", "NW"},  // French Northwest
                {"SO", "SW"},  // French Southwest
                {"N", "N"},
                {"E", "E"},
                {"S", "S"},
                {"O", "W"},    // French West
                {"NE", "NE"},
                {"SE", "SE"}
        };

        for (String[] testCase : testCases) {
            String input = testCase[0];
            String expected = testCase[1];

            String mockHtml = String.format("""
                    <script>
                        data: [{x:1774682280000,y:20,o:"%s"}]
                        data: [{x:1774682280000,low:18,high:25,}]
                    </script>
                    """, input);

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setBody(mockHtml));

            String url = mockWebServer.url("/spot/58").toString();
            Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

            StepVerifier.create(result)
                    .assertNext(conditions -> {
                        assertThat(conditions.direction()).isEqualTo(expected);
                    })
                    .verifyComplete();
        }
    }

    @Test
    void shouldReturnCorrectUrl() {
        assertThat(strategy.getUrl(LE_BARCARES_WG_ID))
                .isEqualTo("https://m.winds-up.com/spot/58");
    }

    @Test
    void shouldFormatTimestampCorrectly() {
        // Test with known timestamp
        String mockHtml = """
                <script>
                    data: [{x:1706184600000,y:25,o:"N"}]
                    data: [{x:1706184600000,low:22,high:30,}]
                </script>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(mockHtml));

        String url = mockWebServer.url("/spot/58").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    // Timestamp 1706184600000 = 2024-01-25 13:10:00 CET
                    assertThat(conditions.date()).isEqualTo("2024-01-25 13:10:00");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleZeroWindSpeed() {
        String mockHtml = """
                <script>
                    data: [{x:1774682280000,y:0,o:"N"}]
                    data: [{x:1774682280000,low:0,high:0,}]
                </script>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(mockHtml));

        String url = mockWebServer.url("/spot/58").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.wind()).isEqualTo(0);
                    assertThat(conditions.gusts()).isEqualTo(0);
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleHighWindSpeed() {
        String mockHtml = """
                <script>
                    data: [{x:1774682280000,y:45,o:"NO"}]
                    data: [{x:1774682280000,low:40,high:50,}]
                </script>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(mockHtml));

        String url = mockWebServer.url("/spot/58").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.wind()).isEqualTo(45);
                    assertThat(conditions.gusts()).isEqualTo(50);
                    assertThat(conditions.direction()).isEqualTo("NW");
                })
                .verifyComplete();
    }
}