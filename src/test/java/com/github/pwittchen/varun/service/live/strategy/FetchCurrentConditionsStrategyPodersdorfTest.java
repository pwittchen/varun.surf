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

class FetchCurrentConditionsStrategyPodersdorfTest {

    private MockWebServer mockWebServer;
    private FetchCurrentConditionsStrategyPodersdorf strategy;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        strategy = new FetchCurrentConditionsStrategyPodersdorf();
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
        assertThat(strategy.canProcess(126330)).isFalse();
        assertThat(strategy.canProcess(999999)).isFalse();
    }

    @Test
    void shouldParseValidKiteridersResponse() {
        String mockResponse = """
                <html>
                <body>
                <table>
                <tr><th>Header</th></tr>
                <tr><td>15.01.2025</td><td>14:30</td><td>SW</td><td>-</td><td>12.5kn</td><td>-</td><td>18.3kn</td><td>15.8 °C</td></tr>
                </table>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/weatherstat_kn.html").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions).isNotNull();
                    assertThat(conditions.date()).isEqualTo("15.01.2025 14:30");
                    assertThat(conditions.direction()).isEqualTo("SW");
                    assertThat(conditions.wind()).isEqualTo(13);
                    assertThat(conditions.gusts()).isEqualTo(18);
                    assertThat(conditions.temp()).isEqualTo(16);
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
                <tr><td>15.01.2025</td><td>14:30</td><td>NNE</td><td>-</td><td>10.0kn</td><td>-</td><td>15.0kn</td><td>20.0 °C</td></tr>
                </table>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/weatherstat_kn.html").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.direction()).isEqualTo("NE");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleHttpError() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500));

        String url = mockWebServer.url("/weatherstat_kn.html").toString();
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
                <tr><th>Header</th></tr>
                </table>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/weatherstat_kn.html").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldRoundDecimalValues() {
        String mockResponse = """
                <html>
                <body>
                <table>
                <tr><th>Header</th></tr>
                <tr><td>15.01.2025</td><td>14:30</td><td>N</td><td>-</td><td>12.8kn</td><td>-</td><td>18.4kn</td><td>20.6 °C</td></tr>
                </table>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/weatherstat_kn.html").toString();
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
    void shouldReturnCorrectUrl() {
        assertThat(strategy.getUrl(859182)).isEqualTo("https://www.kiteriders.at/wind/weatherstat_kn.html");
    }

    @Test
    void shouldHandleNonBreakingSpaces() {
        String mockResponse = """
                <html>
                <body>
                <table>
                <tr><th>Header</th></tr>
                <tr><td>15.01.2025&nbsp;</td><td>&nbsp;14:30&nbsp;</td><td>&nbsp;E&nbsp;</td><td>-</td><td>&nbsp;10kn&nbsp;</td><td>-</td><td>&nbsp;15kn&nbsp;</td><td>&nbsp;18.0 °C&nbsp;</td></tr>
                </table>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setResponseCode(200));

        String url = mockWebServer.url("/weatherstat_kn.html").toString();
        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url);

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions.date()).isEqualTo("15.01.2025 14:30");
                    assertThat(conditions.direction()).isEqualTo("E");
                    assertThat(conditions.wind()).isEqualTo(10);
                    assertThat(conditions.gusts()).isEqualTo(15);
                    assertThat(conditions.temp()).isEqualTo(18);
                })
                .verifyComplete();
    }
}