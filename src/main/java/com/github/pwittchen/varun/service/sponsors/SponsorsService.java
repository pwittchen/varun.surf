package com.github.pwittchen.varun.service.sponsors;

import com.github.pwittchen.varun.model.sponsor.Sponsor;
import com.github.pwittchen.varun.data.provider.sponsors.SponsorsDataProvider;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
public class SponsorsService {

    private static final Logger log = LoggerFactory.getLogger(SponsorsService.class);

    private final AtomicReference<List<Sponsor>> sponsors;
    private final AtomicReference<Map<Integer, List<Sponsor>>> sponsorsBySpotId;
    private Disposable sponsorsDisposable;
    private final SponsorsDataProvider sponsorsDataProvider;

    public SponsorsService(SponsorsDataProvider sponsorsDataProvider) {
        this.sponsors = new AtomicReference<>(new ArrayList<>());
        this.sponsorsBySpotId = new AtomicReference<>(Collections.emptyMap());
        this.sponsorsDataProvider = sponsorsDataProvider;
    }

    @PostConstruct
    public void init() {
        sponsorsDisposable = sponsorsDataProvider
                .getSponsors()
                .collectList()
                .doOnSubscribe(_ -> log.info("Loading sponsors"))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(sponsors -> {
                    this.sponsors.set(sponsors);
                    this.sponsorsBySpotId.set(
                            sponsors.stream().collect(Collectors.groupingBy(Sponsor::id))
                    );
                    log.info("Loaded {} sponsors", sponsors.size());
                }, error -> log.error("Failed to load sponsors", error));
    }

    @PreDestroy
    public void cleanup() {
        sponsorsDisposable.dispose();
    }

    public List<Sponsor> getSponsors() {
        return sponsors.get();
    }

    public List<Sponsor> getSponsorsForSpot(int spotId) {
        return sponsorsBySpotId.get().getOrDefault(spotId, List.of());
    }

    public Optional<Sponsor> getSponsorById(int id) {
        return sponsors
                .get()
                .stream()
                .filter(sponsor -> sponsor.id() == id)
                .findFirst();
    }

    public List<Sponsor> getMainSponsors() {
        return sponsors
                .get()
                .stream()
                .filter(Sponsor::main)
                .toList();
    }
}