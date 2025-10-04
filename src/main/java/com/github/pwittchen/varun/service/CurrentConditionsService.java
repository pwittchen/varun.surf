package com.github.pwittchen.varun.service;

import com.github.pwittchen.varun.model.CurrentConditions;
import com.github.pwittchen.varun.service.strategy.FetchCurrentConditions;
import com.github.pwittchen.varun.service.strategy.FetchCurrentConditionsStrategyKR;
import com.github.pwittchen.varun.service.strategy.FetchCurrentConditionsStrategyWK;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.LinkedList;
import java.util.List;

@Service
public class CurrentConditionsService {

    private final List<FetchCurrentConditions> strategies = new LinkedList<>();

    public CurrentConditionsService() {
        strategies.add(new FetchCurrentConditionsStrategyWK());
        strategies.add(new FetchCurrentConditionsStrategyKR());
    }

    public Mono<CurrentConditions> fetchCurrentConditions(int wgId) {
        return strategies
                .stream()
                .filter(fetchCurrentConditions -> fetchCurrentConditions.canProcess(wgId))
                .map(fetchCurrentConditions -> fetchCurrentConditions.fetchCurrentConditions(wgId))
                .findFirst()
                .orElse(Mono.empty());
    }
}