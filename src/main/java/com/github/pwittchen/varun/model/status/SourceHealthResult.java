package com.github.pwittchen.varun.model.status;

public record SourceHealthResult(
        String name,
        String url,
        String displayUrl,
        boolean ok,
        long latencyMs
) {
}