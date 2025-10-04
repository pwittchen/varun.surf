package com.github.pwittchen.varun.service.strategy;

import com.github.pwittchen.varun.model.CurrentConditions;
import reactor.core.publisher.Mono;

public interface FetchCurrentConditions {
    boolean canProcess(int wgId);

    Mono<CurrentConditions> fetchCurrentConditions(int wgId);
}
