package com.github.pwittchen.varun.model.spot;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.pwittchen.varun.model.live.CurrentConditions;
import com.github.pwittchen.varun.model.live.filter.CurrentConditionsEmptyFilter;
import com.github.pwittchen.varun.model.forecast.Forecast;
import com.github.pwittchen.varun.model.map.Coordinates;
import com.github.pwittchen.varun.model.sponsor.Sponsor;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;

public record Spot(
        String name,
        String country,
        String windguruUrl,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        String windguruFallbackUrl,
        String windfinderUrl,
        String icmUrl,
        String webcamUrl,
        String locationUrl,
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = CurrentConditionsEmptyFilter.class)
        CurrentConditions currentConditions,
        List<CurrentConditions> currentConditionsHistory,

        List<Forecast> forecast,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<Forecast> forecastHourly,
        String aiAnalysisEn,
        String aiAnalysisPl,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        String spotPhotoUrl,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Coordinates coordinates,
        SpotInfo spotInfo,
        SpotInfo spotInfoPL,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<Sponsor> sponsors,
        String lastUpdated
) {
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    public Spot {
        forecast = forecast == null ? new LinkedList<>() : new LinkedList<>(forecast);
        forecastHourly = forecastHourly == null ? new LinkedList<>() : new LinkedList<>(forecastHourly);
        sponsors = sponsors == null ? new LinkedList<>() : new LinkedList<>(sponsors);
    }

    @JsonProperty("wgId")
    public int wgId() {
        if (this.windguruUrl == null || this.windguruUrl.isEmpty()) {
            // Generate deterministic ID based on spot name and country
            return generateDeterministicId();
        }
        String[] parts = this.windguruUrl.split("/");
        return Integer.parseInt(parts[parts.length - 1]);
    }

    /**
     * Generates a deterministic positive integer ID based on spot name and country.
     * This is used when windguruUrl is empty (no windguru station available).
     * The ID is guaranteed to be positive and consistent for the same spot data.
     */
    private int generateDeterministicId() {
        String seed = this.name + ":" + this.country;
        int hash = seed.hashCode();
        // Ensure positive value and avoid collision with typical windguru IDs (which are usually < 1_000_000)
        // Use a high range starting from 9_000_000 to avoid collisions
        return 9_000_000 + Math.abs(hash % 1_000_000);
    }

    /**
     * Returns the Windguru spot ID to use for fetching forecasts.
     * If windguruUrl is empty and a fallbackUrl is available, extracts ID from fallback.
     */
    public int forecastWgId() {
        if ((this.windguruUrl == null || this.windguruUrl.isEmpty())
                && windguruFallbackUrl != null && !windguruFallbackUrl.isEmpty()) {
            String[] fallbackParts = windguruFallbackUrl.split("/");
            return Integer.parseInt(fallbackParts[fallbackParts.length - 1]);
        }
        return wgId();
    }

    /**
     * Returns true if this spot uses a fallback URL for forecasts.
     */
    public boolean usesFallbackUrl() {
        return (this.windguruUrl == null || this.windguruUrl.isEmpty())
                && windguruFallbackUrl != null && !windguruFallbackUrl.isEmpty();
    }

    public Spot withUpdatedTimestamp() {
        return new Spot(
                this.name,
                this.country,
                this.windguruUrl,
                this.windguruFallbackUrl,
                this.windfinderUrl,
                this.icmUrl,
                this.webcamUrl,
                this.locationUrl,
                this.currentConditions,
                this.currentConditionsHistory,
                this.forecast,
                this.forecastHourly,
                this.aiAnalysisEn,
                this.aiAnalysisPl,
                this.spotPhotoUrl,
                this.coordinates,
                this.spotInfo,
                this.spotInfoPL,
                this.sponsors,
                currentTimestamp()
        );
    }

    public Spot withCurrentConditions(CurrentConditions currentConditions) {
        return new Spot(
                this.name,
                this.country,
                this.windguruUrl,
                this.windguruFallbackUrl,
                this.windfinderUrl,
                this.icmUrl,
                this.webcamUrl,
                this.locationUrl,
                currentConditions,
                this.currentConditionsHistory,
                this.forecast,
                this.forecastHourly,
                this.aiAnalysisEn,
                this.aiAnalysisPl,
                this.spotPhotoUrl,
                this.coordinates,
                this.spotInfo,
                this.spotInfoPL,
                this.sponsors,
                CurrentConditionsEmptyFilter.isEmpty(currentConditions)
                        ? this.lastUpdated
                        : currentTimestamp()
        );
    }

    public Spot withAiAnalysisEn(String aiAnalysisEn) {
        return new Spot(
                this.name,
                this.country,
                this.windguruUrl,
                this.windguruFallbackUrl,
                this.windfinderUrl,
                this.icmUrl,
                this.webcamUrl,
                this.locationUrl,
                this.currentConditions,
                this.currentConditionsHistory,
                this.forecast,
                this.forecastHourly,
                aiAnalysisEn,
                aiAnalysisPl,
                this.spotPhotoUrl,
                this.coordinates,
                this.spotInfo,
                this.spotInfoPL,
                this.sponsors,
                aiAnalysisEn != null && aiAnalysisEn.isEmpty()
                        ? this.lastUpdated
                        : currentTimestamp()
        );
    }

    public Spot withAiAnalysisPl(String aiAnalysisPl) {
        return new Spot(
                this.name,
                this.country,
                this.windguruUrl,
                this.windguruFallbackUrl,
                this.windfinderUrl,
                this.icmUrl,
                this.webcamUrl,
                this.locationUrl,
                this.currentConditions,
                this.currentConditionsHistory,
                this.forecast,
                this.forecastHourly,
                this.aiAnalysisEn,
                aiAnalysisPl,
                this.spotPhotoUrl,
                this.coordinates,
                this.spotInfo,
                this.spotInfoPL,
                this.sponsors,
                aiAnalysisPl != null && aiAnalysisPl.isEmpty()
                        ? this.lastUpdated
                        : currentTimestamp()
        );
    }

    public Spot withForecasts(List<Forecast> forecast, List<Forecast> forecastHourly) {
        return new Spot(
                this.name,
                this.country,
                this.windguruUrl,
                this.windguruFallbackUrl,
                this.windfinderUrl,
                this.icmUrl,
                this.webcamUrl,
                this.locationUrl,
                this.currentConditions,
                this.currentConditionsHistory,
                forecast,
                forecastHourly,
                this.aiAnalysisEn,
                this.aiAnalysisPl,
                this.spotPhotoUrl,
                this.coordinates,
                this.spotInfo,
                this.spotInfoPL,
                this.sponsors,
                currentTimestamp()
        );
    }

    public Spot withForecastHourly(List<Forecast> forecastHourly) {
        return new Spot(
                this.name,
                this.country,
                this.windguruUrl,
                this.windguruFallbackUrl,
                this.windfinderUrl,
                this.icmUrl,
                this.webcamUrl,
                this.locationUrl,
                this.currentConditions,
                this.currentConditionsHistory,
                this.forecast,
                forecastHourly,
                this.aiAnalysisEn,
                this.aiAnalysisPl,
                this.spotPhotoUrl,
                this.coordinates,
                this.spotInfo,
                this.spotInfoPL,
                this.sponsors,
                this.lastUpdated
        );
    }

    public Spot(
            String name,
            String country,
            String windguruUrl,
            String windguruFallbackUrl,
            String windfinderUrl,
            String icmUrl,
            String webcamUrl,
            String locationUrl,
            CurrentConditions currentConditions,
            List<CurrentConditions> currentConditionsHistory,
            List<Forecast> forecast,
            List<Forecast> forecastHourly,
            String aiAnalysisEn,
            String aiAnalysisPl,
            String embeddedMap,
            SpotInfo spotInfo,
            SpotInfo spotInfoPL,
            List<Sponsor> sponsors,
            String lastUpdated
    ) {
        this(
                name,
                country,
                windguruUrl,
                windguruFallbackUrl,
                windfinderUrl,
                icmUrl,
                webcamUrl,
                locationUrl,
                currentConditions,
                currentConditionsHistory,
                forecast,
                forecastHourly,
                aiAnalysisEn,
                aiAnalysisPl,
                null,
                null,
                spotInfo,
                spotInfoPL,
                sponsors,
                lastUpdated
        );
    }

    public Spot withSpotPhoto(String spotPhotoUrl) {
        return new Spot(
                this.name,
                this.country,
                this.windguruUrl,
                this.windguruFallbackUrl,
                this.windfinderUrl,
                this.icmUrl,
                this.webcamUrl,
                this.locationUrl,
                this.currentConditions,
                this.currentConditionsHistory,
                this.forecast,
                this.forecastHourly,
                this.aiAnalysisEn,
                this.aiAnalysisPl,
                spotPhotoUrl,
                this.coordinates,
                this.spotInfo,
                this.spotInfoPL,
                this.sponsors,
                this.lastUpdated
        );
    }

    public Spot withCoordinates(Coordinates coordinates) {
        return new Spot(
                this.name,
                this.country,
                this.windguruUrl,
                this.windguruFallbackUrl,
                this.windfinderUrl,
                this.icmUrl,
                this.webcamUrl,
                this.locationUrl,
                this.currentConditions,
                this.currentConditionsHistory,
                this.forecast,
                this.forecastHourly,
                this.aiAnalysisEn,
                this.aiAnalysisPl,
                this.spotPhotoUrl,
                coordinates,
                this.spotInfo,
                this.spotInfoPL,
                this.sponsors,
                this.lastUpdated
        );
    }

    public Spot withSponsors(List<Sponsor> sponsors) {
        return new Spot(
                this.name,
                this.country,
                this.windguruUrl,
                this.windguruFallbackUrl,
                this.windfinderUrl,
                this.icmUrl,
                this.webcamUrl,
                this.locationUrl,
                this.currentConditions,
                this.currentConditionsHistory,
                this.forecast,
                this.forecastHourly,
                this.aiAnalysisEn,
                this.aiAnalysisPl,
                this.spotPhotoUrl,
                this.coordinates,
                this.spotInfo,
                this.spotInfoPL,
                sponsors,
                this.lastUpdated
        );
    }

    public Spot withCurrentConditionsHistory(List<CurrentConditions> currentConditionsHistory) {
        return new Spot(
                this.name,
                this.country,
                this.windguruUrl,
                this.windguruFallbackUrl,
                this.windfinderUrl,
                this.icmUrl,
                this.webcamUrl,
                this.locationUrl,
                this.currentConditions,
                currentConditionsHistory,
                this.forecast,
                this.forecastHourly,
                this.aiAnalysisEn,
                this.aiAnalysisPl,
                this.spotPhotoUrl,
                this.coordinates,
                this.spotInfo,
                this.spotInfoPL,
                this.sponsors,
                this.lastUpdated
        );
    }

    private static String currentTimestamp() {
        return ZonedDateTime.now().format(TIMESTAMP_FORMATTER);
    }
}
