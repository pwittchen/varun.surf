package com.github.pwittchen.varun.service.forecast;

import com.github.pwittchen.varun.model.forecast.Forecast;
import com.github.pwittchen.varun.model.forecast.ForecastData;
import com.github.pwittchen.varun.model.forecast.ForecastModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;

class ForecastAverageCalculatorTest {

    @Test
    void shouldReturnEmptyListForEmptyForecastData() {
        var data = new ForecastData(List.of(), Map.of());

        var result = ForecastAverageCalculator.computeAverage(data);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyListForSingleModel() {
        var forecasts = List.of(new Forecast("Mon 01 Jan 2025 12:00", 10.0, 15.0, "N", 20.0, 0.5, 0, 0));
        var data = new ForecastData(List.of(), Map.of(ForecastModel.GFS, forecasts));

        var result = ForecastAverageCalculator.computeAverage(data);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldComputeAverageForTwoModelsWithSameTimeSlots() {
        var gfsForecasts = List.of(
                new Forecast("Mon 01 Jan 2025 12:00", 10.0, 16.0, "N", 20.0, 0.0, 0, 0)
        );
        var ifsForecasts = List.of(
                new Forecast("Mon 01 Jan 2025 12:00", 14.0, 20.0, "N", 22.0, 1.0, 0, 0)
        );
        var data = new ForecastData(List.of(), Map.of(
                ForecastModel.GFS, gfsForecasts,
                ForecastModel.IFS, ifsForecasts
        ));

        var result = ForecastAverageCalculator.computeAverage(data);

        assertThat(result).hasSize(1);
        var avg = result.get(0);
        assertThat(avg.date()).isEqualTo("Mon 01 Jan 2025 12:00");
        assertThat(avg.wind()).isEqualTo(12.0);
        assertThat(avg.gusts()).isEqualTo(18.0);
        assertThat(avg.temp()).isEqualTo(21.0);
        assertThat(avg.precipitation()).isEqualTo(0.5);
        assertThat(avg.direction()).isEqualTo("N");
    }

    @Test
    void shouldOnlyIncludeTimeSlotsPresentInTwoOrMoreModels() {
        var gfsForecasts = List.of(
                new Forecast("Mon 01 Jan 2025 12:00", 10.0, 15.0, "N", 20.0, 0.0, 0, 0),
                new Forecast("Mon 01 Jan 2025 15:00", 12.0, 18.0, "NE", 21.0, 0.0, 0, 0)
        );
        var ifsForecasts = List.of(
                new Forecast("Mon 01 Jan 2025 12:00", 14.0, 20.0, "N", 22.0, 1.0, 0, 0)
                // Note: no 15:00 slot in IFS
        );
        var data = new ForecastData(List.of(), Map.of(
                ForecastModel.GFS, gfsForecasts,
                ForecastModel.IFS, ifsForecasts
        ));

        var result = ForecastAverageCalculator.computeAverage(data);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).date()).isEqualTo("Mon 01 Jan 2025 12:00");
    }

    @Test
    void shouldHandleCircularDirectionAcross360Boundary() {
        // NW (315) + NE (45) should average to N (0), not S (180)
        var result = ForecastAverageCalculator.circularMeanDirection(List.of("NW", "NE"));

        assertThat(result).isEqualTo("N");
    }

    @Test
    void shouldHandleStandardDirectionAveraging() {
        // N (0) + E (90) should average to NE (45)
        var result = ForecastAverageCalculator.circularMeanDirection(List.of("N", "E"));

        assertThat(result).isEqualTo("NE");
    }

    @Test
    void shouldHandleSameDirections() {
        var result = ForecastAverageCalculator.circularMeanDirection(List.of("S", "S", "S"));

        assertThat(result).isEqualTo("S");
    }

    @Test
    void shouldReturnNForEmptyDirectionsList() {
        var result = ForecastAverageCalculator.circularMeanDirection(List.of());

        assertThat(result).isEqualTo("N");
    }

