package com.github.pwittchen.varun.controller;

import com.github.pwittchen.varun.model.sponsor.Sponsor;
import com.github.pwittchen.varun.service.sponsors.SponsorsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/")
public class SponsorsController {

    private final SponsorsService sponsorsService;

    public SponsorsController(SponsorsService sponsorsService) {
        this.sponsorsService = sponsorsService;
    }

    @GetMapping("sponsors")
    public Flux<Sponsor> mainSponsors() {
        return Flux.fromIterable(sponsorsService.getMainSponsors());
    }
}