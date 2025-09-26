package com.github.pwittchen.varun.service;

import com.github.pwittchen.varun.model.windguru.ForecastModelWindguru;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static com.google.common.truth.Truth.assertThat;

class WeatherForecastServiceTest {

    private WeatherForecastService weatherForecastService;

    @BeforeEach
    void setUp() {
        weatherForecastService = new WeatherForecastService();
    }

    @Test
    void shouldParseWeatherForecast() {
        StepVerifier
                .create(weatherForecastService.getForecast(43, ForecastModelWindguru.GFS))
                .assertNext(weatherForecast -> {
                    assertThat(weatherForecast).isNotNull();
                })
                .verifyComplete();
    }
}