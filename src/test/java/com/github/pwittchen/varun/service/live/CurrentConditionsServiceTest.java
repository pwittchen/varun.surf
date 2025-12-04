package com.github.pwittchen.varun.service.live;

import com.github.pwittchen.varun.http.HttpClientProxy;
import com.github.pwittchen.varun.model.live.CurrentConditions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static com.google.common.truth.Truth.assertThat;

class CurrentConditionsServiceTest {

    private CurrentConditionsService service;

    @BeforeEach
    void setUp() {
        service = new CurrentConditionsService(new HttpClientProxy());
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