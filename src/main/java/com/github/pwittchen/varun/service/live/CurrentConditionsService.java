package com.github.pwittchen.varun.service.live;

import com.github.pwittchen.varun.model.live.CurrentConditions;
import com.github.pwittchen.varun.model.live.filter.CurrentConditionsStalenessChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

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
        List<FetchCurrentConditions> matching = strategies.stream()
                .filter(s -> s.canProcess(wgId))
                .toList();

        Optional<FetchCurrentConditions> primary = matching.stream()
                .filter(s -> !s.isFallbackStation())
                .findFirst();

        Optional<FetchCurrentConditions> fallback = matching.stream()
                .filter(FetchCurrentConditions::isFallbackStation)
                .findFirst();

        if (primary.isEmpty()) {
            return Mono.empty();
        }

        Mono<CurrentConditions> primaryMono = primary.get().fetchCurrentConditions(wgId);

        if (fallback.isEmpty()) {
            return primaryMono;
        }

        Mono<CurrentConditions> fallbackMono = fallback.get().fetchCurrentConditions(wgId);

        return primaryMono
                .flatMap(conditions -> {
                    if (CurrentConditionsStalenessChecker.isStale(conditions, clock)) {
                        LOG.info("Primary station for wgId {} returned stale data ({}), trying fallback",
                                wgId, conditions.date());
                        return fallbackMono
                                .defaultIfEmpty(conditions)
                                .onErrorResume(e -> {
                                    LOG.warn("Fallback station for wgId {} failed: {}, returning stale primary data",
                                            wgId, e.getMessage());
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
                    LOG.info("Primary station for wgId {} errored: {}, trying fallback", wgId, e.getMessage());
                    return fallbackMono;
                });
    }
}
