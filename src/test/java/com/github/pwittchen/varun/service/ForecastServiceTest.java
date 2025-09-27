package com.github.pwittchen.varun.service;

import com.github.pwittchen.varun.mapper.WeatherForecastMapper;
import com.github.pwittchen.varun.model.ForecastModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static com.google.common.truth.Truth.assertThat;

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
                .create(forecastService.getForecast(43, ForecastModel.GFS))
                .assertNext(weatherForecast -> {
                    assertThat(weatherForecast).isNotNull();
                })
                .verifyComplete();
    }
}