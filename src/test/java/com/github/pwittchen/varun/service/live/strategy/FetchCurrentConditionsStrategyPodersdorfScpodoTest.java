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

class FetchCurrentConditionsStrategyPodersdorfScpodoTest {

    private MockWebServer mockWebServer;
    private FetchCurrentConditionsStrategyPodersdorfScpodo strategy;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        strategy = new FetchCurrentConditionsStrategyPodersdorfScpodo(new OkHttpClient(), new Gson());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void shouldReturnTrueForPodersdorfWgId() {
        assertThat(strategy.canProcess(859182)).isTrue();
    }

    @Test
    void shouldReturnFalseForOtherWgIds() {
        assertThat(strategy.canProcess(48009)).isFalse();
        assertThat(strategy.canProcess(999999)).isFalse();
        assertThat(strategy.canProcess(126330)).isFalse();
    }

    @Test
    void shouldReturnTrueForIsFallbackStation() {
        assertThat(strategy.isFallbackStation()).isTrue();
    }

    @Test
    void shouldParseValidJsonResponse() {
        String mockResponse = """
                [
                  {
                    "Date":"27.02.26",
                    "Time":"14:30",
                    "windDirCurDe":"OSO",
                    "windSpeedCur":"12.5",
                    "windSpeedGusts":"18.3",
                    "tempOutCur":"15.8",
                    "tempCurWindchill":"14.2",
                    "DateTime":"2026-02-27 14:30:00"
                  },
                  {
                    "Date":"27.02.26",
                    "Time":"14:29",
                    "windDirCurDe":"OSO",
                    "windSpeedCur":"11.0",
                    "windSpeedGusts":"17.0",
                    "tempOutCur":"15.5",
                    "tempCurWindchill":"14.0",
                    "DateTime":"2026-02-27 14:29:00"
                  }
                ]
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/wind.php").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions).isNotNull();
                    assertThat(conditions.date()).isEqualTo("2026-02-27 14:30:00");
                    assertThat(conditions.direction()).isEqualTo("SE");
                    assertThat(conditions.wind()).isEqualTo(13);
                    assertThat(conditions.gusts()).isEqualTo(18);
                    assertThat(conditions.temp()).isEqualTo(16);
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleGermanDirectionAbbreviations() {
        String mockResponse = """
                [
                  {
                    "Date":"27.02.26",
                    "Time":"14:30",
                    "windDirCurDe":"O",
                    "windSpeedCur":"10.0",
                    "windSpeedGusts":"15.0",
                    "tempOutCur":"20.0",
                    "tempCurWindchill":"18.5",
                    "DateTime":"2026-02-27 14:30:00"
                  }
                ]
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/wind.php").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("E");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleNOAsNorthEast() {
        String mockResponse = """
                [
                  {
                    "Date":"27.02.26",
                    "Time":"14:30",
                    "windDirCurDe":"NO",
                    "windSpeedCur":"10.0",
                    "windSpeedGusts":"15.0",
                    "tempOutCur":"20.0",
                    "tempCurWindchill":"18.5",
                    "DateTime":"2026-02-27 14:30:00"
                  }
                ]
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/wind.php").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("NE");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleSOAsSouthEast() {
        String mockResponse = """
                [
                  {
                    "Date":"27.02.26",
                    "Time":"14:30",
                    "windDirCurDe":"SO",
                    "windSpeedCur":"10.0",
                    "windSpeedGusts":"15.0",
                    "tempOutCur":"20.0",
                    "tempCurWindchill":"18.5",
                    "DateTime":"2026-02-27 14:30:00"
                  }
                ]
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/wind.php").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("SE");
                })
                .verifyComplete();
    }

    @Test
    void shouldRoundDecimalValues() {
        String mockResponse = """
                [
                  {
                    "Date":"27.02.26",
                    "Time":"14:30",
                    "windDirCurDe":"N",
                    "windSpeedCur":"12.8",
                    "windSpeedGusts":"18.4",
                    "tempOutCur":"20.6",
                    "tempCurWindchill":"19.2",
                    "DateTime":"2026-02-27 14:30:00"
                  }
                ]
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/wind.php").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.wind()).isEqualTo(13);
                    assertThat(conditions.gusts()).isEqualTo(18);
                    assertThat(conditions.temp()).isEqualTo(21);
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleHttpError() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500));

        String url = mockWebServer.url("/wind.php").toString();
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

        String url = mockWebServer.url("/wind.php").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleEmptyJsonArray() {
        String mockResponse = "[]";

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/wind.php").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleMalformedJson() {
        String mockResponse = "{invalid json";

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/wind.php").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .expectError()
                .verify();
    }

    @Test
    void shouldReturnCorrectUrl() {
        assertThat(strategy.getUrl(859182)).isEqualTo("https://scpodo.at/wind.php");
    }

    @Test
    void shouldHandleAllGermanDirections() {
        String[] germanDirections = {"N", "NO", "O", "SO", "S", "SW", "W", "NW"};
        String[] expectedEnglish = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

        for (int i = 0; i < germanDirections.length; i++) {
            String mockResponse = String.format("""
                    [
                      {
                        "Date":"27.02.26",
                        "Time":"14:30",
                        "windDirCurDe":"%s",
                        "windSpeedCur":"10.0",
                        "windSpeedGusts":"15.0",
                        "tempOutCur":"20.0",
                        "tempCurWindchill":"18.5",
                        "DateTime":"2026-02-27 14:30:00"
                      }
                    ]
                    """, germanDirections[i]);

            mockWebServer.enqueue(new MockResponse()
                    .setBody(mockResponse)
                    .setResponseCode(200));

            String url = mockWebServer.url("/wind.php").toString();
            Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

            String expectedDirection = expectedEnglish[i];
            StepVerifier.create(result)
                    .assertNext(conditions -> {
                        assertThat(conditions.direction()).isEqualTo(expectedDirection);
                    })
                    .verifyComplete();
        }
    }

    @Test
    void shouldSelectMostRecentReading() {
        String mockResponse = """
                [
                  {
                    "Date":"27.02.26",
                    "Time":"14:32",
                    "windDirCurDe":"W",
                    "windSpeedCur":"20.0",
                    "windSpeedGusts":"25.0",
                    "tempOutCur":"18.0",
                    "tempCurWindchill":"16.0",
                    "DateTime":"2026-02-27 14:32:00"
                  },
                  {
                    "Date":"27.02.26",
                    "Time":"14:31",
                    "windDirCurDe":"N",
                    "windSpeedCur":"15.0",
                    "windSpeedGusts":"20.0",
                    "tempOutCur":"17.0",
                    "tempCurWindchill":"15.0",
                    "DateTime":"2026-02-27 14:31:00"
                  },
                  {
                    "Date":"27.02.26",
                    "Time":"14:30",
                    "windDirCurDe":"E",
                    "windSpeedCur":"10.0",
                    "windSpeedGusts":"15.0",
                    "tempOutCur":"16.0",
                    "tempCurWindchill":"14.0",
                    "DateTime":"2026-02-27 14:30:00"
                  }
                ]
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/wind.php").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.date()).isEqualTo("2026-02-27 14:32:00");
                    assertThat(conditions.direction()).isEqualTo("W");
                    assertThat(conditions.wind()).isEqualTo(20);
                    assertThat(conditions.gusts()).isEqualTo(25);
                    assertThat(conditions.temp()).isEqualTo(18);
                })
                .verifyComplete();
    }
}
