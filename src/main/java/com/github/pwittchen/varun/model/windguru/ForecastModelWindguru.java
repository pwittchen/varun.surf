package com.github.pwittchen.varun.model.windguru;

public enum ForecastModelWindguru {
    GFS, UWRFTAR, WRFGIS, ALL;

    @Override
    public String toString() {
        return super.toString().toLowerCase();
    }
}
