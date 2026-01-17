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

class FetchCurrentConditionsStrategyMietkowTest {

    private MockWebServer mockWebServer;
    private FetchCurrentConditionsStrategyMietkow strategy;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        strategy = new FetchCurrentConditionsStrategyMietkow(new OkHttpClient());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void shouldReturnTrueForMietkowWgId() {
        assertThat(strategy.canProcess(304)).isTrue();
    }

    @Test
    void shouldReturnFalseForOtherWgIds() {
        assertThat(strategy.canProcess(999999)).isFalse();
        assertThat(strategy.canProcess(48009)).isFalse();
    }

    @Test
    void shouldParseValidMietkowResponse() {
        String mockResponse = """
                <!DOCTYPE html>
                <html lang="pl">
                  <head>
                    <meta charset="UTF-8">
                    <title>Zalew Mietkowski, Polska</title>
                  </head>
                  <body>
                    <div id='current_widget' class="widget">
                      <div class="widget_title" style="white-space:nowrap;">
                        Ostatni Odczyt :: &nbsp; <span id="lastupdate_value">17-sty-2026 17:22:24</span>
                      </div>
                      <table>
                        <tbody>
                          <tr>
                            <td class="label" style="font-size:110%;">Wiatr</td>
                            <td class="data" style="font-size:110%;">12 knt (Max 18)</td>
                          </tr>
                          <tr><td class="label">Temperatura</td><td class="data">15,7&#176;C</td></tr>
                        </tbody>
                      </table>
                      <img src="arrow.png"
                          style="transform:rotate(calc(225.0deg - 180deg)) scale(1.0);"
                      />
                      <img src="arrow.png"
                          style="transform:rotate(calc(180.5deg - 180deg)) scale(0.5);"
                      />
                    </div>
                  </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/inx.html").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions).isNotNull();
                    assertThat(conditions.wind()).isEqualTo(12);
                    assertThat(conditions.gusts()).isEqualTo(18);
                    assertThat(conditions.direction()).isEqualTo("SW"); // 225 degrees
                    assertThat(conditions.temp()).isEqualTo(16);
                    assertThat(conditions.date()).isEqualTo("2026-01-17 17:22:24");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleZeroWindSpeed() {
        String mockResponse = """
                <!DOCTYPE html>
                <html>
                  <body>
                    <span id="lastupdate_value">15-sty-2026 12:00:00</span>
                    <td class="label" style="font-size:110%;">Wiatr</td>
                    <td class="data" style="font-size:110%;">0 knt (Max 0)</td>
                    <td class="label">Temperatura</td><td class="data">5,2&#176;C</td>
                    <img src="arrow.png" style="transform:rotate(calc(0.0deg - 180deg));" />
                  </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/inx.html").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.wind()).isEqualTo(0);
                    assertThat(conditions.gusts()).isEqualTo(0);
                    assertThat(conditions.direction()).isEqualTo("N"); // 0 degrees
                    assertThat(conditions.temp()).isEqualTo(5);
                })
                .verifyComplete();
    }

    @Test
    void shouldConvertWindDirectionCorrectly() {
        String mockResponse = """
                <!DOCTYPE html>
                <html>
                  <body>
                    <span id="lastupdate_value">15-sty-2026 12:00:00</span>
                    <td class="label">Wiatr</td>
                    <td class="data">10 knt (Max 15)</td>
                    <td class="label">Temperatura</td><td class="data">10,0&#176;C</td>
                    <img src="arrow.png" style="transform:rotate(calc(90.0deg - 180deg));" />
                  </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/inx.html").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("E"); // 90 degrees
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleNegativeTemperature() {
        String mockResponse = """
                <!DOCTYPE html>
                <html>
                  <body>
                    <span id="lastupdate_value">15-sty-2026 08:00:00</span>
                    <td class="label">Wiatr</td>
                    <td class="data">8 knt (Max 12)</td>
                    <td class="label">Temperatura</td><td class="data">-5,3&#176;C</td>
                    <img src="arrow.png" style="transform:rotate(calc(45.0deg - 180deg));" />
                  </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/inx.html").toString();
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
                <!DOCTYPE html>
                <html>
                  <body>
                    <span id="lastupdate_value">15-sty-2026 14:30:00</span>
                    <td class="label">Wiatr</td>
                    <td class="data">14 knt (Max 22)</td>
                    <td class="label">Temperatura</td><td class="data">18,7&#176;C</td>
                    <img src="arrow.png" style="transform:rotate(calc(267.8deg - 180deg));" />
                  </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/inx.html").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.temp()).isEqualTo(19);
                    assertThat(conditions.direction()).isEqualTo("W"); // 268 degrees rounds to 270 (W)
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleHttpError() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500));

        String url = mockWebServer.url("/inx.html").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleMissingWindData() {
        String mockResponse = """
                <!DOCTYPE html>
                <html>
                  <body>
                    <span id="lastupdate_value">15-sty-2026 12:00:00</span>
                    <td class="label">Temperatura</td><td class="data">10,0&#176;C</td>
                  </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/inx.html").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleMissingTemperature() {
        String mockResponse = """
                <!DOCTYPE html>
                <html>
                  <body>
                    <span id="lastupdate_value">15-sty-2026 12:00:00</span>
                    <td class="label">Wiatr</td>
                    <td class="data">10 knt (Max 15)</td>
                    <img src="arrow.png" style="transform:rotate(calc(90.0deg - 180deg));" />
                  </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/inx.html").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleMissingWindDirection() {
        String mockResponse = """
                <!DOCTYPE html>
                <html>
                  <body>
                    <span id="lastupdate_value">15-sty-2026 12:00:00</span>
                    <td class="label">Wiatr</td>
                    <td class="data">10 knt (Max 15)</td>
                    <td class="label">Temperatura</td><td class="data">10,0&#176;C</td>
                  </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/inx.html").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleMissingTimestampWithFallback() {
        String mockResponse = """
                <!DOCTYPE html>
                <html>
                  <body>
                    <td class="label">Wiatr</td>
                    <td class="data">10 knt (Max 15)</td>
                    <td class="label">Temperatura</td><td class="data">10,0&#176;C</td>
                    <img src="arrow.png" style="transform:rotate(calc(90.0deg - 180deg));" />
                  </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/inx.html").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions).isNotNull();
                    // Should use current timestamp as fallback
                    assertThat(conditions.date()).isNotEmpty();
                    assertThat(conditions.date()).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnCorrectUrl() {
        assertThat(strategy.getUrl(304)).isEqualTo("https://frog01-21064.wykr.es/weewx/inx.html");
    }

    @Test
    void shouldConvertDirectionNorthCorrectly() {
        testDirection(0.0, "N");
    }

    @Test
    void shouldConvertDirectionNorthEastCorrectly() {
        testDirection(45.0, "NE");
    }

    @Test
    void shouldConvertDirectionEastCorrectly() {
        testDirection(90.0, "E");
    }

    @Test
    void shouldConvertDirectionSouthEastCorrectly() {
        testDirection(135.0, "SE");
    }

    @Test
    void shouldConvertDirectionSouthCorrectly() {
        testDirection(180.0, "S");
    }

    @Test
    void shouldConvertDirectionSouthWestCorrectly() {
        testDirection(225.0, "SW");
    }

    @Test
    void shouldConvertDirectionWestCorrectly() {
        testDirection(270.0, "W");
    }

    @Test
    void shouldConvertDirectionNorthWestCorrectly() {
        testDirection(315.0, "NW");
    }

    private void testDirection(double degrees, String expectedCardinal) {
        String mockResponse = String.format(java.util.Locale.US, """
                <!DOCTYPE html>
                <html>
                  <body>
                    <span id="lastupdate_value">15-sty-2026 12:00:00</span>
                    <td class="label">Wiatr</td>
                    <td class="data">10 knt (Max 15)</td>
                    <td class="label">Temperatura</td><td class="data">10,0&#176;C</td>
                    <img src="arrow.png" style="transform:rotate(calc(%.1fdeg - 180deg));" />
                  </body>
                </html>
                """, degrees);

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/inx.html").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo(expectedCardinal);
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleNullResponseBody() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(""));

        String url = mockWebServer.url("/inx.html").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandlePolishMonthNames() {
        // Test different Polish month abbreviations
        String[] monthTests = {
            "15-sty-2026 12:00:00", // styczeń (January)
            "15-lut-2026 12:00:00", // luty (February)
            "15-mar-2026 12:00:00", // marzec (March)
            "15-kwi-2026 12:00:00", // kwiecień (April)
            "15-maj-2026 12:00:00", // maj (May)
            "15-cze-2026 12:00:00", // czerwiec (June)
            "15-lip-2026 12:00:00", // lipiec (July)
            "15-sie-2026 12:00:00", // sierpień (August)
            "15-wrz-2026 12:00:00", // wrzesień (September)
            "15-paź-2026 12:00:00", // październik (October)
            "15-lis-2026 12:00:00", // listopad (November)
            "15-gru-2026 12:00:00"  // grudzień (December)
        };

        for (String dateStr : monthTests) {
            String mockResponse = String.format(java.util.Locale.US, """
                    <!DOCTYPE html>
                    <html>
                      <body>
                        <span id="lastupdate_value">%s</span>
                        <td class="label">Wiatr</td>
                        <td class="data">10 knt (Max 15)</td>
                        <td class="label">Temperatura</td><td class="data">10,0&#176;C</td>
                        <img src="arrow.png" style="transform:rotate(calc(90.0deg - 180deg));" />
                      </body>
                    </html>
                    """, dateStr);

            mockWebServer.enqueue(new MockResponse()
                    .setBody(mockResponse)
                    .setResponseCode(200));

            String url = mockWebServer.url("/inx.html").toString();
            Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

            StepVerifier.create(result)
                    .assertNext(conditions -> {
                        assertThat(conditions).isNotNull();
                        // Should successfully parse and format the timestamp
                        assertThat(conditions.date()).matches("\\d{4}-\\d{2}-15 12:00:00");
                    })
                    .verifyComplete();
        }
    }
}