package com.github.pwittchen.varun.provider;

import com.github.pwittchen.varun.model.Sponsor;
import reactor.core.publisher.Flux;

public interface SponsorsDataProvider {
    Flux<Sponsor> getSponsors();
}