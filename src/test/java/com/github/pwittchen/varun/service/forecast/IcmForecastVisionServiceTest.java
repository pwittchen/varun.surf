package com.github.pwittchen.varun.service.forecast;

import com.github.pwittchen.varun.model.forecast.Forecast;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IcmForecastVisionServiceTest {

    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd.MM", Locale.ENGLISH);
    private static final DateTimeFormatter EXPECTED_DATE =
            DateTimeFormatter.ofPattern("EEE dd MMM yyyy", Locale.ENGLISH);

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
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Warsaw"));
        String day = today.format(DAY_MONTH);

        stubVisionResponse("""
                [
                  {"day":"%s","hour":6,"windMs":6.0,"gustMs":9.0,"arrowPointsTo":"SE","tempC":5.0,
                   "precipitationMm":0.0,"pressureHpa":1012.0,"cloudCoverOctants":4.0},
                  {"day":"%s","hour":9,"windMs":10.0,"gustMs":13.0,"arrowPointsTo":"e","tempC":7.0,
                   "precipitationMm":0.5,"pressureHpa":1011.0,"cloudCoverOctants":8.0}
                ]
                """.formatted(day, day));

        Optional<List<Forecast>> result = extract();

        assertThat(result.isPresent()).isTrue();
        assertThat(result.get()).hasSize(2);

        Forecast first = result.get().getFirst();
        assertThat(first.date()).isEqualTo(today.format(EXPECTED_DATE) + " 06:00");
        assertThat(first.wind()).isEqualTo(11.7); // 6 m/s in knots
        assertThat(first.gusts()).isEqualTo(17.5); // 9 m/s in knots
        assertThat(first.direction()).isEqualTo("NW");
        assertThat(first.temp()).isEqualTo(5.0);
        assertThat(first.pressureHpa()).isEqualTo(1012.0);
        assertThat(first.cloudCoverPercent()).isEqualTo(50.0);

        Forecast second = result.get().get(1);
        assertThat(second.date()).isEqualTo(today.format(EXPECTED_DATE) + " 09:00");
        assertThat(second.wind()).isEqualTo(19.4);
        assertThat(second.direction()).isEqualTo("W");
        assertThat(second.precipitation()).isEqualTo(0.5);
        assertThat(second.cloudCoverPercent()).isEqualTo(100.0);
    }

    @Test
    void shouldReverseDirectionArrowsIntoTheDirectionWindComesFrom() {
        String day = LocalDate.now(ZoneId.of("Europe/Warsaw")).format(DAY_MONTH);

        // ICM arrows are downwind vectors: an arrow pointing east means a west wind
        stubVisionResponse("""
                [
                  {"day":"%s","hour":6,"windMs":8.0,"gustMs":10.0,"arrowPointsTo":"E","tempC":10.0,
                   "precipitationMm":0.0,"pressureHpa":1000.0,"cloudCoverOctants":0.0},
                  {"day":"%s","hour":9,"windMs":8.0,"gustMs":10.0,"arrowPointsTo":"N","tempC":10.0,
                   "precipitationMm":0.0,"pressureHpa":1000.0,"cloudCoverOctants":0.0},
                  {"day":"%s","hour":12,"windMs":8.0,"gustMs":10.0,"arrowPointsTo":"unreadable",
                   "tempC":10.0,"precipitationMm":0.0,"pressureHpa":1000.0,"cloudCoverOctants":0.0}
                ]
                """.formatted(day, day, day));

        Optional<List<Forecast>> result = extract();

        assertThat(result.isPresent()).isTrue();
        assertThat(result.get().getFirst().direction()).isEqualTo("W");
        assertThat(result.get().get(1).direction()).isEqualTo("S");
        assertThat(result.get().get(2).direction()).isEmpty();
    }

    @Test
    void shouldSortPointsChronologically() {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Warsaw"));
        LocalDate tomorrow = today.plusDays(1);

        stubVisionResponse("""
                [
                  {"day":"%s","hour":6,"windMs":5.0,"gustMs":7.0,"arrowPointsTo":"S","tempC":10.0,
                   "precipitationMm":0.0,"pressureHpa":1000.0,"cloudCoverOctants":0.0},
                  {"day":"%s","hour":18,"windMs":5.0,"gustMs":7.0,"arrowPointsTo":"S","tempC":10.0,
                   "precipitationMm":0.0,"pressureHpa":1000.0,"cloudCoverOctants":0.0}
                ]
                """.formatted(tomorrow.format(DAY_MONTH), today.format(DAY_MONTH)));

        Optional<List<Forecast>> result = extract();

        assertThat(result.isPresent()).isTrue();
        assertThat(result.get().getFirst().date()).isEqualTo(today.format(EXPECTED_DATE) + " 18:00");
        assertThat(result.get().get(1).date()).isEqualTo(tomorrow.format(EXPECTED_DATE) + " 06:00");
    }

    @Test
    void shouldClampGustsToAtLeastWindSpeed() {
        String day = LocalDate.now(ZoneId.of("Europe/Warsaw")).format(DAY_MONTH);

        stubVisionResponse("""
                [{"day":"%s","hour":12,"windMs":10.0,"gustMs":4.0,"arrowPointsTo":"N","tempC":10.0,
                  "precipitationMm":0.0,"pressureHpa":1000.0,"cloudCoverOctants":0.0}]
                """.formatted(day));

        Optional<List<Forecast>> result = extract();

        assertThat(result.isPresent()).isTrue();
        assertThat(result.get().getFirst().gusts()).isEqualTo(result.get().getFirst().wind());
    }

    @Test
    void shouldStripMarkdownCodeFencesFromResponse() {
        String day = LocalDate.now(ZoneId.of("Europe/Warsaw")).format(DAY_MONTH);

        stubVisionResponse("""
                ```json
                [{"day":"%s","hour":12,"windMs":7.0,"gustMs":10.0,"arrowPointsTo":"NE","tempC":12.0,
                  "precipitationMm":0.0,"pressureHpa":1005.0,"cloudCoverOctants":2.0}]
                ```
                """.formatted(day));

        Optional<List<Forecast>> result = extract();

        assertThat(result.isPresent()).isTrue();
        assertThat(result.get()).hasSize(1);
        assertThat(result.get().getFirst().direction()).isEqualTo("SW");
    }

    @Test
    void shouldRejectPointsOutsideForecastRange() {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Warsaw"));

        stubVisionResponse("""
                [
                  {"day":"%s","hour":12,"windMs":7.0,"gustMs":10.0,"arrowPointsTo":"NE","tempC":12.0,
                   "precipitationMm":0.0,"pressureHpa":1005.0,"cloudCoverOctants":2.0},
                  {"day":"%s","hour":99,"windMs":7.0,"gustMs":10.0,"arrowPointsTo":"NE","tempC":12.0,
                   "precipitationMm":0.0,"pressureHpa":1005.0,"cloudCoverOctants":2.0}
                ]
                """.formatted(today.plusDays(60).format(DAY_MONTH), today.format(DAY_MONTH)));

        Optional<List<Forecast>> result = extract();

        assertThat(result.isPresent()).isFalse();
    }

    @Test
    void shouldRequestEnglishVersionOfTheMeteogram() throws InterruptedException {
        String day = LocalDate.now(ZoneId.of("Europe/Warsaw")).format(DAY_MONTH);

        stubVisionResponse("""
                [{"day":"%s","hour":12,"windMs":7.0,"gustMs":10.0,"arrowPointsTo":"NE","tempC":12.0,
                  "precipitationMm":0.0,"pressureHpa":1005.0,"cloudCoverOctants":2.0}]
                """.formatted(day));

        String url = mockWebServer.url("/mgram_pict.php?ntype=0u&row=338&col=208&lang=pl").toString();
        service.extractForecastFromMeteogram(url);

        RecordedRequest recorded = mockWebServer.takeRequest();
        assertThat(recorded.getPath()).contains("lang=en");
        assertThat(recorded.getPath()).doesNotContain("lang=pl");
    }

    @Test
    void shouldReturnEmptyWhenChatClientThrowsException() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("fake-image-bytes")
                .setResponseCode(200));

        when(chatClient.prompt()).thenThrow(new RuntimeException("API error"));

        assertThat(extract().isPresent()).isFalse();
    }

    @Test
    void shouldReturnEmptyWhenResponseIsMalformedJson() {
        stubVisionResponse("not valid json at all");

        assertThat(extract().isPresent()).isFalse();
    }

    @Test
    void shouldReturnEmptyWhenImageDownloadFails() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        assertThat(extract().isPresent()).isFalse();
    }

    @Test
    void shouldReturnEmptyWhenResponseIsNull() {
        stubVisionResponse(null);

        assertThat(extract().isPresent()).isFalse();
    }

    @Test
    void shouldReturnEmptyWhenResponseIsEmptyJsonArray() {
        stubVisionResponse("[]");

        assertThat(extract().isPresent()).isFalse();
    }

    @SuppressWarnings("unchecked")
    private void stubVisionResponse(String jsonResponse) {
        mockWebServer.enqueue(new MockResponse()
                .setBody("fake-image-bytes")
                .setResponseCode(200));

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(any(java.util.function.Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.content())
                .thenReturn(jsonResponse == null ? Flux.empty() : Flux.just(jsonResponse));
    }

    private Optional<List<Forecast>> extract() {
        return service.extractForecastFromMeteogram(mockWebServer.url("/meteogram.png").toString());
    }
}
