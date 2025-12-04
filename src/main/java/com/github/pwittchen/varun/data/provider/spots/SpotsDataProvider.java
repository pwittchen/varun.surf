package com.github.pwittchen.varun.data.provider.spots;

import com.github.pwittchen.varun.model.spot.Spot;
import reactor.core.publisher.Flux;

public interface SpotsDataProvider {
    Flux<Spot> getSpots();
}
