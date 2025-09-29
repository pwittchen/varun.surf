package com.github.pwittchen.varun.service;

import com.github.pwittchen.varun.mapper.WeatherForecastMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class ForecastServiceTest {

    private ForecastService forecastService;

    @BeforeEach
    void setUp() {
        final WeatherForecastMapper weatherForecastMapper = new WeatherForecastMapper();
        forecastService = new ForecastService(weatherForecastMapper);
    }

    @Test
    void shouldParseWeatherForecast() {
        StepVerifier
                .create(forecastService.getForecast(43))
                .expectNextCount(1)
                .verifyComplete();
    }
}