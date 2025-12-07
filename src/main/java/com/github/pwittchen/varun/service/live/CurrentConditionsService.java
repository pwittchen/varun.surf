package com.github.pwittchen.varun.service.live;

import com.github.pwittchen.varun.model.live.CurrentConditions;
import com.github.pwittchen.varun.service.live.strategy.FetchCurrentConditionsStrategyMB;
import com.github.pwittchen.varun.service.live.strategy.FetchCurrentConditionsStrategyPodersdorf;
import com.github.pwittchen.varun.service.live.strategy.FetchCurrentConditionsStrategyPuck;
import com.github.pwittchen.varun.service.live.strategy.FetchCurrentConditionsStrategyTurawa;
import com.github.pwittchen.varun.service.live.strategy.FetchCurrentConditionsStrategyWiatrKadynyStations;
import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.LinkedList;
import java.util.List;

@Service
public class CurrentConditionsService {

    private final List<FetchCurrentConditions> strategies = new LinkedList<>();

    public CurrentConditionsService(OkHttpClient httpClient, Gson gson) {
        strategies.add(new FetchCurrentConditionsStrategyWiatrKadynyStations(httpClient));
        strategies.add(new FetchCurrentConditionsStrategyPodersdorf(httpClient));
        strategies.add(new FetchCurrentConditionsStrategyTurawa(httpClient));
        strategies.add(new FetchCurrentConditionsStrategyMB(httpClient));
        strategies.add(new FetchCurrentConditionsStrategyPuck(httpClient, gson));
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