package com.github.pwittchen.varun.mapper;

import com.github.pwittchen.varun.model.Forecast;
import com.github.pwittchen.varun.model.ForecastWg;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

class WeatherForecastMapperTest {

    private WeatherForecastMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new WeatherForecastMapper();
    }

    @Test
    void shouldMapEmptyListToEmptyForecasts() {
        List<ForecastWg> forecasts = new ArrayList<>();
        List<Forecast> result = mapper.toWeatherForecasts(forecasts);

        assertThat(result).isNotNull();
        assertThat(result).hasSize(5);
    }

    @Test
    void shouldMapSingleDayForecasts() {
        List<ForecastWg> forecasts = List.of(
                new ForecastWg("Mon 01. 00h", 10, 15, 180, 20, 0),
                new ForecastWg("Mon 01. 03h", 12, 18, 190, 22, 1)
        );

        List<Forecast> result = mapper.toWeatherForecasts(forecasts);

        assertThat(result).isNotNull();
        assertThat(result).hasSize(5);
        assertThat(result.get(0).date()).isEqualTo("Today");
        assertThat(result.get(0).wind()).isEqualTo(12.0);
        assertThat(result.get(0).gusts()).isEqualTo(18.0);
        assertThat(result.get(0).direction()).isEqualTo("S");
        assertThat(result.get(0).temp()).isEqualTo(22.0);
        assertThat(result.get(0).precipitation()).isEqualTo(10.0);
    }

    @Test
    void shouldMapMultipleDayForecasts() {
        List<ForecastWg> forecasts = List.of(
                new ForecastWg("Mon 01. 00h", 10, 15, 90, 20, 0),
                new ForecastWg("Mon 01. 03h", 12, 18, 90, 22, 0),
                new ForecastWg("Tue 02. 00h", 15, 20, 270, 18, 2),
                new ForecastWg("Tue 02. 03h", 18, 25, 270, 19, 3)
        );

        List<Forecast> result = mapper.toWeatherForecasts(forecasts);

        assertThat(result).isNotNull();
        assertThat(result).hasSize(5);

        assertThat(result.get(0).date()).isEqualTo("Today");
        assertThat(result.get(0).wind()).isEqualTo(12.0);
        assertThat(result.get(0).direction()).isEqualTo("E");

        assertThat(result.get(1).date()).isEqualTo("Tomorrow");
        assertThat(result.get(1).wind()).isEqualTo(18.0);
        assertThat(result.get(1).direction()).isEqualTo("W");
    }

    @Test
    void shouldCalculateCorrectWindDirection() {
        List<ForecastWg> forecasts = List.of(
                new ForecastWg("Mon 01. 00h", 10, 15, 0, 20, 0),    // N
                new ForecastWg("Tue 02. 00h", 10, 15, 45, 20, 0),   // NE
                new ForecastWg("Wed 03. 00h", 10, 15, 90, 20, 0),   // E
                new ForecastWg("Thu 04. 00h", 10, 15, 135, 20, 0),  // SE
                new ForecastWg("Fri 05. 00h", 10, 15, 180, 20, 0)   // S
        );

        List<Forecast> result = mapper.toWeatherForecasts(forecasts);

        assertThat(result.get(0).direction()).isEqualTo("N");
        assertThat(result.get(1).direction()).isEqualTo("NE");
        assertThat(result.get(2).direction()).isEqualTo("E");
        assertThat(result.get(3).direction()).isEqualTo("SE");
        assertThat(result.get(4).direction()).isEqualTo("S");
    }

    @Test
    void shouldCalculateAveragePrecipitation() {
        List<ForecastWg> forecasts = List.of(
                new ForecastWg("Mon 01. 00h", 10, 15, 180, 20, 2),
                new ForecastWg("Mon 01. 03h", 12, 18, 190, 22, 4)
        );

        List<Forecast> result = mapper.toWeatherForecasts(forecasts);

        assertThat(result.get(0).precipitation()).isEqualTo(40.0);
    }

    @Test
    void shouldHandleNegativeDegrees() {
        List<ForecastWg> forecasts = List.of(
                new ForecastWg("Mon 01. 00h", 10, 15, -45, 20, 0)
        );

        List<Forecast> result = mapper.toWeatherForecasts(forecasts);

        assertThat(result.get(0).direction()).isEqualTo("NW");
    }

    @Test
    void shouldHandleDegreesOver360() {
        List<ForecastWg> forecasts = List.of(
                new ForecastWg("Mon 01. 00h", 10, 15, 405, 20, 0)  // 405 % 360 = 45 = NE
        );

        List<Forecast> result = mapper.toWeatherForecasts(forecasts);

        assertThat(result.get(0).direction()).isEqualTo("NE");
    }
}