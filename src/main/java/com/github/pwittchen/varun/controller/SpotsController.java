package com.github.pwittchen.varun.controller;

import com.github.pwittchen.varun.model.Spot;
import com.github.pwittchen.varun.service.AggregatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/")
public class SpotsController {

    private static final Logger log = LoggerFactory.getLogger(SpotsController.class);

    private final AggregatorService aggregatorService;

    public SpotsController(AggregatorService aggregatorService) {
        this.aggregatorService = aggregatorService;
    }

    @GetMapping("spots")
    public Flux<Spot> spots() {
        log.info("GET /api/v1/spots");
        return Flux.fromIterable(aggregatorService.getSpots());
    }

    @GetMapping("health")
    public Mono<String> health() {
        log.info("GET /api/v1/health");
        return Mono.just("UP");
    }
}
