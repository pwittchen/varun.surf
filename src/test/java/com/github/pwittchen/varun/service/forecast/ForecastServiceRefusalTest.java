package com.github.pwittchen.varun.service.forecast;

import com.github.pwittchen.varun.mapper.WeatherForecastMapper;
import com.github.pwittchen.varun.model.forecast.ForecastModel;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.google.common.truth.Truth.assertThat;

class ForecastServiceRefusalTest {

    private static final String WIND_EXPORT = """
            windguru.cz
             Date           WSPD    GUST    WDEG     TMP   APCP1    HCLD    MCLD    LCLD     SLP
             Mon 29. 12h      15      20     257      20       -      80      60      40    1013
            """;

    private static final String WAVE_EXPORT = """
            windguru.cz
             Date          HTSGW   PERPW   WADEG
             Mon 29. 12h     0.8       5     270
            """;

    private MockWebServer server;
    private MutableClock clock;
    private ForecastService service;
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile boolean refuseWaves = true;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @NotNull
            @Override
            public MockResponse dispatch(@NotNull RecordedRequest request) {
                requests.add(request);
                boolean waves = request.getRequestUrl().queryParameter("m").equals("ewam");
                if (status != 200 && (refuseWaves || !waves)) {
                    return new MockResponse().setResponseCode(status);
                }
                return new MockResponse().setBody(waves ? WAVE_EXPORT : WIND_EXPORT);
            }
        });
        server.start();
        clock = new MutableClock(Instant.parse("2026-09-26T12:00:00Z"));
        service = new ForecastService(new WeatherForecastMapper(), new OkHttpClient(), server.url("/").toString(), clock);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void shouldIdentifyItselfInTheUserAgent() {
        service.getForecastData(1, ForecastModel.GFS).block();

        assertThat(requests).isNotEmpty();
        requests.forEach(request -> assertThat(request.getHeader("User-Agent")).contains("varun.surf"));
    }

    @Test
    void shouldStopSendingRequestsAfterARefusal() {
        status = 403;

        StepVerifier.create(service.getForecastData(1, ForecastModel.GFS))
                .expectError(ForecastService.WindguruRefusedException.class)
                .verify();

        assertThat(service.isPaused()).isTrue();
        StepVerifier.create(service.getForecastData(2, ForecastModel.GFS))
                .expectError(ForecastService.WindguruRefusedException.class)
                .verify();
        // The first spot's wave request can still reach the server after the refusal has failed the
        // forecast, so count only the second spot's requests: none of them may have been sent.
        long secondSpotRequests = requests.stream()
                .filter(request -> "2".equals(request.getRequestUrl().queryParameter("s")))
                .count();
        assertThat(secondSpotRequests).isEqualTo(0);
    }

    @Test
    void shouldNotRetryARefusedForecastRequest() {
        status = 403;

        StepVerifier.create(service.getForecastData(1, ForecastModel.GFS))
                .expectError(ForecastService.WindguruRefusedException.class)
                .verify();

        long forecastRequests = requests.stream()
                .filter(request -> !"ewam".equals(request.getRequestUrl().queryParameter("m")))
                .count();
        assertThat(forecastRequests).isEqualTo(1);
    }

    @Test
    void shouldPauseOnTooManyRequests() {
        status = 429;

        StepVerifier.create(service.getForecastData(1, ForecastModel.GFS))
                .expectError(ForecastService.WindguruRefusedException.class)
                .verify();

        assertThat(service.isPaused()).isTrue();
    }

    @Test
    void shouldResumeOnceThePauseRunsOut() {
        // The wave request outlives the refused forecast, and a refusal coming back after the clock
        // was advanced would start a new pause, so only the forecast request is refused here.
        refuseWaves = false;
        status = 403;
        StepVerifier.create(service.getForecastData(1, ForecastModel.GFS)).expectError().verify();

        status = 200;
        clock.advance(ForecastService.REFUSAL_PAUSE.plusSeconds(1));

        assertThat(service.isPaused()).isFalse();
        StepVerifier.create(service.getForecastData(1, ForecastModel.GFS))
                .assertNext(data -> assertThat(data.hourly(ForecastModel.GFS)).isNotEmpty())
                .verifyComplete();
    }

    @Test
    void shouldNotPauseOnOtherErrors() {
        status = 500;

        StepVerifier.create(service.getForecastData(1, ForecastModel.GFS))
                .expectError(IOException.class)
                .verify();

        assertThat(service.isPaused()).isFalse();
    }

    @Test
    void shouldFetchWavesOnceForEveryModelOfASpot() {
        service.getForecastData(1, ForecastModel.GFS).block();
        service.getForecastData(1, ForecastModel.IFS).block();
        service.getForecastData(1, ForecastModel.ICON).block();

        assertThat(countWaveRequests()).isEqualTo(1);
        StepVerifier.create(service.getForecastData(1, ForecastModel.IFS))
                .assertNext(data -> assertThat(data.hourly(ForecastModel.IFS).getFirst().wave()).isEqualTo(0.8))
                .verifyComplete();
    }

    @Test
    void shouldFetchWavesAgainOnceTheCachedOnesExpire() {
        service.getForecastData(1, ForecastModel.GFS).block();
        clock.advance(Duration.ofMinutes(11));
        service.getForecastData(1, ForecastModel.GFS).block();

        assertThat(countWaveRequests()).isEqualTo(2);
    }

    private long countWaveRequests() {
        return requests.stream()
                .filter(request -> "ewam".equals(request.getRequestUrl().queryParameter("m")))
                .count();
    }

    private static final class MutableClock extends Clock {
        private volatile Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
