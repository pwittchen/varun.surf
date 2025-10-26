package com.github.pwittchen.varun.provider.sponsors;

import com.github.pwittchen.varun.model.sponsor.Sponsor;
import reactor.core.publisher.Flux;

public interface SponsorsDataProvider {
    Flux<Sponsor> getSponsors();
}