package com.github.pwittchen.varun.model.live;

public record CurrentConditions(
        String date,
        int wind,
        int gusts,
        String  direction,
        int temp
) {
}
