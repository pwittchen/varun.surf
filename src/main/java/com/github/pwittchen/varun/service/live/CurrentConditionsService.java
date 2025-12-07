package com.github.pwittchen.varun.service.live;

import com.github.pwittchen.varun.model.live.CurrentConditions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class CurrentConditionsService {

    private final List<FetchCurrentConditions> strategies;

    public CurrentConditionsService(List<FetchCurrentConditions> strategies) {
        this.strategies = strategies;
    }

    public Mono<CurrentConditions> fetchCurrentConditions(int wgId) {
        return strategies
                .stream()
                .filter(s -> s.canProcess(wgId))
                .map(s -> s.fetchCurrentConditions(wgId))
                .findFirst()
                .orElse(Mono.empty());
    }
}