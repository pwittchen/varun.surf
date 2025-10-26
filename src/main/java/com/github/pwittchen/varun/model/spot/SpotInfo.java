package com.github.pwittchen.varun.model.spot;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record SpotInfo(
        String type,
        String bestWind,
        String waterTemp,
        String experience,
        String launch,
        String hazards,
        String season,
        String description,
        @JsonIgnore
        String llmComment
) {
}
