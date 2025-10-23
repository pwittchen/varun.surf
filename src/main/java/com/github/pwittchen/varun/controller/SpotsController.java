package com.github.pwittchen.varun.controller;

import com.github.pwittchen.varun.model.ForecastModel;
import com.github.pwittchen.varun.model.Spot;
import com.github.pwittchen.varun.service.AggregatorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/")
public class SpotsController {

    private final AggregatorService aggregatorService;

    public SpotsController(AggregatorService aggregatorService) {
        this.aggregatorService = aggregatorService;
    }

    @GetMapping("spots")
    public Flux<Spot> spots() {
        return Flux.fromIterable(aggregatorService.getSpots());
    }

    @GetMapping("spots/{id}")
    public Mono<ResponseEntity<Spot>> spot(@PathVariable int id) {
        return Mono
                .justOrEmpty(aggregatorService.getSpotById(id))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .doOnSuccess(_ -> aggregatorService.fetchForecastsForAllModels(id));
    }

    @GetMapping("spots/{id}/{model}")
    public Mono<ResponseEntity<Spot>> spot(@PathVariable int id, @PathVariable String model) {
        return Mono
                .justOrEmpty(aggregatorService.getSpotById(id, ForecastModel.valueOfGracefully(model)))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .doOnSuccess(_ -> aggregatorService.fetchForecastsForAllModels(id));
    }

    @GetMapping("health")
    public Mono<Map<String, String>> health() {
        return Mono.just(Map.of("status", "UP"));
    }
}
