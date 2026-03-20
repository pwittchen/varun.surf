package com.github.pwittchen.varun.mapper;

import com.github.pwittchen.varun.model.forecast.Forecast;
import com.github.pwittchen.varun.model.forecast.ForecastWg;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
                new ForecastWg("Mon 01. 00h", 10, 15, 180, 20, 0, 30, 1013),
                new ForecastWg("Mon 01. 03h", 12, 18, 190, 22, 1, 50, 1015)
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
                new ForecastWg("Mon 01. 00h", 10, 15, 90, 20, 0, 0, 1013),
                new ForecastWg("Mon 01. 03h", 12, 18, 90, 22, 0, 0, 1013),
                new ForecastWg("Tue 02. 00h", 15, 20, 270, 18, 2, 0, 1013),
                new ForecastWg("Tue 02. 03h", 18, 25, 270, 19, 3, 0, 1013)
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
                new ForecastWg("Mon 01. 00h", 10, 15, 0, 20, 0, 0, 1013),    // N
                new ForecastWg("Tue 02. 00h", 10, 15, 45, 20, 0, 0, 1013),   // NE
                new ForecastWg("Wed 03. 00h", 10, 15, 90, 20, 0, 0, 1013),   // E
                new ForecastWg("Thu 04. 00h", 10, 15, 135, 20, 0, 0, 1013),  // SE
                new ForecastWg("Fri 05. 00h", 10, 15, 180, 20, 0, 0, 1013)   // S
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
                new ForecastWg("Mon 01. 00h", 10, 15, 180, 20, 2, 0, 1013),
                new ForecastWg("Mon 01. 03h", 12, 18, 190, 22, 4, 0, 1013)
        );

        List<Forecast> result = mapper.toWeatherForecasts(forecasts);

        assertThat(result.get(0).precipitation()).isEqualTo(40.0);
    }

    @Test
    void shouldHandleNegativeDegrees() {
        List<ForecastWg> forecasts = List.of(
                new ForecastWg("Mon 01. 00h", 10, 15, -45, 20, 0, 0, 1013)
        );

        List<Forecast> result = mapper.toWeatherForecasts(forecasts);

        assertThat(result.get(0).direction()).isEqualTo("NW");
    }

    @Test
    void shouldHandleDegreesOver360() {
        List<ForecastWg> forecasts = List.of(
                new ForecastWg("Mon 01. 00h", 10, 15, 405, 20, 0, 0, 1013)  // 405 % 360 = 45 = NE
        );

        List<Forecast> result = mapper.toWeatherForecasts(forecasts);

        assertThat(result.get(0).direction()).isEqualTo("NE");
    }

    @Test
    void shouldFormatHourlyForecastWithFullDate() {
        List<ForecastWg> forecasts = List.of(
                new ForecastWg("Mon 01. 02h", 10, 15, 180, 20, 0, 0, 1013)
        );

        List<Forecast> result = mapper.toHourlyForecasts(forecasts);

        assertThat(result).hasSize(1);
        String formatted = result.getFirst().date();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE dd MMM yyyy HH:mm", Locale.ENGLISH);
        LocalDateTime parsed = LocalDateTime.parse(formatted, formatter);
        assertThat(parsed.getHour()).isEqualTo(2);
        assertThat(parsed.getDayOfMonth()).isEqualTo(1);
    }

    @Test
    void shouldMapCloudCoverAndPressureInDailyForecasts() {
        List<ForecastWg> forecasts = List.of(
                new ForecastWg("Mon 01. 00h", 10, 15, 180, 20, 0, 60, 1013),
                new ForecastWg("Mon 01. 03h", 12, 18, 190, 22, 1, 80, 1015)
        );

        List<Forecast> result = mapper.toWeatherForecasts(forecasts);

        assertThat(result.get(0).cloudCoverPercent()).isEqualTo(80.0);
        assertThat(result.get(0).pressureHpa()).isEqualTo(1015.0);
    }

    @Test
    void shouldMapCloudCoverAndPressureInHourlyForecasts() {
        List<ForecastWg> forecasts = List.of(
                new ForecastWg("Mon 01. 02h", 10, 15, 180, 20, 0, 45, 1018)
        );

        List<Forecast> result = mapper.toHourlyForecasts(forecasts);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().cloudCoverPercent()).isEqualTo(45.0);
        assertThat(result.getFirst().pressureHpa()).isEqualTo(1018.0);
    }

    @Test
    void shouldMapWaveFieldsInHourlyForecasts() {
        List<ForecastWg> forecasts = List.of(
                new ForecastWg("Mon 01. 02h", 10, 15, 180, 20, 0, 45, 1018, 1.5, 8.0, 270)
        );

        List<Forecast> result = mapper.toHourlyForecasts(forecasts);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().wave()).isEqualTo(1.5);
        assertThat(result.getFirst().wavePeriod()).isEqualTo(8.0);
        assertThat(result.getFirst().waveDirection()).isEqualTo("W");
    }

    @Test
    void shouldMapNullWaveFieldsInHourlyForecasts() {
        List<ForecastWg> forecasts = List.of(
                new ForecastWg("Mon 01. 02h", 10, 15, 180, 20, 0, 45, 1018, null, null, null)
        );

        List<Forecast> result = mapper.toHourlyForecasts(forecasts);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().wave()).isNull();
        assertThat(result.getFirst().wavePeriod()).isNull();
        assertThat(result.getFirst().waveDirection()).isNull();
    }

    @Test
    void shouldMapWaveFieldsInDailyForecasts() {
        List<ForecastWg> forecasts = List.of(
                new ForecastWg("Mon 01. 00h", 10, 15, 180, 20, 0, 30, 1013, 1.2, 7.0, 180),
                new ForecastWg("Mon 01. 03h", 12, 18, 190, 22, 1, 50, 1015, 1.8, 9.0, 180)
        );

        List<Forecast> result = mapper.toWeatherForecasts(forecasts);

        assertThat(result.get(0).wave()).isEqualTo(1.8);
        assertThat(result.get(0).wavePeriod()).isEqualTo(9.0);
        assertThat(result.get(0).waveDirection()).isEqualTo("S");
    }

    @Test
    void shouldReturnNullWaveFieldsForInlandSpots() {
        List<ForecastWg> forecasts = List.of(
                new ForecastWg("Mon 01. 00h", 10, 15, 180, 20, 0, 30, 1013),
                new ForecastWg("Mon 01. 03h", 12, 18, 190, 22, 1, 50, 1015)
        );

        List<Forecast> result = mapper.toWeatherForecasts(forecasts);

        assertThat(result.get(0).wave()).isNull();
        assertThat(result.get(0).wavePeriod()).isNull();
        assertThat(result.get(0).waveDirection()).isNull();
    }
}
