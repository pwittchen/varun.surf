package com.github.pwittchen.varun.model;

public record CurrentConditions(
        String date,
        int wind,
        int gusts,
        String  direction,
        int temp,
        int precipitation
) {
}
