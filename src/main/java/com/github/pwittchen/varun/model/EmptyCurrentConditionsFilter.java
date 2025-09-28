package com.github.pwittchen.varun.model;

public final class EmptyCurrentConditionsFilter {
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof LiveConditions(
                String date,
                int wind,
                int gusts,
                String direction,
                int temp,
                int precipitation
        ))) return false;
        return date == null && direction == null && wind == 0 && gusts == 0 && temp == 0 && precipitation == 0;
    }
}
