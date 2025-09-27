package com.github.pwittchen.varun.model;

import java.util.List;

public record Spot(
        String name,
        String country,
        String windguruUrl,
        String windFinderUrl,
        String icmUrl,
        String webcamUrl,
        String locationUrl,
        LiveConditions currentConditions,
        List<Forecast> forecast
) {
    public String wgId() {
        String[] parts = this.windguruUrl.split("/");
        return parts[parts.length - 1];
    }
}
