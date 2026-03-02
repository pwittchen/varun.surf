package com.github.pwittchen.varun.model;

import com.github.pwittchen.varun.model.forecast.ForecastModel;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

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
    void shouldReturnGfsForNullModel() {
        ForecastModel result = ForecastModel.fromModelKey(null);
        assertThat(result).isEqualTo(ForecastModel.GFS);
    }

    @Test
    void shouldReturnGfsForEmptyModel() {
        ForecastModel result = ForecastModel.valueOfGracefully("");
        assertThat(result).isEqualTo(ForecastModel.GFS);
    }

    @Test
    void shouldContainGfsAndIfs() {
        ForecastModel[] models = ForecastModel.values();
        assertThat(models).asList().contains(ForecastModel.GFS);
        assertThat(models).asList().contains(ForecastModel.IFS);
    }

    @Test
    void shouldHaveMoreThanTwoModels() {
        assertThat(ForecastModel.values().length).isGreaterThan(2);
    }

    @Test
    void shouldResolveFromModelKey() {
        assertThat(ForecastModel.fromModelKey("icon")).isEqualTo(ForecastModel.ICON);
        assertThat(ForecastModel.fromModelKey("harmeu")).isEqualTo(ForecastModel.HARM_EU);
        assertThat(ForecastModel.fromModelKey("hrrr")).isEqualTo(ForecastModel.HRRR);
    }

    @Test
    void shouldResolveFromModelKeyCaseInsensitive() {
        assertThat(ForecastModel.fromModelKey("ICON")).isEqualTo(ForecastModel.ICON);
        assertThat(ForecastModel.fromModelKey("Hrrr")).isEqualTo(ForecastModel.HRRR);
    }

    @Test
    void shouldHaveUniqueModelKeys() {
        Set<String> modelKeys = Arrays.stream(ForecastModel.values())
                .map(ForecastModel::modelKey)
                .collect(Collectors.toSet());
        assertThat(modelKeys).hasSize(ForecastModel.values().length);
    }

    @Test
    void shouldHaveModelKeyAndDisplayName() {
        for (ForecastModel model : ForecastModel.values()) {
            assertThat(model.modelKey()).isNotEmpty();
            assertThat(model.displayName()).isNotEmpty();
        }
    }
}
