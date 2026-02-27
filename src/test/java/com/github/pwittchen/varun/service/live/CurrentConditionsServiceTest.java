package com.github.pwittchen.varun.service.live;

import com.github.pwittchen.varun.model.live.CurrentConditions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

class CurrentConditionsServiceTest {

    private static final int WG_ID = 12345;
    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
    private static final LocalDateTime NOW = LocalDateTime.of(2025, 6, 15, 14, 0, 0);
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static CurrentConditions freshConditions() {
        String date = NOW.minusMinutes(10).format(FMT);
        return new CurrentConditions(date, 15, 20, "N", 22);
    }

    private static CurrentConditions staleConditions() {
        String date = NOW.minusHours(25).format(FMT);
        return new CurrentConditions(date, 10, 14, "SW", 18);
    }

    private static CurrentConditions fallbackConditions() {
        String date = NOW.minusMinutes(5).format(FMT);
        return new CurrentConditions(date, 12, 16, "W", 20);
    }

    private static FetchCurrentConditions strategy(int wgId, boolean fallback, Mono<CurrentConditions> result) {
        return new FetchCurrentConditions() {
            @Override
            public boolean canProcess(int id) {
                return id == wgId;
            }

            @Override
            public Mono<CurrentConditions> fetchCurrentConditions(int id) {
                return result;
            }

            @Override
            public boolean isFallbackStation() {
                return fallback;
            }
        };
    }

    @Test
    void shouldReturnFreshPrimaryDataWithoutCallingFallback() {
        CurrentConditions fresh = freshConditions();
        CurrentConditionsService service = new CurrentConditionsService(
                List.of(
                        strategy(WG_ID, false, Mono.just(fresh)),
                        strategy(WG_ID, true, Mono.error(new RuntimeException("should not be called")))
                ),
                FIXED_CLOCK
        );

        StepVerifier.create(service.fetchCurrentConditions(WG_ID))
                .assertNext(c -> assertThat(c).isEqualTo(fresh))
                .verifyComplete();
    }

    @Test
    void shouldReturnFallbackWhenPrimaryIsStale() {
        CurrentConditions fallback = fallbackConditions();
        CurrentConditionsService service = new CurrentConditionsService(
                List.of(
                        strategy(WG_ID, false, Mono.just(staleConditions())),
                        strategy(WG_ID, true, Mono.just(fallback))
                ),
                FIXED_CLOCK
        );

        StepVerifier.create(service.fetchCurrentConditions(WG_ID))
                .assertNext(c -> assertThat(c).isEqualTo(fallback))
                .verifyComplete();
    }

    @Test
    void shouldReturnStalePrimaryWhenFallbackErrors() {
        CurrentConditions stale = staleConditions();
        CurrentConditionsService service = new CurrentConditionsService(
                List.of(
                        strategy(WG_ID, false, Mono.just(stale)),
                        strategy(WG_ID, true, Mono.error(new RuntimeException("fallback failed")))
                ),
                FIXED_CLOCK
        );

        StepVerifier.create(service.fetchCurrentConditions(WG_ID))
                .assertNext(c -> assertThat(c).isEqualTo(stale))
                .verifyComplete();
    }

    @Test
    void shouldReturnStalePrimaryWhenFallbackIsEmpty() {
        CurrentConditions stale = staleConditions();
        CurrentConditionsService service = new CurrentConditionsService(
                List.of(
                        strategy(WG_ID, false, Mono.just(stale)),
                        strategy(WG_ID, true, Mono.empty())
                ),
                FIXED_CLOCK
        );

        StepVerifier.create(service.fetchCurrentConditions(WG_ID))
                .assertNext(c -> assertThat(c).isEqualTo(stale))
                .verifyComplete();
    }

    @Test
    void shouldReturnFallbackWhenPrimaryErrors() {
        CurrentConditions fallback = fallbackConditions();
        CurrentConditionsService service = new CurrentConditionsService(
                List.of(
                        strategy(WG_ID, false, Mono.error(new RuntimeException("primary failed"))),
                        strategy(WG_ID, true, Mono.just(fallback))
                ),
                FIXED_CLOCK
        );

        StepVerifier.create(service.fetchCurrentConditions(WG_ID))
                .assertNext(c -> assertThat(c).isEqualTo(fallback))
                .verifyComplete();
    }

    @Test
    void shouldReturnFallbackWhenPrimaryIsEmpty() {
        CurrentConditions fallback = fallbackConditions();
        CurrentConditionsService service = new CurrentConditionsService(
                List.of(
                        strategy(WG_ID, false, Mono.empty()),
                        strategy(WG_ID, true, Mono.just(fallback))
                ),
                FIXED_CLOCK
        );

        StepVerifier.create(service.fetchCurrentConditions(WG_ID))
                .assertNext(c -> assertThat(c).isEqualTo(fallback))
                .verifyComplete();
    }

    @Test
    void shouldReturnPrimaryDirectlyWhenNoFallbackExists() {
        CurrentConditions fresh = freshConditions();
        CurrentConditionsService service = new CurrentConditionsService(
                List.of(strategy(WG_ID, false, Mono.just(fresh))),
                FIXED_CLOCK
        );

        StepVerifier.create(service.fetchCurrentConditions(WG_ID))
                .assertNext(c -> assertThat(c).isEqualTo(fresh))
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyWhenNoPrimaryExists() {
        CurrentConditionsService service = new CurrentConditionsService(
                List.of(strategy(WG_ID, true, Mono.just(fallbackConditions()))),
                FIXED_CLOCK
        );

        StepVerifier.create(service.fetchCurrentConditions(WG_ID))
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyForUnknownWgId() {
        CurrentConditionsService service = new CurrentConditionsService(
                List.of(strategy(WG_ID, false, Mono.just(freshConditions()))),
                FIXED_CLOCK
        );

        StepVerifier.create(service.fetchCurrentConditions(999999))
                .verifyComplete();
    }
}
