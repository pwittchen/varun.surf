package com.github.pwittchen.varun.service.live;

import com.github.pwittchen.varun.http.HttpClientProxy;
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

    public CurrentConditionsService(HttpClientProxy httpClientProxy) {
        strategies.add(new FetchCurrentConditionsStrategyWiatrKadynyStations(httpClientProxy));
        strategies.add(new FetchCurrentConditionsStrategyPodersdorf(httpClientProxy));
        strategies.add(new FetchCurrentConditionsStrategyTurawa(httpClientProxy));
        strategies.add(new FetchCurrentConditionsStrategyMB(httpClientProxy));
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