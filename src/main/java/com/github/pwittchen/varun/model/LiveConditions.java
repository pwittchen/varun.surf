package com.github.pwittchen.varun.model;

public record LiveConditions(
        String date,
        int wind,
        int gusts,
        String  direction,
        int temp,
        int precipitation
) {
}
