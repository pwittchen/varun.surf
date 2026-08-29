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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiServiceEnTest {

    private static final DateTimeFormatter GRID_FORMATTER =
            DateTimeFormatter.ofPattern("EEE dd MMM yyyy HH:mm", Locale.ENGLISH);

    private static final LocalDateTime START = LocalDateTime.of(2025, 10, 28, 14, 0);

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.StreamResponseSpec streamSpec;

    private AiServiceEn aiServiceEn;

    @BeforeEach
    void setUp() {
        aiServiceEn = new AiServiceEn(chatClient);
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
        var spot = createSpot("", "Poland");

        // when
        var result = aiServiceEn.fetchAiAnalysis(spot, createHourlyForecast());

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
        var result = aiServiceEn.fetchAiAnalysis(spot, createHourlyForecast());

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
        var spot = createSpot("Hel", "Poland");

        // when
        var result = aiServiceEn.fetchAiAnalysis(spot, new HourlyForecast(123, List.of()));

        // then
        StepVerifier.create(result)
                .expectNextCount(0)
                .verifyComplete();

        verify(chatClient, never()).prompt();
    }

    @Test
    void shouldFetchAiAnalysisSuccessfully() {
        // given
        var spot = createSpot("Hel", "Poland");
        var aiResponse = "Good conditions for kitesurfing with 15 kts NW wind.";

        mockChatResponse("Good ", "conditions ", "for ", "kitesurfing ", "with ", "15 ", "kts ", "NW ", "wind.");

        // when & then
        StepVerifier.withVirtualTime(() -> aiServiceEn.fetchAiAnalysis(spot, createHourlyForecast()))
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
        var spot = createSpot("Hel", "Poland");
        mockChatResponse("Test ", "response");

        // when
        var result = aiServiceEn.fetchAiAnalysis(spot, createHourlyForecast());

        // then
        StepVerifier.create(result)
                .expectNext("Test response")
                .verifyComplete();
    }

    @Test
    void shouldHandleEmptyStreamResponse() {
        // given
        var spot = createSpot("Hel", "Poland");

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.empty());

        // when
        var result = aiServiceEn.fetchAiAnalysis(spot, createHourlyForecast());

        // then
        StepVerifier.create(result)
                .expectNext("")
                .verifyComplete();
    }

    @Test
    void shouldConcatenateMultipleChunks() {
        // given
        var spot = createSpot("Hel", "Poland");
        mockChatResponse("Part1", "Part2", "Part3");

        // when & then - the chunks arrive as fast as the model emits them, so there
        // is no virtual time to wind forward any more
        StepVerifier.create(aiServiceEn.fetchAiAnalysis(spot, createHourlyForecast()))
                .expectNext("Part1Part2Part3")
                .verifyComplete();
    }

    @Test
    void shouldFormatPromptWithSpotData() {
        // given
        var spot = createSpot("Hel", "Poland");
        mockChatResponse("Response");

        // when
        aiServiceEn.fetchAiAnalysis(spot, createHourlyForecast()).block();

        // then
        verify(requestSpec).user(argThat((String prompt) ->
                prompt.contains("Hel") &&
                        prompt.contains("Poland") &&
                        // the full "Tue 28 Oct 2025 14:00" is shortened to save tokens
                        prompt.contains("Tue 14:00|12|16|NW|21|0.4|40|1013|0.8|4|SW")
        ));
    }

    @Test
    void shouldCarryEveryForecastVariableInTheRows() {
        // given
        var spot = createSpot("Hel", "Poland");
        mockChatResponse("Response");

        // when
        aiServiceEn.fetchAiAnalysis(spot, createHourlyForecast()).block();

        // then
        verify(requestSpec).user(argThat((String prompt) ->
                prompt.contains("time|wind|gust|dir|temp|rain|cloud|pressure|wave|wavePeriod|waveDir")
        ));
    }

    @Test
    void shouldDropWaveColumnsForInlandSpots() {
        // given - three columns of dashes on every row of every lake is a lot of
        // tokens spent saying nothing
        var spot = createSpot("Zegrze", "Poland");
        mockChatResponse("Response");

        // when
        aiServiceEn.fetchAiAnalysis(spot, createInlandHourlyForecast()).block();

        // then
        verify(requestSpec).user(argThat((String prompt) ->
                prompt.contains("time|wind|gust|dir|temp|rain|cloud|pressure")
                        && !prompt.contains("waveDir")
                        && prompt.contains("Tue 14:00|12|16|NW|21|0.4|40|1013")
        ));
    }

    @Test
    void shouldMarkUnknownValuesWithADash() {
        // given
        var spot = createSpot("Hel", "Poland");
        var hourly = new HourlyForecast(123, List.of(
                new Forecast("Tue 28 Oct 2025 14:00", 12, 16, "", 21, 0.4, 40, 1013, 0.8, null, null)
        ));
        mockChatResponse("Response");

        // when
        aiServiceEn.fetchAiAnalysis(spot, hourly).block();

        // then
        verify(requestSpec).user(argThat((String prompt) ->
                prompt.contains("Tue 14:00|12|16|-|21|0.4|40|1013|0.8|-|-")
        ));
    }

    @Test
    void shouldIncludeLlmCommentInPromptWhenProvided() {
        // given
        var spotInfo = new SpotInfo("Beach", "W, SW", "18-22°C", "Intermediate", "sandy", "none", "Spring, Summer", "Great spot", "Wind is usually stronger than forecast due to thermal effect from nearby mountains.");
        var spot = createSpotWithInfo("Hel", "Poland", spotInfo);
        mockChatResponse("Response");

        // when
        aiServiceEn.fetchAiAnalysis(spot, createHourlyForecast()).block();

        // then
        verify(requestSpec).user(argThat((String prompt) ->
                prompt.contains("ADDITIONAL SPOT-SPECIFIC CONTEXT:") &&
                        prompt.contains("Wind is usually stronger than forecast due to thermal effect from nearby mountains.")
        ));
    }

    @Test
    void shouldIgnorePolishLlmCommentInEnglishAnalysis() {
        // given
        var spotInfoEn = new SpotInfo("Beach", "S", "18-22°C", "Advanced", "sandy", "none", "Summer", "Great spot", "Rideable only in S wind.");
        var spotInfoPl = new SpotInfo("Plaża", "S", "18-22°C", "Zaawansowany", "piaszczysty", "brak", "Lato", "Świetny spot", "Pływalne tylko przy wietrze S.");
        var spot = createSpotWithInfos("Hel", "Poland", spotInfoEn, spotInfoPl);
        mockChatResponse("Response");

        // when
        aiServiceEn.fetchAiAnalysis(spot, createHourlyForecast()).block();

        // then
        verify(requestSpec).user(argThat((String prompt) ->
                prompt.contains("Rideable only in S wind.") &&
                        !prompt.contains("Pływalne tylko przy wietrze S.")
        ));
    }

    @Test
    void shouldNotIncludeLlmCommentSectionWhenEmpty() {
        // given
        var spotInfo = new SpotInfo("Beach", "W, SW", "18-22°C", "Intermediate", "sandy", "none", "Spring, Summer", "Great spot", "");
        var spot = createSpotWithInfo("Hel", "Poland", spotInfo);
        mockChatResponse("Response");

        // when
        aiServiceEn.fetchAiAnalysis(spot, createHourlyForecast()).block();

        // then
        verify(requestSpec).user(argThat((String prompt) ->
                !prompt.contains("ADDITIONAL SPOT-SPECIFIC CONTEXT:")
        ));
    }

    @Test
    void shouldNotCarryDailyAveragesInPrompt() {
        // given - daily averages hide the hours a session actually happens in, so
        // the prompt is built from the hourly data alone
        var spot = createSpot("Hel", "Poland");
        mockChatResponse("Response");

        // when
        aiServiceEn.fetchAiAnalysis(spot, createHourlyForecast()).block();

        // then
        verify(requestSpec).user(argThat((String prompt) ->
                !prompt.contains("Today|") && !prompt.contains("Tomorrow|")
        ));
    }

    @Test
    void shouldThinTheGridBeyondTheDetailedWindow() {
        // given - a full 120-hour forecast: hourly for the first 48 hours, then
        // every third hour, which is the resolution the forecast itself drops to
        var spot = createSpot("Hel", "Poland");
        mockChatResponse("Response");

        // when
        aiServiceEn.fetchAiAnalysis(spot, createFullGrid()).block();

        // then
        verify(requestSpec).user(argThat((String prompt) -> {
            long rows = countRows(prompt);
            // the grid starts at 14:00, so of the 48 hourly rows 32 fall in daylight
            // (8 on the first evening, 16 on the full day, 8 on the last morning), and
            // of the 24 coarse rows 15 do - the rest are night and are dropped
            return rows == 32 + 15;
        }));
    }

    @Test
    void shouldDropNightHoursFromTheRows() {
        // given - nobody rides in the dark, so night rows are noise in the answer
        // and tokens in the prompt
        var spot = createSpot("Hel", "Poland");
        mockChatResponse("Response");

        // when
        aiServiceEn.fetchAiAnalysis(spot, createFullGrid()).block();

        // then
        verify(requestSpec).user(argThat((String prompt) ->
                countRows(prompt) > 0 && rowHours(prompt).allMatch(hour -> hour >= 6 && hour <= 21)
        ));
    }

    @Test
    void shouldKeepBothEndsOfTheDaylightWindow() {
        // given
        var spot = createSpot("Hel", "Poland");
        mockChatResponse("Response");

        // when
        aiServiceEn.fetchAiAnalysis(spot, createFullGrid()).block();

        // then - 06:00 and 21:00 are in, the hours either side of them are not
        verify(requestSpec).user(argThat((String prompt) ->
                prompt.contains(shortHour(START.plusHours(16)) + "|")   // 06:00
                        && prompt.contains(shortHour(START.plusHours(31)) + "|")   // 21:00
                        && !prompt.contains(shortHour(START.plusHours(15)) + "|")  // 05:00
                        && !prompt.contains(shortHour(START.plusHours(32)) + "|")  // 22:00
        ));
    }

    @Test
    void shouldTellTheModelNotToAnalyseNightHours() {
        // given
        var spot = createSpot("Hel", "Poland");
        mockChatResponse("Response");

        // when
        aiServiceEn.fetchAiAnalysis(spot, createHourlyForecast()).block();

        // then
        verify(requestSpec).user(argThat((String prompt) ->
                prompt.contains("daylight hours only")
                        && prompt.contains("never describe conditions at night")
        ));
    }

    private long countRows(String prompt) {
        return prompt.lines().filter(line -> line.matches("\\w{3} \\d{2}:\\d{2}\\|.*")).count();
    }

    private java.util.stream.IntStream rowHours(String prompt) {
        return prompt.lines()
                .filter(line -> line.matches("\\w{3} \\d{2}:\\d{2}\\|.*"))
                .mapToInt(line -> Integer.parseInt(line.substring(4, 6)));
    }

    @Test
    void shouldKeepEveryHourWithinTheDetailedWindow() {
        // given
        var spot = createSpot("Hel", "Poland");
        mockChatResponse("Response");

        // when
        aiServiceEn.fetchAiAnalysis(spot, createFullGrid()).block();

        // then - hour 47 is still there, hour 49 is not (48 is, as the first coarse row)
        verify(requestSpec).user(argThat((String prompt) ->
                prompt.contains(shortHour(START.plusHours(47)) + "|")
                        && prompt.contains(shortHour(START.plusHours(48)) + "|")
                        && !prompt.contains(shortHour(START.plusHours(49)) + "|")
                        && prompt.contains(shortHour(START.plusHours(51)) + "|")
        ));
    }

    private String shortHour(LocalDateTime time) {
        String[] parts = time.format(GRID_FORMATTER).split(" ");
        return parts[0] + " " + parts[4];
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

    private HourlyForecast createFullGrid() {
        var hours = new ArrayList<Forecast>();
        for (int hour = 0; hour < 120; hour++) {
            hours.add(new Forecast(
                    START.plusHours(hour).format(GRID_FORMATTER),
                    10 + hour % 12, 15 + hour % 12, "N", 20, 0, 10, 1013
            ));
        }
        return new HourlyForecast(123, hours);
    }

    private Spot createSpot(String name, String country) {
        return createSpotWithInfo(name, country, null);
    }

    private Spot createSpotWithInfo(String name, String country, SpotInfo spotInfo) {
        return createSpotWithInfos(name, country, spotInfo, null);
    }

    private Spot createSpotWithInfos(String name, String country, SpotInfo spotInfo, SpotInfo spotInfoPL) {
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
                spotInfoPL,
                null,
                null,
                null
        );
    }
}
