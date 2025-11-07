package com.github.pwittchen.varun.service.live;

import com.github.pwittchen.varun.model.live.CurrentConditions;
import reactor.core.publisher.Mono;

public interface FetchCurrentConditions {
    boolean canProcess(int wgId);

    Mono<CurrentConditions> fetchCurrentConditions(int wgId);
}
