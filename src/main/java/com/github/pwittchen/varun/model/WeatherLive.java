package com.github.pwittchen.varun.model;

public record WeatherLive(
        String date,
        int wind,
        int gusts,
        WindDirection direction,
        int temp,
        int precipitation,
        float wave
) {
}
