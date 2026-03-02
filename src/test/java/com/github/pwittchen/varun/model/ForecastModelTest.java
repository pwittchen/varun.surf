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
        ForecastModel result = ForecastModel.fromApiKey(null);
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
    void shouldResolveFromApiKey() {
        assertThat(ForecastModel.fromApiKey("icon")).isEqualTo(ForecastModel.ICON);
        assertThat(ForecastModel.fromApiKey("harmeu")).isEqualTo(ForecastModel.HARM_EU);
        assertThat(ForecastModel.fromApiKey("hrrr")).isEqualTo(ForecastModel.HRRR);
    }

    @Test
    void shouldResolveFromApiKeyCaseInsensitive() {
        assertThat(ForecastModel.fromApiKey("ICON")).isEqualTo(ForecastModel.ICON);
        assertThat(ForecastModel.fromApiKey("Hrrr")).isEqualTo(ForecastModel.HRRR);
    }

    @Test
    void shouldHaveUniqueApiKeys() {
        Set<String> apiKeys = Arrays.stream(ForecastModel.values())
                .map(ForecastModel::apiKey)
                .collect(Collectors.toSet());
        assertThat(apiKeys).hasSize(ForecastModel.values().length);
    }

    @Test
    void shouldHaveApiKeyAndDisplayName() {
        for (ForecastModel model : ForecastModel.values()) {
            assertThat(model.apiKey()).isNotEmpty();
            assertThat(model.displayName()).isNotEmpty();
        }
    }
}
