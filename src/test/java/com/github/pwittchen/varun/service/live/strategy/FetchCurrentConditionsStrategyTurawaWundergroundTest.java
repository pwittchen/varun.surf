package com.github.pwittchen.varun.service.live.strategy;

import com.github.pwittchen.varun.model.live.CurrentConditions;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

class FetchCurrentConditionsStrategyTurawaWundergroundTest {

    private static final String OBS_TIME = "Tue Oct 27 2026 10:06:19 GMT+0000 (Coordinated Universal Time)";

    private MockWebServer mockWebServer;
    private FetchCurrentConditionsStrategyTurawaWunderground strategy;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        strategy = new FetchCurrentConditionsStrategyTurawaWunderground(new OkHttpClient());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void shouldReturnTrueForTurawaSouthWgId() {
        assertThat(strategy.canProcess(SpotsJson.wgIdOf("Turawa Południe"))).isTrue();
    }

    @Test
    void shouldReturnFalseForOtherWgIds() {
        // the northern shore is served by the airmax station
        assertThat(strategy.canProcess(SpotsJson.wgIdOf("Turawa Północ"))).isFalse();
        assertThat(strategy.canProcess(859182)).isFalse();
        assertThat(strategy.canProcess(999999)).isFalse();
    }

    @Test
    void shouldNotBeFallbackStation() {
        assertThat(strategy.isFallbackStation()).isFalse();
    }

    @Test
    void shouldReturnCorrectUrl() {
        assertThat(strategy.getUrl(SpotsJson.wgIdOf("Turawa Południe")))
                .isEqualTo("https://www.wunderground.com/dashboard/pws/ISZCZE187");
    }

    @Test
    void shouldParseValidResponse() {
        mockWebServer.enqueue(new MockResponse()
                .setBody(createPage("m", "18.5", "27.8", "135", "23.4", OBS_TIME))
                .setResponseCode(200));

        Mono<CurrentConditions> result = strategy.fetchCurrentConditions(url());

        StepVerifier.create(result)
                .assertNext(conditions -> {
                    assertThat(conditions).isNotNull();
                    assertThat(conditions.date()).isEqualTo("2026-10-27 11:06:19"); // UTC+1 in October
                    assertThat(conditions.direction()).isEqualTo("SE");
                    assertThat(conditions.wind()).isEqualTo(10); // 18.5 km/h = 9.99 kts
                    assertThat(conditions.gusts()).isEqualTo(15); // 27.8 km/h = 15.01 kts
                    assertThat(conditions.temp()).isEqualTo(23);
                })
                .verifyComplete();
    }

    @Test
    void shouldConvertObservationTimeToSummerTime() {
        mockWebServer.enqueue(new MockResponse()
                .setBody(createPage("m", "10", "12", "180", "20",
                        "Sat Jul 25 2026 18:48:06 GMT+0000 (Coordinated Universal Time)"))
                .setResponseCode(200));

        StepVerifier.create(strategy.fetchCurrentConditions(url()))
                .assertNext(conditions -> assertThat(conditions.date()).isEqualTo("2026-07-25 20:48:06"))
                .verifyComplete();
    }

    @Test
    void shouldConvertDegreesToCardinalDirections() {
        int[] degrees = {0, 45, 90, 135, 180, 225, 270, 315, 359};
        String[] expected = {"N", "NE", "E", "SE", "S", "SW", "W", "NW", "N"};

        for (int i = 0; i < degrees.length; i++) {
            mockWebServer.enqueue(new MockResponse()
                    .setBody(createPage("m", "10", "12", String.valueOf(degrees[i]), "15", OBS_TIME))
                    .setResponseCode(200));

            String expectedDirection = expected[i];
            StepVerifier.create(strategy.fetchCurrentConditions(url()))
                    .assertNext(conditions -> assertThat(conditions.direction()).isEqualTo(expectedDirection))
                    .verifyComplete();
        }
    }

