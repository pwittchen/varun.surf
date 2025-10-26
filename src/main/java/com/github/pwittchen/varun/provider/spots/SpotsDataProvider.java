package com.github.pwittchen.varun.provider.spots;

import com.github.pwittchen.varun.model.spot.Spot;
import reactor.core.publisher.Flux;

public interface SpotsDataProvider {
    Flux<Spot> getSpots();
}
