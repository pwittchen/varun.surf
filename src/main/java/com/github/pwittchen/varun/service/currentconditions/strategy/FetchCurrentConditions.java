package com.github.pwittchen.varun.service.currentconditions.strategy;

import com.github.pwittchen.varun.model.currentconditions.CurrentConditions;
import reactor.core.publisher.Mono;

public interface FetchCurrentConditions {
    boolean canProcess(int wgId);

    Mono<CurrentConditions> fetchCurrentConditions(int wgId);
}
