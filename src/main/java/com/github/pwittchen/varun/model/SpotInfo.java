package com.github.pwittchen.varun.model;

public record SpotInfo(
        String type,
        String bestWind,
        String waterTemp,
        String experience,
        String launch,
        String hazards,
        String season,
        String description
) {
}
