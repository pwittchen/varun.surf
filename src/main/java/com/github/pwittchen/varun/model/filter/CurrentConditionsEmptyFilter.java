package com.github.pwittchen.varun.model.filter;

import com.github.pwittchen.varun.model.CurrentConditions;

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

    public static boolean isEmpty(CurrentConditions c) {
        if (c == null) return true;
        return c.date() == null && c.direction() == null && c.wind() == 0 && c.gusts() == 0 && c.temp() == 0;
    }
}
