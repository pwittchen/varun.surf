package com.github.pwittchen.varun.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

class HttpClientMetricsEventListenerTest {

    private MockWebServer server;
    private MeterRegistry registry;
    private OkHttpClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        registry = new SimpleMeterRegistry();
        HttpClientMetricsEventListener listener = new HttpClientMetricsEventListener(registry);
        client = new OkHttpClient.Builder().eventListenerFactory(_ -> listener).build();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void shouldCountSuccessfulResponseAsSuccess() throws IOException {
        server.enqueue(new MockResponse().setBody("ok"));

        execute();

        assertThat(count("varun.http.client.requests.success")).isEqualTo(1.0);
        assertThat(count("varun.http.client.requests.failed")).isEqualTo(0.0);
    }

    @Test
    void shouldCountForbiddenResponseAsFailed() throws IOException {
        server.enqueue(new MockResponse().setResponseCode(403));

        execute();

        assertThat(count("varun.http.client.requests.success")).isEqualTo(0.0);
        assertThat(registry.find("varun.http.client.requests.failed").tag("exception", "HTTP 403").counter())
                .isNotNull();
        assertThat(count("varun.http.client.requests.failed")).isEqualTo(1.0);
    }

    @Test
    void shouldCountTooManyRequestsResponseAsFailed() throws IOException {
        server.enqueue(new MockResponse().setResponseCode(429));

        execute();

        assertThat(count("varun.http.client.requests.success")).isEqualTo(0.0);
        assertThat(count("varun.http.client.requests.failed")).isEqualTo(1.0);
    }

    @Test
    void shouldNotCarryRefusalOverToTheNextRequest() throws IOException {
        server.enqueue(new MockResponse().setResponseCode(403));
        server.enqueue(new MockResponse().setBody("ok"));

        execute();
        execute();

        assertThat(count("varun.http.client.requests.success")).isEqualTo(1.0);
        assertThat(count("varun.http.client.requests.failed")).isEqualTo(1.0);
    }

    private void execute() throws IOException {
        try (Response response = client.newCall(new Request.Builder().url(server.url("/")).build()).execute()) {
            response.body().string();
        }
    }

    private double count(String name) {
        return registry.find(name).counters().stream().mapToDouble(Counter::count).sum();
    }
}
