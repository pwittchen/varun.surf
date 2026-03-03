package com.github.pwittchen.varun.service.forecast;

import com.github.pwittchen.varun.model.forecast.Forecast;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IcmForecastVisionServiceTest {

    private MockWebServer mockWebServer;
    private IcmForecastVisionService service;
    private ChatClient chatClient;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.StreamResponseSpec streamResponseSpec;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        streamResponseSpec = mock(ChatClient.StreamResponseSpec.class);

        OkHttpClient httpClient = new OkHttpClient();
        Gson gson = new GsonBuilder().create();
        service = new IcmForecastVisionService(chatClient, httpClient, gson);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void shouldExtractForecastFromValidResponse() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("fake-image-bytes")
                .setResponseCode(200));

        String jsonResponse = """
                [
                  {"date":"Mon 03 Mar 2026 06:00","wind":12.5,"gusts":18.0,"direction":"NW","temp":5.0,"precipitation":0.0},
                  {"date":"Mon 03 Mar 2026 09:00","wind":15.0,"gusts":22.0,"direction":"W","temp":7.0,"precipitation":0.5}
                ]
                """;

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(any(java.util.function.Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.content()).thenReturn(Flux.just(jsonResponse));

        String url = mockWebServer.url("/meteogram.png").toString();
        Optional<List<Forecast>> result = service.extractForecastFromMeteogram(url);

        assertThat(result.isPresent()).isTrue();
        assertThat(result.get()).hasSize(2);
        assertThat(result.get().get(0).wind()).isEqualTo(12.5);
        assertThat(result.get().get(0).direction()).isEqualTo("NW");
        assertThat(result.get().get(1).date()).isEqualTo("Mon 03 Mar 2026 09:00");
    }

    @Test
    void shouldReturnEmptyWhenChatClientThrowsException() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("fake-image-bytes")
                .setResponseCode(200));

        when(chatClient.prompt()).thenThrow(new RuntimeException("API error"));

        String url = mockWebServer.url("/meteogram.png").toString();
        Optional<List<Forecast>> result = service.extractForecastFromMeteogram(url);

        assertThat(result.isPresent()).isFalse();
    }

    @Test
    void shouldReturnEmptyWhenResponseIsMalformedJson() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("fake-image-bytes")
                .setResponseCode(200));

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(any(java.util.function.Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.content()).thenReturn(Flux.just("not valid json at all"));

        String url = mockWebServer.url("/meteogram.png").toString();
        Optional<List<Forecast>> result = service.extractForecastFromMeteogram(url);

        assertThat(result.isPresent()).isFalse();
    }

    @Test
    void shouldReturnEmptyWhenImageDownloadFails() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        String url = mockWebServer.url("/meteogram.png").toString();
        Optional<List<Forecast>> result = service.extractForecastFromMeteogram(url);

        assertThat(result.isPresent()).isFalse();
    }

    @Test
    void shouldReturnEmptyWhenResponseIsNull() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("fake-image-bytes")
                .setResponseCode(200));

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(any(java.util.function.Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.content()).thenReturn(Flux.empty());

        String url = mockWebServer.url("/meteogram.png").toString();
        Optional<List<Forecast>> result = service.extractForecastFromMeteogram(url);

        assertThat(result.isPresent()).isFalse();
    }

    @Test
    void shouldReturnEmptyWhenResponseIsEmptyJsonArray() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("fake-image-bytes")
                .setResponseCode(200));

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(any(java.util.function.Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.content()).thenReturn(Flux.just("[]"));

        String url = mockWebServer.url("/meteogram.png").toString();
        Optional<List<Forecast>> result = service.extractForecastFromMeteogram(url);

        assertThat(result.isPresent()).isFalse();
    }
}
