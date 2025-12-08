package com.github.pwittchen.varun.controller;

import com.github.pwittchen.varun.metrics.SpotsControllerMetrics;
import com.github.pwittchen.varun.model.forecast.ForecastModel;
import com.github.pwittchen.varun.model.spot.Spot;
import com.github.pwittchen.varun.service.AggregatorService;
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
    private final SpotsControllerMetrics metrics;

    public SpotsController(AggregatorService aggregatorService, SpotsControllerMetrics metrics) {
        this.aggregatorService = aggregatorService;
        this.metrics = metrics;
    }

    @GetMapping("spots")
    public Flux<Spot> spots() {
        metrics.incrementSpotsRequestCounter();
        return Flux
                .fromIterable(aggregatorService.getSpots())
                .map(Spot::withoutCurrentConditionsHistory);
    }

    @GetMapping("spots/{id}")
    public Mono<ResponseEntity<Spot>> spot(@PathVariable int id) {
        metrics.incrementSpotByIdRequestCounter();
        return Mono
                .justOrEmpty(aggregatorService.getSpotById(id))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .doOnSuccess(_ -> aggregatorService.fetchForecastsForAllModels(id));
    }

    @GetMapping("spots/{id}/{model}")
    public Mono<ResponseEntity<Spot>> spot(@PathVariable int id, @PathVariable String model) {
        metrics.incrementSpotByIdRequestCounter();
        return Mono
                .justOrEmpty(aggregatorService.getSpotById(id, ForecastModel.valueOfGracefully(model)))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .doOnSuccess(_ -> aggregatorService.fetchForecastsForAllModels(id));
    }
}
