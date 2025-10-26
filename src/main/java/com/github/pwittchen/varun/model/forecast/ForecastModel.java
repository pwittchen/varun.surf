package com.github.pwittchen.varun.model.forecast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    IFS;

    private static final Logger log = LoggerFactory.getLogger(ForecastModel.class);

     public static ForecastModel valueOfGracefully(String model) {
        try {
            return ForecastModel.valueOf(model.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.error("Unknown forecast model: {}", model);
            log.error("Fallback to default model: {}", ForecastModel.GFS);
            return ForecastModel.GFS;
        }
    }
}
