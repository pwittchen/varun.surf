package com.github.pwittchen.varun.controller;

import com.github.pwittchen.varun.model.forecast.ForecastModel;
import com.github.pwittchen.varun.model.spot.Spot;
import com.github.pwittchen.varun.service.AggregatorService;
import io.micrometer.core.instrument.Counter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/")
public class SpotsController {

    private final AggregatorService aggregatorService;
    private final Counter apiSpotsRequestCounter;
    private final Counter apiSpotByIdRequestCounter;

    public SpotsController(
            AggregatorService aggregatorService,
            Counter apiSpotsRequestCounter,
            Counter apiSpotByIdRequestCounter) {
        this.aggregatorService = aggregatorService;
        this.apiSpotsRequestCounter = apiSpotsRequestCounter;
        this.apiSpotByIdRequestCounter = apiSpotByIdRequestCounter;
    }

    @GetMapping("spots")
    public Flux<Spot> spots() {
        apiSpotsRequestCounter.increment();
        return Flux.fromIterable(aggregatorService.getSpots());
    }

    @GetMapping("spots/{id}")
    public Mono<ResponseEntity<Spot>> spot(@PathVariable int id) {
        apiSpotByIdRequestCounter.increment();
        return Mono
                .justOrEmpty(aggregatorService.getSpotById(id))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .doOnSuccess(_ -> aggregatorService.fetchForecastsForAllModels(id));
    }

    @GetMapping("spots/{id}/{model}")
    public Mono<ResponseEntity<Spot>> spot(@PathVariable int id, @PathVariable String model) {
        apiSpotByIdRequestCounter.increment();
        return Mono
                .justOrEmpty(aggregatorService.getSpotById(id, ForecastModel.valueOfGracefully(model)))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .doOnSuccess(_ -> aggregatorService.fetchForecastsForAllModels(id));
    }
}