    @Test
    void shouldParseNegativeTemperature() {
        mockWebServer.enqueue(new MockResponse()
                .setBody(createPage("m", "10", "12", "180", "-5.2", OBS_TIME))
                .setResponseCode(200));

        StepVerifier.create(strategy.fetchCurrentConditions(url()))
                .assertNext(conditions -> assertThat(conditions.temp()).isEqualTo(-5))
                .verifyComplete();
    }

    @Test
    void shouldRejectNonMetricUnits() {
        // in imperial units the wind would be in mph, read as km/h it would be 40% too low
        mockWebServer.enqueue(new MockResponse()
                .setBody(createPage("e", "10", "12", "180", "50", OBS_TIME))
                .setResponseCode(200));

        StepVerifier.create(strategy.fetchCurrentConditions(url()))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleHttpError() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        StepVerifier.create(strategy.fetchCurrentConditions(url()))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleEmptyBody() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(204));

        StepVerifier.create(strategy.fetchCurrentConditions(url()))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandlePageWithoutWidgets() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("<html><body><div>ISZCZE187</div></body></html>")
                .setResponseCode(200));

        StepVerifier.create(strategy.fetchCurrentConditions(url()))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleEmptyWindSpeed() {
        mockWebServer.enqueue(new MockResponse()
                .setBody(createPage("m", "", "12", "180", "20", OBS_TIME))
                .setResponseCode(200));

        StepVerifier.create(strategy.fetchCurrentConditions(url()))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleMissingObservationTime() {
        String page = createPage("m", "10", "12", "180", "20", OBS_TIME)
                .replace("data-obs-time-utc=\"" + OBS_TIME + "\"", "");

        mockWebServer.enqueue(new MockResponse().setBody(page).setResponseCode(200));

        StepVerifier.create(strategy.fetchCurrentConditions(url()))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleMalformedObservationTime() {
        mockWebServer.enqueue(new MockResponse()
                .setBody(createPage("m", "10", "12", "180", "20", "yesterday"))
                .setResponseCode(200));

        StepVerifier.create(strategy.fetchCurrentConditions(url()))
                .expectError(RuntimeException.class)
                .verify();
    }

    private String url() {
        return mockWebServer.url("/dashboard/pws/ISZCZE187").toString();
    }

    // trimmed copy of the server-rendered dashboard; the widget attributes are
    // written without spaces between them, exactly as the page serves them
    private String createPage(String unit, String windSpeed, String windGust, String windDir, String temp, String obsTime) {
        return """
                <html><body>
                <pws-status-view data-time-string="11:06 AM CET" data-pws-id="ISZCZE187" \
                data-iana-time-zone="Europe/Warsaw" data-status="connected" data-obs-time-utc="%5$s">
                  <div class="text" id="pws-status-text">connected</div>
                </pws-status-view>
                <temp-widget-view data-pws-id="ISZCZE187"data-status="connected"data-unit="%1$s"data-temp="%4$s"\
                data-feels-like="%4$s"data-main-value="%4$s"aria-valuenow="%4$s"data-secondary-value="%4$s" class="widget-view">
                  <div class="main-value value-unit degrees" id="temp-widget-main-display">%4$s</div>
                </temp-widget-view>
                <wind-widget-view data-pws-id="ISZCZE187"data-status="connected"data-unit="%1$s"data-wind-speed="%2$s"\
                data-wind-gust="%3$s"data-wind-dir="%6$s"data-description="moderate"data-main-value="%2$s"\
                aria-valuenow="%2$s"data-secondary-value="%3$s" class="widget-view">
                </wind-widget-view>
                <rain-widget-view data-pws-id="ISZCZE187"data-status="connected"data-unit="%1$s"data-precip-rate="0"\
                data-precip-total="0"data-main-value="0"aria-valuenow="0"data-secondary-value="0" class="widget-view">
                </rain-widget-view>
                </body></html>
                """.formatted(unit, windSpeed, windGust, temp, obsTime, windDir);
    }
}
