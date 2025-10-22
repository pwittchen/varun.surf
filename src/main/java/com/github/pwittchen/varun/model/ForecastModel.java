package com.github.pwittchen.varun.model;

/**
 * The ForecastModel enum represents different weather forecast models,
 * which are used to generate meteorological data and predictions.
 * <p>
 * More details: <a href="https://micro.windguru.cz/help.php">micro windguru help</a>
 * There are more models available in the windguru,
 * but this enum contains only these, which are currently used in this project.
 * In the future, more models might be added.
 */
public enum ForecastModel {
    GFS,
    IFS
}
