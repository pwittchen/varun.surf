package com.github.pwittchen.varun.model;

public enum ForecastModel {
    GFS, UWRFTAR, WRFGIS, ALL;

    @Override
    public String toString() {
        return super.toString().toLowerCase();
    }
}
