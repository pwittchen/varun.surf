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
        List<Forecast> forecast,
        String aiAnalysis,
        SpotInfo spotInfo
) {
    public int wgId() {
        String[] parts = this.windguruUrl.split("/");
        return Integer.parseInt(parts[parts.length - 1]);
    }
}
