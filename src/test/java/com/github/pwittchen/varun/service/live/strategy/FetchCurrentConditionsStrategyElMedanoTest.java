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
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static com.google.common.truth.Truth.assertThat;

class FetchCurrentConditionsStrategyElMedanoTest {

    private MockWebServer mockWebServer;
    private FetchCurrentConditionsStrategyElMedano strategy;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        strategy = new FetchCurrentConditionsStrategyElMedano(new OkHttpClient());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void shouldReturnTrueForElMedanoWgId() {
        assertThat(strategy.canProcess(207008)).isTrue();
    }

    @Test
    void shouldReturnFalseForOtherWgIds() {
        assertThat(strategy.canProcess(859182)).isFalse();
        assertThat(strategy.canProcess(999999)).isFalse();
    }

    @Test
    void shouldParseValidBergfexResponse() {
        String mockResponse = """
                <html>
                <body>
                <table>
                <tr>
                    <th>time</th>
                    <th>wind</th>
                    <th colspan="2">gusts</th>
                    <th>temperature</th>
                </tr>
                <tr >
                    <td>15:45</td>
                    <td class="bft2 knt7">
                        <strong>7 kts</strong> <span style="min-width: 50px; display: inline-block;">(2 Bft<span class="smaller mobile-hidden">, 13 km/h</span>)</span>
                    </td>
                    <td class="bft3 knt10" style="width: 0px;"></td>
                    <td>
                        <strong>10 kts</strong> <span style="min-width: 50px; display: inline-block;">(3 Bft<span class="smaller mobile-hidden">, 19 km/h</span>)</span>
                    </td>
                    <td>
                        <div style="position: relative; text-align: center;">
                            <strong><span title="231">SW</span></strong>
                            <div style="position: absolute; top: 2px; right: -12px;" class="mobile-hidden direction direction2 direction-sw"></div>
                        </div>
                    </td>
                    <td class="mobile-hidden">18.8 &deg;C</td>
                </tr>
                </table>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/wetterstation/").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        String expectedDate = LocalDate.now(ZoneId.of("Atlantic/Canary"))
                .atTime(LocalTime.of(15, 45))
                .atZone(ZoneId.of("Atlantic/Canary"))
                .withZoneSameInstant(ZoneId.of("Europe/Madrid"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions).isNotNull();
                    assertThat(conditions.date()).isEqualTo(expectedDate);
                    assertThat(conditions.direction()).isEqualTo("SW");
                    assertThat(conditions.wind()).isEqualTo(7);
                    assertThat(conditions.gusts()).isEqualTo(10);
                    assertThat(conditions.temp()).isEqualTo(19);
                })
                .verifyComplete();
    }

    @Test
    void shouldNormalizeWindDirection() {
        String mockResponse = """
                <html>
                <body>
                <table>
                <tr><th>Header</th></tr>
                <tr >
                    <td>15:40</td>
                    <td class="bft2 knt6">
                        <strong>6 kts</strong>
                    </td>
                    <td class="bft4 knt12" style="width: 0px;"></td>
                    <td>
                        <strong>12 kts</strong>
                    </td>
                    <td>
                        <div style="position: relative; text-align: center;">
                            <strong><span title="239">WSW</span></strong>
                        </div>
                    </td>
                    <td class="mobile-hidden">18.9 &deg;C</td>
                </tr>
                </table>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/wetterstation/").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("SW");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleNorthDirection() {
        String mockResponse = """
                <html>
                <body>
                <table>
                <tr><th>Header</th></tr>
                <tr >
                    <td>14:30</td>
                    <td class="bft3 knt8">
                        <strong>8 kts</strong>
                    </td>
                    <td class="bft4 knt11" style="width: 0px;"></td>
                    <td>
                        <strong>11 kts</strong>
                    </td>
                    <td>
                        <div style="position: relative; text-align: center;">
                            <strong><span title="5">N</span></strong>
                        </div>
                    </td>
                    <td class="mobile-hidden">20.5 &deg;C</td>
                </tr>
                </table>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/wetterstation/").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("N");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleHttpError() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500));

        String url = mockWebServer.url("/wetterstation/").toString();
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

        String url = mockWebServer.url("/wetterstation/").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleNoDataRow() {
        String mockResponse = """
                <html>
                <body>
                <table>
                <tr>
                    <th>time</th>
                    <th>wind</th>
                </tr>
                </table>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/wetterstation/").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldRoundDecimalTemperature() {
        String mockResponse = """
                <html>
                <body>
                <table>
                <tr><th>Header</th></tr>
                <tr >
                    <td>16:00</td>
                    <td class="bft2 knt5">
                        <strong>5 kts</strong>
                    </td>
                    <td class="bft3 knt9" style="width: 0px;"></td>
                    <td>
                        <strong>9 kts</strong>
                    </td>
                    <td>
                        <div style="position: relative; text-align: center;">
                            <strong><span title="180">S</span></strong>
                        </div>
                    </td>
                    <td class="mobile-hidden">22.4 &deg;C</td>
                </tr>
                </table>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/wetterstation/").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.temp()).isEqualTo(22);
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleHighWindSpeeds() {
        String mockResponse = """
                <html>
                <body>
                <table>
                <tr><th>Header</th></tr>
                <tr >
                    <td>12:30</td>
                    <td class="bft6 knt25">
                        <strong>25 kts</strong>
                    </td>
                    <td class="bft7 knt32" style="width: 0px;"></td>
                    <td>
                        <strong>32 kts</strong>
                    </td>
                    <td>
                        <div style="position: relative; text-align: center;">
                            <strong><span title="45">NE</span></strong>
                        </div>
                    </td>
                    <td class="mobile-hidden">19.2 &deg;C</td>
                </tr>
                </table>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/wetterstation/").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.wind()).isEqualTo(25);
                    assertThat(conditions.gusts()).isEqualTo(32);
                    assertThat(conditions.direction()).isEqualTo("NE");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleEastDirection() {
        String mockResponse = """
                <html>
                <body>
                <table>
                <tr><th>Header</th></tr>
                <tr >
                    <td>11:15</td>
                    <td class="bft4 knt15">
                        <strong>15 kts</strong>
                    </td>
                    <td class="bft5 knt20" style="width: 0px;"></td>
                    <td>
                        <strong>20 kts</strong>
                    </td>
                    <td>
                        <div style="position: relative; text-align: center;">
                            <strong><span title="90">E</span></strong>
                        </div>
                    </td>
                    <td class="mobile-hidden">21.0 &deg;C</td>
                </tr>
                </table>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/wetterstation/").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("E");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnCorrectUrl() {
        assertThat(strategy.getUrl(207008)).isEqualTo("https://cabezo.bergfex.at/wetterstation/");
    }

    @Test
    void shouldHandleMorningTime() {
        String mockResponse = """
                <html>
                <body>
                <table>
                <tr><th>Header</th></tr>
                <tr >
                    <td>09:15</td>
                    <td class="bft1 knt3">
                        <strong>3 kts</strong>
                    </td>
                    <td class="bft2 knt5" style="width: 0px;"></td>
                    <td>
                        <strong>5 kts</strong>
                    </td>
                    <td>
                        <div style="position: relative; text-align: center;">
                            <strong><span title="270">W</span></strong>
                        </div>
                    </td>
                    <td class="mobile-hidden">17.5 &deg;C</td>
                </tr>
                </table>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/wetterstation/").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        String expectedDate = LocalDate.now(ZoneId.of("Atlantic/Canary"))
                .atTime(LocalTime.of(9, 15))
                .atZone(ZoneId.of("Atlantic/Canary"))
                .withZoneSameInstant(ZoneId.of("Europe/Madrid"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.date()).isEqualTo(expectedDate);
                    assertThat(conditions.wind()).isEqualTo(3);
                    assertThat(conditions.gusts()).isEqualTo(5);
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleSEDirection() {
        String mockResponse = """
                <html>
                <body>
                <table>
                <tr><th>Header</th></tr>
                <tr >
                    <td>13:20</td>
                    <td class="bft3 knt10">
                        <strong>10 kts</strong>
                    </td>
                    <td class="bft4 knt13" style="width: 0px;"></td>
                    <td>
                        <strong>13 kts</strong>
                    </td>
                    <td>
                        <div style="position: relative; text-align: center;">
                            <strong><span title="135">SE</span></strong>
                        </div>
                    </td>
                    <td class="mobile-hidden">20.0 &deg;C</td>
                </tr>
                </table>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/wetterstation/").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("SE");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleNWDirection() {
        String mockResponse = """
                <html>
                <body>
                <table>
                <tr><th>Header</th></tr>
                <tr >
                    <td>10:05</td>
                    <td class="bft2 knt6">
                        <strong>6 kts</strong>
                    </td>
                    <td class="bft3 knt8" style="width: 0px;"></td>
                    <td>
                        <strong>8 kts</strong>
                    </td>
                    <td>
                        <div style="position: relative; text-align: center;">
                            <strong><span title="315">NW</span></strong>
                        </div>
                    </td>
                    <td class="mobile-hidden">18.0 &deg;C</td>
                </tr>
                </table>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/wetterstation/").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("NW");
                })
                .verifyComplete();
    }

    @Test
    void shouldSkipRowsWithEmptyDirection() {
        // Real scenario: first rows may have empty direction (e.g., title="-40" with empty span)
        // The strategy should skip these and find the first row with valid direction
        String mockResponse = """
                <html>
                <body>
                <table>
                <tr><th>Header</th></tr>
                <tr >
                    <td>17:30</td>
                    <td class="bft0 knt0">
                        <strong>0 kts</strong>
                    </td>
                    <td class="bft0 knt0" style="width: 0px;"></td>
                    <td>
                        <strong>0 kts</strong>
                    </td>
                    <td>
                        <div style="position: relative; text-align: center;">
                            <strong><span title="-40"></span></strong>
                        </div>
                    </td>
                    <td class="mobile-hidden">54.6 &deg;C</td>
                </tr>
                <tr >
                    <td>17:25</td>
                    <td class="bft0 knt0">
                        <strong>0 kts</strong>
                    </td>
                    <td class="bft0 knt0" style="width: 0px;"></td>
                    <td>
                        <strong>0 kts</strong>
                    </td>
                    <td>
                        <div style="position: relative; text-align: center;">
                            <strong><span title="-40"></span></strong>
                        </div>
                    </td>
                    <td class="mobile-hidden">54.6 &deg;C</td>
                </tr>
                <tr >
                    <td>17:20</td>
                    <td class="bft3 knt8">
                        <strong>8 kts</strong>
                    </td>
                    <td class="bft4 knt12" style="width: 0px;"></td>
                    <td>
                        <strong>12 kts</strong>
                    </td>
                    <td>
                        <div style="position: relative; text-align: center;">
                            <strong><span title="96">E</span></strong>
                        </div>
                    </td>
                    <td class="mobile-hidden">22.6 &deg;C</td>
                </tr>
                </table>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/wetterstation/").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        String expectedDate = LocalDate.now(ZoneId.of("Atlantic/Canary"))
                .atTime(LocalTime.of(17, 20))
                .atZone(ZoneId.of("Atlantic/Canary"))
                .withZoneSameInstant(ZoneId.of("Europe/Madrid"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    // Should skip the first two rows with empty direction and use the third row
                    assertThat(conditions.date()).isEqualTo(expectedDate);
                    assertThat(conditions.direction()).isEqualTo("E");
                    assertThat(conditions.wind()).isEqualTo(8);
                    assertThat(conditions.gusts()).isEqualTo(12);
                    assertThat(conditions.temp()).isEqualTo(23);
                })
                .verifyComplete();
    }
}
