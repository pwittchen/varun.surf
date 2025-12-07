package com.github.pwittchen.varun.service.live;

import com.github.pwittchen.varun.model.live.CurrentConditions;
import com.github.pwittchen.varun.service.live.strategy.FetchCurrentConditionsStrategyMB;
import com.github.pwittchen.varun.service.live.strategy.FetchCurrentConditionsStrategyPodersdorf;
import com.github.pwittchen.varun.service.live.strategy.FetchCurrentConditionsStrategyPuck;
import com.github.pwittchen.varun.service.live.strategy.FetchCurrentConditionsStrategyTurawa;
import com.github.pwittchen.varun.service.live.strategy.FetchCurrentConditionsStrategyWiatrKadynyStations;
import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;

class CurrentConditionsServiceTest {

    private CurrentConditionsService service;

    @BeforeEach
    void setUp() {
        OkHttpClient httpClient = new OkHttpClient();
        Gson gson = new Gson();
        List<FetchCurrentConditions> strategies = List.of(
                new FetchCurrentConditionsStrategyMB(httpClient),
                new FetchCurrentConditionsStrategyPodersdorf(httpClient),
                new FetchCurrentConditionsStrategyPuck(httpClient, gson),
                new FetchCurrentConditionsStrategyTurawa(httpClient),
                new FetchCurrentConditionsStrategyWiatrKadynyStations(httpClient)
        );
        service = new CurrentConditionsService(strategies);
    }

    @Test
    void shouldReturnEmptyMonoForUnknownWgId() {
        Mono<CurrentConditions> result = service.fetchCurrentConditions(999999);

        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void shouldProcessWKStrategy() {
        int wgId = 126330; // wiatrkadyny.pl ID

        Mono<CurrentConditions> result = service.fetchCurrentConditions(wgId);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldProcessKRStrategy() {
        int wgId = 859182; // kiteriders.at ID

        Mono<CurrentConditions> result = service.fetchCurrentConditions(wgId);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldProcessAllWKIds() {
        int[] wkIds = {126330, 14473, 509469, 500760, 4165, 48009};

        for (int wgId : wkIds) {
            Mono<CurrentConditions> result = service.fetchCurrentConditions(wgId);
            assertThat(result).isNotNull();
        }
    }
}