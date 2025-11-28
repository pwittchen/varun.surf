package com.github.pwittchen.varun.service.live;

import com.github.pwittchen.varun.http.HttpClientProvider;
import com.github.pwittchen.varun.model.live.CurrentConditions;
import com.github.pwittchen.varun.service.live.strategy.FetchCurrentConditionsStrategyMB;
import com.github.pwittchen.varun.service.live.strategy.FetchCurrentConditionsStrategyPodersdorf;
import com.github.pwittchen.varun.service.live.strategy.FetchCurrentConditionsStrategyTurawa;
import com.github.pwittchen.varun.service.live.strategy.FetchCurrentConditionsStrategyWiatrKadynyStations;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.LinkedList;
import java.util.List;

@Service
public class CurrentConditionsService {

    private final List<FetchCurrentConditions> strategies = new LinkedList<>();

    public CurrentConditionsService(HttpClientProvider httpClientProvider) {
        strategies.add(new FetchCurrentConditionsStrategyWiatrKadynyStations(httpClientProvider));
        strategies.add(new FetchCurrentConditionsStrategyPodersdorf(httpClientProvider));
        strategies.add(new FetchCurrentConditionsStrategyTurawa(httpClientProvider));
        strategies.add(new FetchCurrentConditionsStrategyMB(httpClientProvider));
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