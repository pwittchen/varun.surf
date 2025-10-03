package com.github.pwittchen.varun.service;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

class CurrentConditionsServiceTest {

    private MockWebServer mockWebServer;
    private CurrentConditionsService service;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        service = new CurrentConditionsService();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void shouldParseForecastData() {
        String mockData = "29/09/25 23:08:31 10.1 74.9 5.9 4.7 7.0 65 0 0 1029.4 ENE 3 kts C hPa mm 346.88 0.4 0 0 0 10.1 74.9 7.1 0 12.9 23:36 9.7 2025-09-29 21:47:26 12.8 23:03 17.1 21:19 1029.5 22:54 1027.8 02:01 0 0 0 0 0 0 0 0 65 0 0 0 0 ENE 1718 m 10.1 12.56 0 0";

        mockWebServer.enqueue(new MockResponse().setBody(mockData).setResponseCode(200));

        String url = mockWebServer.url("/wiatrkadyny.txt").toString();

        StepVerifier
                .create(service.fetchWKCurrentConditions(url))
                .assertNext(conditions -> {
                    assertThat(conditions.date()).isEqualTo("29/09/25 23:08:31");
                    assertThat(conditions.temp()).isEqualTo(10);
                    assertThat(conditions.wind()).isEqualTo(7);
                    assertThat(conditions.direction()).isEqualTo("NE");
                    assertThat(conditions.gusts()).isEqualTo(13);
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleHttpError() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        String url = mockWebServer.url("/wiatrkadyny.txt").toString();

        StepVerifier
                .create(service.fetchWKCurrentConditions(url))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldParseKiteRidersAtForecast() {
        String mockHtml = """
                <!DOCTYPE html>
                <html>
                <body>
                <table>
                <tr><th>Date</th><th>Time</th><th>Dir</th><th>Bft Avg</th><th>kn Avg</th><th>Bft Gust</th><th>kn Gust</th><th>Temp</th></tr>
                <tr style="background:#3dfa8e;"><td style="background:#3dfa8e;">29/09/2025</td><td style="background:#3dfa8e;">23:14</td><td style="background:#3dfa8e;">NNW</td><td style="background:#3dfa8e;"><b> 3 Bft</b></td><td style="background:#3dfa8e;">&nbsp;<b> 10.5 kn</b>&nbsp;</td><td style="background:#3dfa8e;"> 4 Bft</td><td style="background:#3dfa8e;">&nbsp; 12.2 kn&nbsp;</td><td style="background:#3dfa8e;">11.2 °C</td><td style="background:#3dfa8e;"> 5.1 °C</td><td style="background:#3dfa8e;">1023 mbar</td><td style="background:#3dfa8e;">+0.68</td></tr>
                <tr><td>28/09/2025</td><td>22:14</td><td>N</td><td>2 Bft</td><td>8.0 kn</td><td>3 Bft</td><td>10.0 kn</td><td>10.0 °C</td></tr>
                </table>
                </body>
                </html>
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockHtml)
                .setResponseCode(200));

        String url = mockWebServer.url("/weatherstat_kn.html").toString();

        StepVerifier
                .create(service.fetchKRCurrentConditions(url))
                .assertNext(conditions -> {
                    assertThat(conditions.date()).isEqualTo("29/09/2025 23:14");
                    assertThat(conditions.temp()).isEqualTo(11);
                    assertThat(conditions.wind()).isEqualTo(11);
                    assertThat(conditions.direction()).isEqualTo("NW");
                    assertThat(conditions.gusts()).isEqualTo(12);
                })
                .verifyComplete();
    }

    @Test
    void shouldFetchCurrentConditionsForPolishStation() {
        StepVerifier
                .create(service.fetchCurrentConditions(126330))
                .assertNext(conditions -> {
                    assertThat(conditions).isNotNull();
                    assertThat(conditions.date()).isNotEmpty();
                    assertThat(conditions.direction()).isNotEmpty();
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyForUnknownStation() {
        StepVerifier
                .create(service.fetchCurrentConditions(999999))
                .verifyComplete();
    }

}