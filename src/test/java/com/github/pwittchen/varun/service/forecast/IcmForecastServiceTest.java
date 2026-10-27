package com.github.pwittchen.varun.service.forecast;

import com.github.pwittchen.varun.model.forecast.Forecast;
import com.github.pwittchen.varun.model.map.Coordinates;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;

class IcmForecastServiceTest {

    // 2026-10-27T07:00:00Z, 08:00 in Warsaw (CET)
    private static final long FIRST_TIMESTAMP = 1793084400L;

    private static final String AVAILABLE = """
            {"um4_120":[1792972800,1793016000],"um4_60":[1793016000,1793037600],"gfs":[1793037600]}
            """;

    private static final String METEOGRAM = """
            {"data":{
              "wind10_sd_true_prev_point":{"data":[5.0,10.0,nan],"first_timestamp":"%d","interval":3600},
              "wind10_dr_deg_true_prev_point":{"data":[180.0,292.0,90.0],"first_timestamp":"%d","interval":3600},
              "wind_gust_max":{"data":[8.0,7.0,9.0],"first_timestamp":"%d","interval":3600},
              "airtmp_point":{"data":[12.34,-327276480.0,10.0],"first_timestamp":"%d","interval":3600},
              "pcpttl_aver":{"data":[0.0,1.26,0.0],"first_timestamp":"%d","interval":3600},
              "cldtot_aver":{"data":[0.125,1.0,0.0],"first_timestamp":"%d","interval":3600},
              "slpres_point":{"data":[101325.0,100800.0,101000.0],"first_timestamp":"%d","interval":3600}
            },"fstart":"2026-10-27T06:00:00Z"}
            """.formatted(FIRST_TIMESTAMP, FIRST_TIMESTAMP, FIRST_TIMESTAMP, FIRST_TIMESTAMP,
            FIRST_TIMESTAMP, FIRST_TIMESTAMP, FIRST_TIMESTAMP);

    private MockWebServer mockWebServer;
    private IcmForecastService service;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        String apiUrl = mockWebServer.url("/").toString().replaceAll("/$", "");
        service = new IcmForecastService(new OkHttpClient(), apiUrl);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void shouldFetchTheLatestLongRunForThePoint() throws InterruptedException {
        // given
        mockWebServer.enqueue(new MockResponse().setBody(AVAILABLE));
        mockWebServer.enqueue(new MockResponse().setBody(METEOGRAM));

        // when
        Optional<List<Forecast>> forecasts = service.fetchForecast(new Coordinates(54.7, 18.55));

        // then
        assertThat(forecasts).isPresent();
        assertThat(forecasts.get()).hasSize(2);

        RecordedRequest available = mockWebServer.takeRequest();
        assertThat(available.getPath()).isEqualTo("/meteorograms/available");
        RecordedRequest meteogram = mockWebServer.takeRequest();
        assertThat(meteogram.getMethod()).isEqualTo("POST");
        assertThat(meteogram.getPath()).isEqualTo("/meteorograms/um4_120");
        var body = JsonParser.parseString(meteogram.getBody().readUtf8()).getAsJsonObject();
        assertThat(body.get("date").getAsLong()).isEqualTo(1793016000L);
        assertThat(body.getAsJsonObject("point").get("lat").getAsString()).isEqualTo("54.7000");
        assertThat(body.getAsJsonObject("point").get("lon").getAsString()).isEqualTo("18.5500");
    }

    @Test
    void shouldFallBackToTheShortRunWhenTheLongOneIsMissing() throws InterruptedException {
        // given
        mockWebServer.enqueue(new MockResponse().setBody("{\"um4_120\":[],\"um4_60\":[1793037600]}"));
        mockWebServer.enqueue(new MockResponse().setBody(METEOGRAM));

        // when
        Optional<List<Forecast>> forecasts = service.fetchForecast(new Coordinates(54.7, 18.55));

        // then
        assertThat(forecasts).isPresent();
        mockWebServer.takeRequest();
        assertThat(mockWebServer.takeRequest().getPath()).isEqualTo("/meteorograms/um4_60");
    }

    @Test
    void shouldReturnEmptyWhenTheApiFails() {
        // given
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        // when
        Optional<List<Forecast>> forecasts = service.fetchForecast(new Coordinates(54.7, 18.55));

        // then
        assertThat(forecasts).isEmpty();
    }

    @Test
    void shouldConvertTheSeriesIntoHourlyForecasts() {
        // when
        List<Forecast> forecasts = IcmForecastService.toForecasts(
                JsonParser.parseString(METEOGRAM.replace("nan", "null")).getAsJsonObject());

        // then - the third hour has no wind and is dropped
        assertThat(forecasts).hasSize(2);

        Forecast first = forecasts.get(0);
        assertThat(first.date()).isEqualTo("Tue 27 Oct 2026 08:00");
        assertThat(first.wind()).isEqualTo(9.7);
        assertThat(first.gusts()).isEqualTo(15.6);
        assertThat(first.direction()).isEqualTo("S");
        assertThat(first.temp()).isEqualTo(12.3);
        assertThat(first.precipitation()).isEqualTo(0.0);
        assertThat(first.cloudCoverPercent()).isEqualTo(12.5);
        assertThat(first.pressureHpa()).isEqualTo(1013.3);

        Forecast second = forecasts.get(1);
        assertThat(second.date()).isEqualTo("Tue 27 Oct 2026 09:00");
        assertThat(second.wind()).isEqualTo(19.4);
        // gusts below the mean wind are lifted to it
        assertThat(second.gusts()).isEqualTo(19.4);
        assertThat(second.direction()).isEqualTo("W");
        // the "no value" marker reads as zero
        assertThat(second.temp()).isEqualTo(0.0);
        assertThat(second.precipitation()).isEqualTo(1.3);
        assertThat(second.cloudCoverPercent()).isEqualTo(100.0);
        assertThat(second.pressureHpa()).isEqualTo(1008.0);
    }

    @Test
    void shouldLinkTheMeteogramPageOnlyForCoveredCountries() {
        assertThat(service.toMeteogramUrl(new Coordinates(50.29, 18.66), "Poland"))
                .hasValue("https://app.meteo.pl/meteogram/50.2900,18.6600");
        assertThat(service.toMeteogramUrl(new Coordinates(49.6, 15.5), "Czech Republic")).isPresent();
        assertThat(service.toMeteogramUrl(new Coordinates(36.0, -5.6), "Spain")).isEmpty();
        assertThat(service.toMeteogramUrl(null, "Poland")).isEmpty();
    }
}
