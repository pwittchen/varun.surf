package com.github.pwittchen.varun.controller;

import com.github.pwittchen.varun.model.Spot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/")
public class SpotsController {

    @GetMapping("spots")
    public Flux<Spot> spots() {
        return Flux.empty();
    }
}
