package com.github.pwittchen.varun.controller;

import com.github.pwittchen.varun.config.SessionAuthenticationFilter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class SessionController {

    @GetMapping("/session")
    public Mono<Map<String, String>> session(WebSession session) {
        session.getAttributes().putIfAbsent(SessionAuthenticationFilter.SESSION_INITIALIZED_ATTR, true);
        return Mono.just(Map.of("status", "OK"));
    }
}
