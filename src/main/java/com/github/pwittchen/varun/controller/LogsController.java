package com.github.pwittchen.varun.controller;

import com.github.pwittchen.varun.service.logs.LogEntry;
import com.github.pwittchen.varun.service.logs.LogsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/")
public class LogsController {

    private final LogsService logsService;

    public LogsController(LogsService logsService) {
        this.logsService = logsService;
    }

    @GetMapping("logs")
    public Mono<List<LogEntry>> getLogs(
            @RequestParam(required = false) String level) {
        if (level != null && !level.isBlank()) {
            return Mono.just(logsService.getLogs(level));
        }
        return Mono.just(logsService.getLogs());
    }
}
