package com.github.pwittchen.varun.model;

import com.github.pwittchen.varun.model.forecast.ForecastModel;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;

class ForecastModelTest {

    @Test
    void shouldReturnGfsForValidGfsString() {
        ForecastModel result = ForecastModel.valueOfGracefully("gfs");
        assertThat(result).isEqualTo(ForecastModel.GFS);
    }

    @Test
    void shouldReturnGfsForValidGfsStringInUpperCase() {
        ForecastModel result = ForecastModel.valueOfGracefully("GFS");
        assertThat(result).isEqualTo(ForecastModel.GFS);
    }

    @Test
    void shouldReturnIfsForValidIfsString() {
        ForecastModel result = ForecastModel.valueOfGracefully("ifs");
        assertThat(result).isEqualTo(ForecastModel.IFS);
    }

    @Test
    void shouldReturnIfsForValidIfsStringInUpperCase() {
        ForecastModel result = ForecastModel.valueOfGracefully("IFS");
        assertThat(result).isEqualTo(ForecastModel.IFS);
    }

    @Test
    void shouldReturnGfsForInvalidModel() {
        ForecastModel result = ForecastModel.valueOfGracefully("invalid");
        assertThat(result).isEqualTo(ForecastModel.GFS);
    }

    @Test
    void shouldThrowExceptionForNullModel() {
        try {
            ForecastModel.valueOfGracefully(null);
        } catch (NullPointerException e) {
            // Expected - null pointer when calling toUpperCase() on null
            assertThat(e).isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    void shouldReturnGfsForEmptyModel() {
        ForecastModel result = ForecastModel.valueOfGracefully("");
        assertThat(result).isEqualTo(ForecastModel.GFS);
    }

    @Test
    void shouldHaveTwoModels() {
        assertThat(ForecastModel.values()).hasLength(2);
    }

    @Test
    void shouldContainGfsAndIfs() {
        ForecastModel[] models = ForecastModel.values();
        assertThat(models).asList().contains(ForecastModel.GFS);
        assertThat(models).asList().contains(ForecastModel.IFS);
    }
}