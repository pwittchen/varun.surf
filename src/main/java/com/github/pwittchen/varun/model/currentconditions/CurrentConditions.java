package com.github.pwittchen.varun.model.currentconditions;

public record CurrentConditions(
        String date,
        int wind,
        int gusts,
        String  direction,
        int temp
) {
}
