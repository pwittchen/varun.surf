package com.github.pwittchen.varun.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public record Spot(
        String name,
        String country,
        String windguruUrl,
        String windfinderUrl,
        String icmUrl,
        String webcamUrl,
        String locationUrl,
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = CurrentConditionsEmptyFilter.class)
        CurrentConditions currentConditions,
        List<Forecast> forecast,
        String aiAnalysis,
        SpotInfo spotInfo,
        String lastUpdated
) {
    public int wgId() {
        String[] parts = this.windguruUrl.split("/");
        return Integer.parseInt(parts[parts.length - 1]);
    }

    public Spot withUpdatedTimestamp() {
        return new Spot(
                this.name,
                this.country,
                this.windguruUrl,
                this.windfinderUrl,
                this.icmUrl,
                this.webcamUrl,
                this.locationUrl,
                this.currentConditions,
                this.forecast,
                this.aiAnalysis,
                this.spotInfo,
                ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"))
        );
    }

}
