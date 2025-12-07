package com.github.pwittchen.varun.model;

import com.github.pwittchen.varun.model.live.CurrentConditions;
import com.github.pwittchen.varun.model.forecast.Forecast;
import com.github.pwittchen.varun.model.spot.Spot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpotTest {

    @Test
    void shouldExtractWgIdFromWindguruUrl() {
        // given
        var spot = createSpot("https://windguru.cz/123456");

        // when
        var wgId = spot.wgId();

        // then
        assertThat(wgId).isEqualTo(123456);
    }

    @Test
    void shouldExtractWgIdFromComplexUrl() {
        // given
        var spot = createSpot("https://windguru.cz/station/42");

        // when
        var wgId = spot.wgId();

        // then
        assertThat(wgId).isEqualTo(42);
    }

    @Test
    void shouldThrowExceptionWhenUrlEndsWithSlash() {
        // given
        var spot = createSpot("https://windguru.cz/");

        // when/then
        assertThatThrownBy(() -> spot.wgId())
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void shouldThrowExceptionWhenUrlHasNonNumericId() {
        // given
        var spot = createSpot("https://windguru.cz/invalid");

        // when/then
        assertThatThrownBy(() -> spot.wgId())
                .isInstanceOf(NumberFormatException.class);
    }

    @Test
    void shouldGenerateDeterministicIdWhenWindguruUrlIsEmpty() {
        // given
        var spot = createSpotWithFallback("", "https://windguru.cz/123456");

        // when
        var wgId = spot.wgId();

        // then
        // The ID should be generated deterministically and be in the range 9_000_000+
        assertThat(wgId).isGreaterThanOrEqualTo(9_000_000);
        assertThat(wgId).isLessThan(10_000_000);

        // Creating the same spot again should produce the same ID
        var sameSpot = createSpotWithFallback("", "https://windguru.cz/123456");
        assertThat(sameSpot.wgId()).isEqualTo(wgId);
    }

    @Test
    void shouldReturnForecastWgIdFromFallbackUrl() {
        // given
        var spot = createSpotWithFallback("", "https://windguru.cz/123456");

        // when
        var forecastWgId = spot.forecastWgId();

        // then
        assertThat(forecastWgId).isEqualTo(123456);
    }

    @Test
    void shouldReturnRegularWgIdAsForecastWgIdWhenNoFallback() {
        // given
        var spot = createSpot("https://windguru.cz/789");

        // when
        var forecastWgId = spot.forecastWgId();

        // then
        assertThat(forecastWgId).isEqualTo(789);
    }

    @Test
    void shouldReturnTrueForUsesFallbackUrlWhenWindguruUrlIsEmptyAndFallbackExists() {
        // given
        var spot = createSpotWithFallback("", "https://windguru.cz/123456");

        // when/then
        assertThat(spot.usesFallbackUrl()).isTrue();
    }

    @Test
    void shouldReturnFalseForUsesFallbackUrlWhenWindguruUrlIsNotEmpty() {
        // given
        var spot = createSpot("https://windguru.cz/123456");

        // when/then
        assertThat(spot.usesFallbackUrl()).isFalse();
    }

    @Test
    void shouldReturnFalseForUsesFallbackUrlWhenNoFallbackProvided() {
        // given
        var spot = createSpotWithFallback("", null);

        // when/then
        assertThat(spot.usesFallbackUrl()).isFalse();
    }

    @Test
    void shouldGenerateDifferentIdsForDifferentSpots() {
        // given
        var spot1 = createSpotWithNameAndFallback("Spot A", "Poland", "", null);
        var spot2 = createSpotWithNameAndFallback("Spot B", "Poland", "", null);

        // when
        var wgId1 = spot1.wgId();
        var wgId2 = spot2.wgId();

        // then
        assertThat(wgId1).isNotEqualTo(wgId2);
    }

    @Test
    void shouldUpdateTimestampWhenCallingWithUpdatedTimestamp() {
        // given
        var spot = createSpot("https://windguru.cz/123");
        var originalTimestamp = spot.lastUpdated();

        // when
        var updatedSpot = spot.withUpdatedTimestamp();

        // then
        assertThat(updatedSpot.lastUpdated()).isNotEqualTo(originalTimestamp);
        assertThat(updatedSpot.lastUpdated()).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} .*");
    }

    @Test
    void shouldPreserveAllFieldsWhenUpdatingTimestamp() {
        // given
        var currentConditions = new CurrentConditions("2025-01-01", 15, 20, "N", 25);
        var forecast = new ArrayList<>(List.of(new Forecast("Mon 12:00", 10.0, 15.0, "N", 12.5, 0.0)));
        var spot = new Spot(
                "Hel",
                "Poland",
                "https://windguru.cz/123",
                null, // windguruFallbackUrl
                "https://windfinder.com/test",
                "https://icm.edu.pl",
                "https://webcam.com",
                "https://maps.google.com",
                currentConditions,
                new ArrayList<>(),
                forecast,
                new ArrayList<>(),
                "AI analysis",
                null,
                null,
                null,
                null,
                null,
                null,
                "2025-01-01 00:00:00 UTC"
        );

        // when
        var updatedSpot = spot.withUpdatedTimestamp();

        // then
        assertThat(updatedSpot.name()).isEqualTo("Hel");
        assertThat(updatedSpot.country()).isEqualTo("Poland");
        assertThat(updatedSpot.windguruUrl()).isEqualTo("https://windguru.cz/123");
        assertThat(updatedSpot.currentConditions()).isEqualTo(currentConditions);
        assertThat(updatedSpot.forecast()).isEqualTo(forecast);
        assertThat(updatedSpot.aiAnalysisEn()).isEqualTo("AI analysis");
    }

    @Test
    void shouldUpdateTimestampWhenCurrentConditionsAreNotEmpty() {
        // given
        var spot = createSpot("https://windguru.cz/123");
        var currentConditions = new CurrentConditions("2025-01-01", 15, 20, "N", 25);

        // when
        var updatedSpot = spot.withCurrentConditions(currentConditions);

        // then
        assertThat(updatedSpot.currentConditions()).isEqualTo(currentConditions);
        assertThat(updatedSpot.lastUpdated()).isNotNull();
        assertThat(updatedSpot.lastUpdated()).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} .*");
    }

    @Test
    void shouldNotUpdateTimestampWhenCurrentConditionsAreEmpty() {
        // given
        var originalTimestamp = "2025-01-01 12:00:00 UTC";
        var spot = new Spot(
                "Hel",
                "Poland",
                "https://windguru.cz/123",
                null, // windguruFallbackUrl
                null, null, null, null,
                null,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                originalTimestamp
        );
        var emptyConditions = new CurrentConditions(null, 0, 0, null, 0);

        // when
        var updatedSpot = spot.withCurrentConditions(emptyConditions);

        // then
        assertThat(updatedSpot.currentConditions()).isEqualTo(emptyConditions);
        assertThat(updatedSpot.lastUpdated()).isEqualTo(originalTimestamp);
    }

    @Test
    void shouldNotUpdateTimestampWhenCurrentConditionsAreNull() {
        // given
        var originalTimestamp = "2025-01-01 12:00:00 UTC";
        var spot = new Spot(
                "Hel",
                "Poland",
                "https://windguru.cz/123",
                null, // windguruFallbackUrl
                null, null, null, null,
                null,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                originalTimestamp
        );

        // when
        var updatedSpot = spot.withCurrentConditions(null);

        // then
        assertThat(updatedSpot.currentConditions()).isNull();
        assertThat(updatedSpot.lastUpdated()).isEqualTo(originalTimestamp);
    }

    @Test
    void shouldUpdateTimestampWhenAiAnalysisIsNotEmpty() {
        // given
        var spot = createSpot("https://windguru.cz/123");
        var aiAnalysis = "Good conditions for kitesurfing";

        // when
        var updatedSpot = spot.withAiAnalysisEn(aiAnalysis);

        // then
        assertThat(updatedSpot.aiAnalysisEn()).isEqualTo(aiAnalysis);
        assertThat(updatedSpot.lastUpdated()).isNotNull();
        assertThat(updatedSpot.lastUpdated()).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} .*");
    }

    @Test
    void shouldNotUpdateTimestampWhenAiAnalysisIsEmpty() {
        // given
        var originalTimestamp = "2025-01-01 12:00:00 UTC";
        var spot = new Spot(
                "Hel",
                "Poland",
                "https://windguru.cz/123",
                null, // windguruFallbackUrl
                null, null, null, null,
                null,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                originalTimestamp
        );

        // when
        var updatedSpot = spot.withAiAnalysisEn("");

        // then
        assertThat(updatedSpot.aiAnalysisEn()).isEmpty();
        assertThat(updatedSpot.lastUpdated()).isEqualTo(originalTimestamp);
    }

    @Test
    void shouldPreserveAllFieldsWhenUpdatingAiAnalysis() {
        // given
        var currentConditions = new CurrentConditions("2025-01-01", 15, 20, "N", 25);
        var forecast = new ArrayList<>(List.of(new Forecast("Mon 12:00", 10.0, 15.0, "N", 12.5, 0.0)));
        var spot = new Spot(
                "Hel",
                "Poland",
                "https://windguru.cz/123",
                null, // windguruFallbackUrl
                "https://windfinder.com/test",
                "https://icm.edu.pl",
                "https://webcam.com",
                "https://maps.google.com",
                currentConditions,
                new ArrayList<>(),
                forecast,
                new ArrayList<>(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "2025-01-01 00:00:00 UTC"
        );

        // when
        var updatedSpot = spot.withAiAnalysisEn("New AI analysis");

        // then
        assertThat(updatedSpot.name()).isEqualTo("Hel");
        assertThat(updatedSpot.country()).isEqualTo("Poland");
        assertThat(updatedSpot.windguruUrl()).isEqualTo("https://windguru.cz/123");
        assertThat(updatedSpot.currentConditions()).isEqualTo(currentConditions);
        assertThat(updatedSpot.forecast()).isEqualTo(forecast);
    }

    @Test
    void shouldPreserveAllFieldsWhenUpdatingCurrentConditions() {
        // given
        var originalForecast = new ArrayList<>(List.of(new Forecast("Mon 12:00", 10.0, 15.0, "N", 12.5, 0.0)));
        var originalHourly = List.of(new Forecast("Mon 12:00", 10.0, 15.0,  "N", 12.5, 0.0));
        var spot = new Spot(
                "Hel",
                "Poland",
                "https://windguru.cz/123",
                null, // windguruFallbackUrl
                "https://windfinder.com/test",
                "https://icm.edu.pl",
                "https://webcam.com",
                "https://maps.google.com",
                null,
                new ArrayList<>(),
                originalForecast,
                originalHourly,
                "AI analysis",
                null,
                null,
                null,
                null,
                null,
                null,
                "2025-01-01 00:00:00 UTC"
        );
        var newConditions = new CurrentConditions("2025-01-02", 18, 25, "NW", 22);

        // when
        var updatedSpot = spot.withCurrentConditions(newConditions);

        // then
        assertThat(updatedSpot.name()).isEqualTo("Hel");
        assertThat(updatedSpot.country()).isEqualTo("Poland");
        assertThat(updatedSpot.windguruUrl()).isEqualTo("https://windguru.cz/123");
        assertThat(updatedSpot.forecast()).isEqualTo(originalForecast);
        assertThat(updatedSpot.forecastHourly()).isEqualTo(originalHourly);
        assertThat(updatedSpot.aiAnalysisEn()).isEqualTo("AI analysis");
    }

    @Test
    void shouldUpdateForecastsAndHourlyData() {
        // given
        var spot = createSpot("https://windguru.cz/123");
        var dailyForecast = List.of(new Forecast("Today", 10.0, 12.0, "N", 15.0, 0.5));
        var hourlyForecast = List.of(new Forecast("Mon 01h", 9.0, 11.0, "N", 14.0, 0.1));

        // when
        var updatedSpot = spot.withForecasts(dailyForecast, hourlyForecast);

        // then
        assertThat(updatedSpot.forecast()).containsExactlyElementsOf(dailyForecast);
        assertThat(updatedSpot.forecastHourly()).containsExactlyElementsOf(hourlyForecast);
        assertThat(updatedSpot.lastUpdated()).isNotNull();
    }

    @Test
    void shouldUpdateTimestampWhenAiAnalysisPlIsNotEmpty() {
        // given
        var spot = createSpot("https://windguru.cz/123");
        var aiAnalysisPl = "Dobre warunki do kitesurfingu";

        // when
        var updatedSpot = spot.withAiAnalysisPl(aiAnalysisPl);

        // then
        assertThat(updatedSpot.aiAnalysisPl()).isEqualTo(aiAnalysisPl);
        assertThat(updatedSpot.lastUpdated()).isNotNull();
        assertThat(updatedSpot.lastUpdated()).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} .*");
    }

    @Test
    void shouldNotUpdateTimestampWhenAiAnalysisPlIsEmpty() {
        // given
        var originalTimestamp = "2025-01-01 12:00:00 UTC";
        var spot = new Spot(
                "Hel",
                "Poland",
                "https://windguru.cz/123",
                null, // windguruFallbackUrl
                null, null, null, null,
                null,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                originalTimestamp
        );

        // when
        var updatedSpot = spot.withAiAnalysisPl("");

        // then
        assertThat(updatedSpot.aiAnalysisPl()).isEmpty();
        assertThat(updatedSpot.lastUpdated()).isEqualTo(originalTimestamp);
    }

    @Test
    void shouldPreserveAllFieldsWhenUpdatingAiAnalysisPl() {
        // given
        var currentConditions = new CurrentConditions("2025-01-01", 15, 20, "N", 25);
        var forecast = new ArrayList<>(List.of(new Forecast("Mon 12:00", 10.0, 15.0, "N", 12.5, 0.0)));
        var spot = new Spot(
                "Hel",
                "Poland",
                "https://windguru.cz/123",
                null, // windguruFallbackUrl
                "https://windfinder.com/test",
                "https://icm.edu.pl",
                "https://webcam.com",
                "https://maps.google.com",
                currentConditions,
                new ArrayList<>(),
                forecast,
                new ArrayList<>(),
                "English AI",
                null,
                null,
                null,
                null,
                null,
                null,
                "2025-01-01 00:00:00 UTC"
        );

        // when
        var updatedSpot = spot.withAiAnalysisPl("Polska analiza AI");

        // then
        assertThat(updatedSpot.name()).isEqualTo("Hel");
        assertThat(updatedSpot.country()).isEqualTo("Poland");
        assertThat(updatedSpot.windguruUrl()).isEqualTo("https://windguru.cz/123");
        assertThat(updatedSpot.currentConditions()).isEqualTo(currentConditions);
        assertThat(updatedSpot.forecast()).isEqualTo(forecast);
        assertThat(updatedSpot.aiAnalysisEn()).isEqualTo("English AI");
        assertThat(updatedSpot.aiAnalysisPl()).isEqualTo("Polska analiza AI");
    }

    @Test
    void shouldSupportBothEnAndPlAiAnalysis() {
        // given
        var spot = createSpot("https://windguru.cz/123");

        // when
        var spotWithEn = spot.withAiAnalysisEn("English analysis");
        var spotWithBoth = spotWithEn.withAiAnalysisPl("Polska analiza");

        // then
        assertThat(spotWithBoth.aiAnalysisEn()).isEqualTo("English analysis");
        assertThat(spotWithBoth.aiAnalysisPl()).isEqualTo("Polska analiza");
    }

    @Test
    void shouldReturnHourlyForecastWithoutChangingTimestamp() {
        // given
        var originalTimestamp = "2025-01-01 12:00:00 UTC";
        var spot = new Spot(
                "Hel",
                "Poland",
                "https://windguru.cz/123",
                null, // windguruFallbackUrl
                null, null, null, null,
                null,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                originalTimestamp
        );
        var hourlyForecast = List.of(new Forecast("Mon 01h", 9.0, 11.0,  "N", 14.0, 0.1));

        // when
        var updatedSpot = spot.withForecastHourly(hourlyForecast);

        // then
        assertThat(updatedSpot.forecastHourly()).containsExactlyElementsOf(hourlyForecast);
        assertThat(updatedSpot.lastUpdated()).isEqualTo(originalTimestamp);
    }

    private Spot createSpot(String windguruUrl) {
        return new Spot(
                "Test Spot",
                "Poland",
                windguruUrl,
                null, // windguruFallbackUrl
                null, null, null, null,
                null,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private Spot createSpotWithFallback(String windguruUrl, String windguruFallbackUrl) {
        return new Spot(
                "Test Spot",
                "Poland",
                windguruUrl,
                windguruFallbackUrl,
                null, null, null, null,
                null,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private Spot createSpotWithNameAndFallback(String name, String country, String windguruUrl, String windguruFallbackUrl) {
        return new Spot(
                name,
                country,
                windguruUrl,
                windguruFallbackUrl,
                null, null, null, null,
                null,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
