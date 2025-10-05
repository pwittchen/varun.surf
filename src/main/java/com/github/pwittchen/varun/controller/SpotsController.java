package com.github.pwittchen.varun.controller;

import com.github.pwittchen.varun.model.Spot;
import com.github.pwittchen.varun.service.AggregatorService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/")
public class SpotsController {
    private final AggregatorService aggregatorService;

    ChatClient chatClient;

    public SpotsController(AggregatorService aggregatorService, ChatClient chatClient) {
        this.aggregatorService = aggregatorService;
        this.chatClient = chatClient;
    }

    @GetMapping("spots")
    public Flux<Spot> spots() {
        return Flux.fromIterable(aggregatorService.getSpots());
    }

    //PLEASE NOTE: you need to run ollama serve command in order to use this
    //TODO: this is just a placeholder example - it needs to be moved to aggregator and then used there in the right way

    @GetMapping("ai")
    public Mono<String> ai() {
        return chatClient
                .prompt()
                .user("tell me a joke")
                .stream()
                .content()
                .collectList()
                .map(list -> String.join("", list));
    }

    @GetMapping("health")
    public Mono<Map<String, String>> health() {
        return Mono.just(Map.of("status", "UP"));
    }
}
