package com.github.pwittchen.varun.service;

import com.github.pwittchen.varun.model.forecast.Forecast;
import com.github.pwittchen.varun.model.spot.Spot;
import com.github.pwittchen.varun.model.spot.SpotInfo;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.StreamResponseSpec streamSpec;

    private Gson gson;
    private AiService aiService;

    @BeforeEach
    void setUp() {
        gson = new Gson();
        aiService = new AiService(chatClient, gson);
    }

    @Test
    void shouldReturnEmptyWhenSpotNameIsEmpty() {
        // given
        var spot = createSpot("", "Poland", List.of(createForecast()));

        // when
        var result = aiService.fetchAiAnalysis(spot);

        // then
        StepVerifier.create(result)
                .expectNextCount(0)
                .verifyComplete();

        verify(chatClient, never()).prompt();
    }

    @Test
    void shouldReturnEmptyWhenCountryIsEmpty() {
        // given
        var spot = createSpot("Hel", "", List.of(createForecast()));

        // when
        var result = aiService.fetchAiAnalysis(spot);

        // then
        StepVerifier.create(result)
                .expectNextCount(0)
                .verifyComplete();

        verify(chatClient, never()).prompt();
    }

    @Test
    void shouldReturnEmptyWhenForecastIsEmpty() {
        // given
        var spot = createSpot("Hel", "Poland", List.of());

        // when
        var result = aiService.fetchAiAnalysis(spot);

        // then
        StepVerifier.create(result)
                .expectNextCount(0)
                .verifyComplete();

        verify(chatClient, never()).prompt();
    }

    @Test
    void shouldReturnEmptyWhenAllFieldsAreEmpty() {
        // given
        var spot = createSpot("", "", List.of());

        // when
        var result = aiService.fetchAiAnalysis(spot);

        // then
        StepVerifier.create(result)
                .expectNextCount(0)
                .verifyComplete();

        verify(chatClient, never()).prompt();
    }

    @Test
    void shouldFetchAiAnalysisSuccessfully() {
        // given
        var spot = createSpot("Hel", "Poland", List.of(createForecast()));
        var aiResponse = "Good conditions for kitesurfing with 15 kts NW wind.";

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.just("Good ", "conditions ", "for ", "kitesurfing ", "with ", "15 ", "kts ", "NW ", "wind."));

        // when
        var result = aiService.fetchAiAnalysis(spot);

        // then
        StepVerifier.create(result)
                .expectNext(aiResponse)
                .verifyComplete();

        verify(chatClient).prompt();
        verify(requestSpec).user(anyString());
        verify(requestSpec).stream();
        verify(streamSpec).content();
    }

    @Test
    void shouldApplyDelayBetweenElements() {
        // given
        var spot = createSpot("Hel", "Poland", List.of(createForecast()));

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.just("Test ", "response"));

        // when
        var result = aiService.fetchAiAnalysis(spot);

        // then
        StepVerifier.create(result)
                .expectNext("Test response")
                .verifyComplete();
    }

    @Test
    void shouldHandleEmptyStreamResponse() {
        // given
        var spot = createSpot("Hel", "Poland", List.of(createForecast()));

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.empty());

        // when
        var result = aiService.fetchAiAnalysis(spot);

        // then
        StepVerifier.create(result)
                .expectNext("")
                .verifyComplete();
    }

    @Test
    void shouldConcatenateMultipleChunks() {
        // given
        var spot = createSpot("Hel", "Poland", List.of(createForecast()));

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.just("Part1", "Part2", "Part3"));

        // when
        var result = aiService.fetchAiAnalysis(spot);

        // then
        StepVerifier.create(result)
                .expectNext("Part1Part2Part3")
                .verifyComplete();
    }

    @Test
    void shouldFormatPromptWithSpotData() {
        // given
        var forecast = createForecast();
        var spot = createSpot("Hel", "Poland", List.of(forecast));

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.just("Response"));

        // when
        aiService.fetchAiAnalysis(spot).block();

        // then
        verify(requestSpec).user(argThat((String prompt) ->
                prompt.contains("Hel") &&
                        prompt.contains("Poland") &&
                        prompt.contains(gson.toJson(List.of(forecast)))
        ));
    }

    @Test
    void shouldHandleMultipleForecastItems() {
        // given
        var forecast1 = new Forecast("Mon 12:00", 10.0, 15.0, "N", 12.5, 0.0);
        var forecast2 = new Forecast("Mon 15:00", 12.0, 18.0, "NE", 15.0, 0.0);
        var forecast3 = new Forecast("Mon 18:00", 8.0, 12.0, "E", 10.0, 1.0);
        var spot = createSpot("Hel", "Poland", List.of(forecast1, forecast2, forecast3));

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.just("Analysis"));

        // when
        var result = aiService.fetchAiAnalysis(spot);

        // then
        StepVerifier.create(result)
                .expectNext("Analysis")
                .verifyComplete();
    }

    @Test
    void shouldIncludeLlmCommentInPromptWhenProvided() {
        // given
        var forecast = createForecast();
        var spotInfo = new SpotInfo("Beach", "W, SW", "18-22°C", "Intermediate", "sandy", "none", "Spring, Summer", "Great spot", "Wind is usually stronger than forecast due to thermal effect from nearby mountains.");
        var spot = createSpotWithInfo("Hel", "Poland", List.of(forecast), spotInfo);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.just("Response"));

        // when
        aiService.fetchAiAnalysis(spot).block();

        // then
        verify(requestSpec).user(argThat((String prompt) ->
                prompt.contains("ADDITIONAL SPOT-SPECIFIC CONTEXT:") &&
                        prompt.contains("Wind is usually stronger than forecast due to thermal effect from nearby mountains.")
        ));
    }

    @Test
    void shouldNotIncludeLlmCommentSectionWhenEmpty() {
        // given
        var forecast = createForecast();
        var spotInfo = new SpotInfo("Beach", "W, SW", "18-22°C", "Intermediate", "sandy", "none", "Spring, Summer", "Great spot", "");
        var spot = createSpotWithInfo("Hel", "Poland", List.of(forecast), spotInfo);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.just("Response"));

        // when
        aiService.fetchAiAnalysis(spot).block();

        // then
        verify(requestSpec).user(argThat((String prompt) ->
                !prompt.contains("ADDITIONAL SPOT-SPECIFIC CONTEXT:")
        ));
    }

    private Spot createSpot(String name, String country, List<Forecast> forecasts) {
        return new Spot(
                name,
                country,
                "https://windguru.cz/123",
                null,
                null,
                null,
                null,
                null,
                new ArrayList<>(forecasts),
                new ArrayList<>(),
                null,
                null,
                null,
                null
        );
    }

    private Spot createSpotWithInfo(String name, String country, List<Forecast> forecasts, SpotInfo spotInfo) {
        return new Spot(
                name,
                country,
                "https://windguru.cz/123",
                null,
                null,
                null,
                null,
                null,
                new ArrayList<>(forecasts),
                new ArrayList<>(),
                null,
                null,
                spotInfo,
                null
        );
    }

    private Forecast createForecast() {
        return new Forecast("Mon 12:00", 10.0, 15.0, "N", 12.5, 0.0);
    }
}
