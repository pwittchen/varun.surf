package com.github.pwittchen.varun.service.logs;

public record LogEntry(
        long timestamp,
        String level,
        String loggerName,
        String message,
        String threadName
) {}
