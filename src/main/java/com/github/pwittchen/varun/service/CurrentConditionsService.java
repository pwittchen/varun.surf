package com.github.pwittchen.varun.service;

import com.github.pwittchen.varun.model.CurrentConditions;
import com.github.pwittchen.varun.service.strategy.FetchCurrentConditions;
import com.github.pwittchen.varun.service.strategy.FetchCurrentConditionsStrategyPodersdorf;
import com.github.pwittchen.varun.service.strategy.FetchCurrentConditionsStrategyWiatrKadynyStations;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.LinkedList;
import java.util.List;

@Service
public class CurrentConditionsService {

    private final List<FetchCurrentConditions> strategies = new LinkedList<>();

    public CurrentConditionsService() {
        strategies.add(new FetchCurrentConditionsStrategyWiatrKadynyStations());
        strategies.add(new FetchCurrentConditionsStrategyPodersdorf());
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