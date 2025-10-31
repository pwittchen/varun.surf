package com.github.pwittchen.varun.service.forecast;

import com.github.pwittchen.varun.mapper.WeatherForecastMapper;
import com.github.pwittchen.varun.model.forecast.Forecast;
import com.github.pwittchen.varun.model.forecast.ForecastData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;

class ForecastServiceTest {

    private ForecastService service;

    @BeforeEach
    void setUp() {
        WeatherForecastMapper mapper = new WeatherForecastMapper();
        service = new ForecastService(mapper);
    }

    @Test
    void shouldFetchForecastForValidSpotId() {
        Mono<List<Forecast>> result = service.getForecast(500760);

        StepVerifier.create(result)
                .assertNext(forecasts -> {
                    assertThat(forecasts).isNotNull();
                    assertThat(forecasts).hasSize(5);
                    assertThat(forecasts.get(0)).isNotNull();
                    assertThat(forecasts.get(0).date()).isNotEmpty();
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnForecastWithAllRequiredFields() {
        Mono<List<Forecast>> result = service.getForecast(500760);

        StepVerifier.create(result)
                .assertNext(forecasts -> {
                    assertThat(forecasts).isNotEmpty();
                    Forecast firstForecast = forecasts.get(0);
                    assertThat(firstForecast.date()).isNotEmpty();
                    assertThat(firstForecast.wind()).isAtLeast(0.0);
                    assertThat(firstForecast.gusts()).isAtLeast(0.0);
                    assertThat(firstForecast.direction()).isNotEmpty();
                    assertThat(firstForecast.temp()).isGreaterThan(-50.0);
                    assertThat(firstForecast.precipitation()).isAtLeast(0.0);
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnExactlyFiveForecasts() {
        Mono<List<Forecast>> result = service.getForecast(500760);

        StepVerifier.create(result)
                .assertNext(forecasts -> {
                    assertThat(forecasts).hasSize(5);
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnValidWindDirections() {
        Mono<List<Forecast>> result = service.getForecast(500760);

        StepVerifier.create(result)
                .assertNext(forecasts -> {
                    assertThat(forecasts).isNotEmpty();
                    for (Forecast forecast : forecasts) {
                        assertThat(forecast.direction()).matches("^(N|NE|E|SE|S|SW|W|NW)$");
                    }
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnForecastWithValidDateLabels() {
        Mono<List<Forecast>> result = service.getForecast(500760);

        StepVerifier.create(result)
                .assertNext(forecasts -> {
                    assertThat(forecasts).hasSize(5);
                    assertThat(forecasts.get(0).date()).isEqualTo("Today");
                    assertThat(forecasts.get(1).date()).isEqualTo("Tomorrow");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleDifferentSpotIds() {
        Mono<List<Forecast>> result1 = service.getForecast(500760);
        Mono<List<Forecast>> result2 = service.getForecast(14473);

        StepVerifier.create(result1)
                .assertNext(forecasts -> {
                    assertThat(forecasts).hasSize(5);
                })
                .verifyComplete();

        StepVerifier.create(result2)
                .assertNext(forecasts -> {
                    assertThat(forecasts).hasSize(5);
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnNonNegativeWindSpeed() {
        Mono<List<Forecast>> result = service.getForecast(500760);

        StepVerifier.create(result)
                .assertNext(forecasts -> {
                    for (Forecast forecast : forecasts) {
                        assertThat(forecast.wind()).isAtLeast(0.0);
                        assertThat(forecast.gusts()).isAtLeast(0.0);
                    }
                })
                .verifyComplete();
    }


    @Test
    void shouldReturnNonNegativePrecipitation() {
        Mono<List<Forecast>> result = service.getForecast(500760);

        StepVerifier.create(result)
                .assertNext(forecasts -> {
                    for (Forecast forecast : forecasts) {
                        assertThat(forecast.precipitation()).isAtLeast(0.0);
                    }
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnReasonableTemperatureRange() {
        Mono<List<Forecast>> result = service.getForecast(500760);

        StepVerifier.create(result)
                .assertNext(forecasts -> {
                    for (Forecast forecast : forecasts) {
                        assertThat(forecast.temp()).isAtLeast(-50.0);
                        assertThat(forecast.temp()).isAtMost(60.0);
                    }
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleInvalidSpotId() {
        Mono<List<Forecast>> result = service.getForecast(-1);

        StepVerifier.create(result)
                .assertNext(forecasts -> {
                    // May return empty or error, depends on API response
                    assertThat(forecasts).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleZeroSpotId() {
        Mono<List<Forecast>> result = service.getForecast(0);

        StepVerifier.create(result)
                .assertNext(forecasts -> {
                    assertThat(forecasts).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnHourlyForecasts() {
        Mono<ForecastData> result = service.getForecastData(500760);

        StepVerifier.create(result)
                .assertNext(data -> {
                    assertThat(data.hourlyGfs()).isNotNull();
                    assertThat(data.hourlyGfs()).isNotEmpty();
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnGfsModelForecastData() {
        Mono<ForecastData> result = service.getForecastData(500760, "gfs");

        StepVerifier.create(result)
                .assertNext(data -> {
                    assertThat(data).isNotNull();
                    assertThat(data.hourlyGfs()).isNotEmpty();
                    assertThat(data.hourlyIfs()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnIfsModelForecastData() {
        Mono<ForecastData> result = service.getForecastData(500760, "ifs");

        StepVerifier.create(result)
                .assertNext(data -> {
                    assertThat(data).isNotNull();
                    assertThat(data.hourlyIfs()).isNotEmpty();
                    assertThat(data.hourlyGfs()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleLowerCaseModelParameter() {
        Mono<ForecastData> resultGfs = service.getForecastData(500760, "gfs");
        Mono<ForecastData> resultIfs = service.getForecastData(500760, "ifs");

        StepVerifier.create(resultGfs)
                .assertNext(data -> {
                    assertThat(data.hourlyGfs()).isNotEmpty();
                })
                .verifyComplete();

        StepVerifier.create(resultIfs)
                .assertNext(data -> {
                    assertThat(data.hourlyIfs()).isNotEmpty();
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnDailyForecastsWithGfsModel() {
        Mono<ForecastData> result = service.getForecastData(500760, "gfs");

        StepVerifier.create(result)
                .assertNext(data -> {
                    assertThat(data.daily()).isNotNull();
                    assertThat(data.daily()).hasSize(5);
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnDailyForecastsWithIfsModel() {
        Mono<ForecastData> result = service.getForecastData(500760, "ifs");

        StepVerifier.create(result)
                .assertNext(data -> {
                    assertThat(data.daily()).isNotNull();
                    assertThat(data.daily()).hasSize(5);
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnValidHourlyForecastsForGfs() {
        Mono<ForecastData> result = service.getForecastData(500760, "gfs");

        StepVerifier.create(result)
                .assertNext(data -> {
                    assertThat(data.hourlyGfs()).isNotEmpty();
                    Forecast firstHourly = data.hourlyGfs().get(0);
                    assertThat(firstHourly.date()).isNotEmpty();
                    assertThat(firstHourly.wind()).isAtLeast(0.0);
                    assertThat(firstHourly.gusts()).isAtLeast(0.0);
                    assertThat(firstHourly.direction()).matches("^(N|NE|E|SE|S|SW|W|NW)$");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnValidHourlyForecastsForIfs() {
        Mono<ForecastData> result = service.getForecastData(500760, "ifs");

        StepVerifier.create(result)
                .assertNext(data -> {
                    assertThat(data.hourlyIfs()).isNotEmpty();
                    Forecast firstHourly = data.hourlyIfs().get(0);
                    assertThat(firstHourly.date()).isNotEmpty();
                    assertThat(firstHourly.wind()).isAtLeast(0.0);
                    assertThat(firstHourly.gusts()).isAtLeast(0.0);
                    assertThat(firstHourly.direction()).matches("^(N|NE|E|SE|S|SW|W|NW)$");
                })
                .verifyComplete();
    }

    @Test
    void shouldDefaultToGfsWhenNoModelSpecified() {
        Mono<ForecastData> resultDefault = service.getForecastData(500760);
        Mono<ForecastData> resultGfs = service.getForecastData(500760, "gfs");

        StepVerifier.create(resultDefault)
                .assertNext(dataDefault -> {
                    StepVerifier.create(resultGfs)
                            .assertNext(dataGfs -> {
                                assertThat(dataDefault.daily()).hasSize(dataGfs.daily().size());
                                assertThat(dataDefault.hourlyGfs()).isNotEmpty();
                            })
                            .verifyComplete();
                })
                .verifyComplete();
    }
}
