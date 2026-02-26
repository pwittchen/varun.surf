package com.github.pwittchen.varun.service.live;

import com.github.pwittchen.varun.model.live.CurrentConditions;
import com.github.pwittchen.varun.model.live.filter.CurrentConditionsStalenessChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.List;

@Service
public class CurrentConditionsService {

    private static final Logger LOG = LoggerFactory.getLogger(CurrentConditionsService.class);

    private final List<FetchCurrentConditions> strategies;
    private final Clock clock;

    @Autowired
    public CurrentConditionsService(List<FetchCurrentConditions> strategies) {
        this(strategies, Clock.systemDefaultZone());
    }

    CurrentConditionsService(List<FetchCurrentConditions> strategies, Clock clock) {
        this.strategies = strategies;
        this.clock = clock;
    }

    public Mono<CurrentConditions> fetchCurrentConditions(int wgId) {
        return Flux.fromIterable(strategies)
                .filter(strategy -> strategy.canProcess(wgId))
                .collectMultimap(FetchCurrentConditions::isFallbackStation)
                .flatMap(grouped -> {
                    var fallbackMono = Mono.defer(() ->
                            grouped.getOrDefault(true, List.of())
                                    .stream()
                                    .findFirst()
                                    .map(s -> s.fetchCurrentConditions(wgId))
                                    .orElse(Mono.empty())
                    );

                    return Mono.justOrEmpty(
                                    grouped.getOrDefault(false, List.of())
                                            .stream()
                                            .findFirst()
                            )
                            .flatMap(primary -> primary.fetchCurrentConditions(wgId)
                                    .flatMap(conditions -> {
                                        if (CurrentConditionsStalenessChecker.isStale(conditions, clock)) {
                                            LOG.info("Primary station for wgId {} returned stale data ({}), " +
                                                    "trying fallback", wgId, conditions.date());
                                            return fallbackMono
                                                    .defaultIfEmpty(conditions)
                                                    .onErrorResume(e -> {
                                                        LOG.warn("Fallback station for wgId {} failed: {}, " +
                                                                "returning stale primary data", wgId, e.getMessage());
                                                        return Mono.just(conditions);
                                                    });
                                        }
                                        return Mono.just(conditions);
                                    })
                                    .switchIfEmpty(Mono.defer(() -> {
                                        LOG.info("Primary station for wgId {} returned empty, trying fallback", wgId);
                                        return fallbackMono;
                                    }))
                                    .onErrorResume(e -> {
                                        LOG.info("Primary station for wgId {} errored: {}, trying fallback",
                                                wgId, e.getMessage());
                                        return fallbackMono;
                                    })
                            );
                });
    }
}
