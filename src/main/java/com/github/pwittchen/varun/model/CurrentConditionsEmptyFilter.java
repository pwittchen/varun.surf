package com.github.pwittchen.varun.model;

public final class CurrentConditionsEmptyFilter {
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CurrentConditions(
                String date,
                int wind,
                int gusts,
                String direction,
                int temp
        ))) return false;
        return date == null && direction == null && wind == 0 && gusts == 0 && temp == 0;
    }
}