    @Test
    void shouldRoundValuesToOneDecimalPlace() {
        // Use values where the average produces a clear rounding case
        // (10.0 + 10.3) / 2 = 10.15 -> rounds to 10.2 (or 10.1 depending on float repr)
        // Use (10.0 + 11.0) / 2 = 10.5 -> rounds to 10.5 (exact)
        var gfsForecasts = List.of(
                new Forecast("Mon 01 Jan 2025 12:00", 10.33, 15.0, "N", 20.0, 0.0, 0, 0)
        );
        var ifsForecasts = List.of(
                new Forecast("Mon 01 Jan 2025 12:00", 10.33, 15.0, "N", 20.0, 0.0, 0, 0)
        );
        var iconForecasts = List.of(
                new Forecast("Mon 01 Jan 2025 12:00", 10.33, 15.0, "N", 20.0, 0.0, 0, 0)
        );
        var data = new ForecastData(List.of(), Map.of(
                ForecastModel.GFS, gfsForecasts,
                ForecastModel.IFS, ifsForecasts,
                ForecastModel.ICON, iconForecasts
        ));

        var result = ForecastAverageCalculator.computeAverage(data);

        assertThat(result).hasSize(1);
        // 10.33 average is 10.33, rounded to 1 decimal = 10.3
        assertThat(result.get(0).wind()).isEqualTo(10.3);
    }

    @Test
    void shouldComputeAverageWithThreeModels() {
        var gfs = List.of(new Forecast("Mon 01 Jan 2025 12:00", 10.0, 15.0, "N", 20.0, 0.0, 0, 0));
        var ifs = List.of(new Forecast("Mon 01 Jan 2025 12:00", 13.0, 18.0, "NE", 22.0, 1.0, 0, 0));
        var icon = List.of(new Forecast("Mon 01 Jan 2025 12:00", 16.0, 21.0, "E", 24.0, 2.0, 0, 0));

        var data = new ForecastData(List.of(), Map.of(
                ForecastModel.GFS, gfs,
                ForecastModel.IFS, ifs,
                ForecastModel.ICON, icon
        ));

        var result = ForecastAverageCalculator.computeAverage(data);

        assertThat(result).hasSize(1);
        var avg = result.get(0);
        assertThat(avg.wind()).isEqualTo(13.0);
        assertThat(avg.gusts()).isEqualTo(18.0);
        assertThat(avg.temp()).isEqualTo(22.0);
        assertThat(avg.precipitation()).isEqualTo(1.0);
        assertThat(avg.direction()).isEqualTo("NE");
    }

    @Test
    void shouldSkipEmptyModels() {
        var gfs = List.of(new Forecast("Mon 01 Jan 2025 12:00", 10.0, 15.0, "N", 20.0, 0.0, 0, 0));
        var ifs = List.of(new Forecast("Mon 01 Jan 2025 12:00", 14.0, 20.0, "N", 22.0, 1.0, 0, 0));
        List<Forecast> emptyIcon = List.of();

        var data = new ForecastData(List.of(), Map.of(
                ForecastModel.GFS, gfs,
                ForecastModel.IFS, ifs,
                ForecastModel.ICON, emptyIcon
        ));

        var result = ForecastAverageCalculator.computeAverage(data);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).wind()).isEqualTo(12.0);
    }

    @Test
    void shouldHandleOppositeDirections() {
        // S (180) + N (0) -> opposite directions, circular mean is ambiguous
        // Due to floating point, sin(180°) ≈ 1.2e-16, cos averages to 0, result is ~90° = E
        var result = ForecastAverageCalculator.circularMeanDirection(List.of("S", "N"));

        // The result is implementation-defined for perfectly opposite directions;
        // we just verify it returns a valid cardinal direction
        assertThat(result).isAnyOf("N", "NE", "E", "SE", "S", "SW", "W", "NW");
    }
}
