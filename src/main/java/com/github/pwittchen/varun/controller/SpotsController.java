package com.github.pwittchen.varun.controller;

import com.github.pwittchen.varun.model.Spot;
import com.github.pwittchen.varun.service.AggregatorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

    @GetMapping("health")
    public Mono<String> health() {
        return Mono.just("UP");
    }
}
