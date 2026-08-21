package com.github.pwittchen.varun.service.ai;

import com.github.pwittchen.varun.model.forecast.Forecast;
import com.github.pwittchen.varun.model.forecast.HourlyForecast;
import com.github.pwittchen.varun.model.spot.Spot;
import com.github.pwittchen.varun.model.spot.SpotInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiServicePlTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.StreamResponseSpec streamSpec;

    private AiServicePl aiServicePl;

    @BeforeEach
    void setUp() {
        aiServicePl = new AiServicePl(chatClient);
    }

    private void mockChatResponse(String... chunks) {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.just(chunks));
    }

    @Test
    void shouldReturnEmptyWhenSpotNameIsEmpty() {
        // given
        var spot = createSpot("", "Polska");

        // when
        var result = aiServicePl.fetchAiAnalysis(spot, createHourlyForecast());

        // then
        StepVerifier.create(result)
                .expectNextCount(0)
                .verifyComplete();

        verify(chatClient, never()).prompt();
    }

    @Test
    void shouldReturnEmptyWhenCountryIsEmpty() {
        // given
        var spot = createSpot("Hel", "");

        // when
        var result = aiServicePl.fetchAiAnalysis(spot, createHourlyForecast());

        // then
        StepVerifier.create(result)
                .expectNextCount(0)
                .verifyComplete();

        verify(chatClient, never()).prompt();
    }

    @Test
    void shouldReturnEmptyWhenHourlyForecastIsEmpty() {
        // given - the hourly forecast is the only weather data the prompt carries,
        // so without it there is nothing to write an analysis from
        var spot = createSpot("Hel", "Polska");

        // when
        var result = aiServicePl.fetchAiAnalysis(spot, new HourlyForecast(123, List.of()));

        // then
        StepVerifier.create(result)
                .expectNextCount(0)
                .verifyComplete();

        verify(chatClient, never()).prompt();
    }

    @Test
    void shouldFetchAiAnalysisSuccessfully() {
        // given
        var spot = createSpot("Hel", "Polska");
        var aiResponse = "Dobre warunki do kitesurfingu z wiatrem 15 kts NW.";

        mockChatResponse("Dobre ", "warunki ", "do ", "kitesurfingu ", "z ", "wiatrem ", "15 ", "kts ", "NW.");

        // when & then
        StepVerifier.withVirtualTime(() -> aiServicePl.fetchAiAnalysis(spot, createHourlyForecast()))
                .thenAwait(Duration.ofSeconds(20))
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
        var spot = createSpot("Hel", "Polska");
        mockChatResponse("Test ", "odpowiedzi");

        // when
        var result = aiServicePl.fetchAiAnalysis(spot, createHourlyForecast());

        // then
        StepVerifier.create(result)
                .expectNext("Test odpowiedzi")
                .verifyComplete();
    }

    @Test
    void shouldHandleEmptyStreamResponse() {
        // given
        var spot = createSpot("Hel", "Polska");

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.empty());

        // when
        var result = aiServicePl.fetchAiAnalysis(spot, createHourlyForecast());

        // then
        StepVerifier.create(result)
                .expectNext("")
                .verifyComplete();
    }

    @Test
    void shouldConcatenateMultipleChunks() {
        // given
        var spot = createSpot("Hel", "Polska");
        mockChatResponse("Część1", "Część2", "Część3");

        // when & then
        // the 3 chunks are emitted at 1s, 2s and 3s by delayElements(), so awaiting 5s is enough.
        // awaiting longer would push virtual time past the 15s timeout() deadline of the last chunk
        // and let the timeout fire before completion is observed, which makes the test flaky.
        StepVerifier.withVirtualTime(() -> aiServicePl.fetchAiAnalysis(spot, createHourlyForecast()))
                .thenAwait(Duration.ofSeconds(5))
                .expectNext("Część1Część2Część3")
                .verifyComplete();
    }

    @Test
    void shouldFormatPromptWithSpotData() {
        // given
        var spot = createSpot("Hel", "Polska");
        mockChatResponse("Odpowiedź");

        // when
        aiServicePl.fetchAiAnalysis(spot, createHourlyForecast()).block();

        // then
        verify(requestSpec).user(argThat((String prompt) ->
                prompt.contains("Hel") &&
                        prompt.contains("Polska") &&
                        // the full "Tue 28 Oct 2025 14:00" is shortened to save tokens
                        prompt.contains("Tue 14:00|12|16|NW|21|0.4|40|1013|0.8|4|SW")
        ));
    }

    @Test
    void shouldCarryEveryForecastVariableInTheRows() {
        // given
        var spot = createSpot("Hel", "Polska");
        mockChatResponse("Odpowiedź");

        // when
        aiServicePl.fetchAiAnalysis(spot, createHourlyForecast()).block();

        // then
        verify(requestSpec).user(argThat((String prompt) ->
                prompt.contains("czas|wiatr|porywy|kierunek|temp|opady|zachmurzenie|ciśnienie|fala|okresFali|kierunekFali")
        ));
    }

    @Test
    void shouldDropWaveColumnsForInlandSpots() {
        // given - three columns of dashes on every row of every lake is a lot of
        // tokens spent saying nothing
        var spot = createSpot("Zegrze", "Polska");
        mockChatResponse("Odpowiedź");

        // when
        aiServicePl.fetchAiAnalysis(spot, createInlandHourlyForecast()).block();

        // then
        verify(requestSpec).user(argThat((String prompt) ->
                prompt.contains("czas|wiatr|porywy|kierunek|temp|opady|zachmurzenie|ciśnienie")
                        && !prompt.contains("kierunekFali")
                        && prompt.contains("Tue 14:00|12|16|NW|21|0.4|40|1013")
        ));
    }

    @Test
    void shouldIncludeLlmCommentInPromptWhenProvided() {
        // given
        var spotInfo = new SpotInfo("Plaża", "W, SW", "18-22°C", "Średniozaawansowany", "piaszczysty", "brak", "Wiosna, Lato", "Świetny spot", "Wiatr jest zazwyczaj silniejszy niż prognoza z powodu efektu termicznego z pobliskich gór.");
        var spot = createSpotWithInfo("Hel", "Polska", spotInfo);
        mockChatResponse("Odpowiedź");

        // when
        aiServicePl.fetchAiAnalysis(spot, createHourlyForecast()).block();

        // then
        verify(requestSpec).user(argThat((String prompt) ->
                prompt.contains("DODATKOWY KONTEKST SPECYFICZNY DLA DANEGO SPOTU:") &&
                        prompt.contains("Wiatr jest zazwyczaj silniejszy niż prognoza z powodu efektu termicznego z pobliskich gór.")
        ));
    }

    @Test
    void shouldNotIncludeLlmCommentSectionWhenEmpty() {
        // given
        var spotInfo = new SpotInfo("Plaża", "W, SW", "18-22°C", "Średniozaawansowany", "piaszczysty", "brak", "Wiosna, Lato", "Świetny spot", "");
        var spot = createSpotWithInfo("Hel", "Polska", spotInfo);
        mockChatResponse("Odpowiedź");

        // when
        aiServicePl.fetchAiAnalysis(spot, createHourlyForecast()).block();

        // then
        verify(requestSpec).user(argThat((String prompt) ->
                !prompt.contains("DODATKOWY KONTEKST SPECYFICZNY DLA DANEGO SPOTU:")
        ));
    }

    @Test
    void shouldUsePolishPromptTemplate() {
        // given
        var spot = createSpot("Hel", "Polska");
        mockChatResponse("Odpowiedź");

        // when
        aiServicePl.fetchAiAnalysis(spot, createHourlyForecast()).block();

        // then
        verify(requestSpec).user(argThat((String prompt) ->
                prompt.contains("Jesteś profesjonalnym analitykiem pogodowym kitesurfingu") &&
                        prompt.contains("Spot:") &&
                        prompt.contains("Kraj:") &&
                        prompt.contains("Prognoza godzinowa")
        ));
    }

    @Test
    void shouldNotCarryDailyAveragesInPrompt() {
        // given
        var spot = createSpot("Hel", "Polska");
        mockChatResponse("Odpowiedź");

        // when
        aiServicePl.fetchAiAnalysis(spot, createHourlyForecast()).block();

        // then
        verify(requestSpec).user(argThat((String prompt) ->
                !prompt.contains("Today|") && !prompt.contains("Tomorrow|")
        ));
    }

    private HourlyForecast createHourlyForecast() {
        return new HourlyForecast(123, List.of(
                new Forecast("Tue 28 Oct 2025 14:00", 12, 16, "NW", 21, 0.4, 40, 1013, 0.8, 4.0, "SW"),
                new Forecast("Tue 28 Oct 2025 15:00", 14, 18, "N", 20, 0.0, 20, 1014, 0.6, 4.0, "SW")
        ));
    }

    private HourlyForecast createInlandHourlyForecast() {
        return new HourlyForecast(123, List.of(
                new Forecast("Tue 28 Oct 2025 14:00", 12, 16, "NW", 21, 0.4, 40, 1013),
                new Forecast("Tue 28 Oct 2025 15:00", 14, 18, "N", 20, 0.0, 20, 1014)
        ));
    }

    private Spot createSpot(String name, String country) {
        return createSpotWithInfo(name, country, null);
    }

    private Spot createSpotWithInfo(String name, String country, SpotInfo spotInfo) {
        return new Spot(
                name,
                country,
                "https://windguru.cz/123",
                null, // windguruFallbackUrl
                null,
                null,
                null,
                null,
                null,
                new ArrayList<>(),
                new ArrayList<>(List.of(new Forecast("Today", 10.0, 15.0, "N", 12.5, 0.0, 0, 0))),
                new ArrayList<>(),
                null,
                null,
                null,
                null,
                spotInfo,
                null,
                null,
                null,
                null
        );
    }
}
