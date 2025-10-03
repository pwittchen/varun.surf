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
                .create(service.fetchWiatrKadynyForecast(url))
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
                .create(service.fetchWiatrKadynyForecast(url))
                .expectError(RuntimeException.class)
                .verify();
    }

}