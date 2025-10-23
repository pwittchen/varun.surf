package com.github.pwittchen.varun.controller;

import com.github.pwittchen.varun.model.Sponsor;
import com.github.pwittchen.varun.service.SponsorsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/")
public class SponsorsController {

    private final SponsorsService sponsorsService;

    public SponsorsController(SponsorsService sponsorsService) {
        this.sponsorsService = sponsorsService;
    }

    @GetMapping("sponsors")
    public Flux<Sponsor> sponsors() {
        return Flux.fromIterable(sponsorsService.getSponsors());
    }

    @GetMapping("sponsors/{id}")
    public Mono<ResponseEntity<Sponsor>> sponsor(@PathVariable int id) {
        return Mono
                .justOrEmpty(sponsorsService.getSponsorById(id))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("sponsors/main")
    public Flux<Sponsor> mainSponsors() {
        return Flux.fromIterable(sponsorsService.getMainSponsors());
    }
}