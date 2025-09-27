package com.github.pwittchen.varun.provider;

import com.github.pwittchen.varun.model.Spot;
import reactor.core.publisher.Flux;

public interface SpotsDataProvider {
    Flux<Spot> getSpots();
}
