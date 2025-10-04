package com.github.pwittchen.varun.service;

import com.github.pwittchen.varun.model.CurrentConditions;
import com.github.pwittchen.varun.service.strategy.FetchCurrentConditionsStrategyKR;
import com.github.pwittchen.varun.service.strategy.FetchCurrentConditionsStrategyWK;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class CurrentConditionsService {

    private final FetchCurrentConditionsStrategyWK fetchCurrentConditionsStrategyWK;
    private final FetchCurrentConditionsStrategyKR fetchCurrentConditionsStrategyKR;

    public CurrentConditionsService() {
        this.fetchCurrentConditionsStrategyWK = new FetchCurrentConditionsStrategyWK();
        this.fetchCurrentConditionsStrategyKR = new FetchCurrentConditionsStrategyKR();
    }

    public Mono<CurrentConditions> fetchCurrentConditions(int wgId) {
        if (fetchCurrentConditionsStrategyWK.canProcess(wgId)) {
            return fetchCurrentConditionsStrategyWK.fetchCurrentConditions(wgId);
        }
        if (fetchCurrentConditionsStrategyKR.canProcess(wgId)) {
            return fetchCurrentConditionsStrategyKR.fetchCurrentConditions(wgId);
        }

        return Mono.empty();
    }
}