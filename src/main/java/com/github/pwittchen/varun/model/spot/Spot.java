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
        String windfinderUrl,
        String icmUrl,
        String webcamUrl,
        String locationUrl,
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = CurrentConditionsEmptyFilter.class)
        CurrentConditions currentConditions,
        List<Forecast> forecast,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<Forecast> forecastHourly,
        String aiAnalysisEn,
        String aiAnalysisPl,
        String embeddedMap,
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
        String[] parts = this.windguruUrl.split("/");
        return Integer.parseInt(parts[parts.length - 1]);
    }

    public Spot withUpdatedTimestamp() {
        return new Spot(
                this.name,
                this.country,
                this.windguruUrl,
                this.windfinderUrl,
                this.icmUrl,
                this.webcamUrl,
                this.locationUrl,
                this.currentConditions,
                this.forecast,
                this.forecastHourly,
                this.aiAnalysisEn,
                this.aiAnalysisPl,
                this.embeddedMap,
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
                this.windfinderUrl,
                this.icmUrl,
                this.webcamUrl,
                this.locationUrl,
                currentConditions,
                this.forecast,
                this.forecastHourly,
                this.aiAnalysisEn,
                this.aiAnalysisPl,
                this.embeddedMap,
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
                this.windfinderUrl,
                this.icmUrl,
                this.webcamUrl,
                this.locationUrl,
                this.currentConditions,
                this.forecast,
                this.forecastHourly,
                aiAnalysisEn,
                aiAnalysisPl,
                this.embeddedMap,
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
                this.windfinderUrl,
                this.icmUrl,
                this.webcamUrl,
                this.locationUrl,
                this.currentConditions,
                this.forecast,
                this.forecastHourly,
                this.aiAnalysisEn,
                aiAnalysisPl,
                this.embeddedMap,
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
                this.windfinderUrl,
                this.icmUrl,
                this.webcamUrl,
                this.locationUrl,
                this.currentConditions,
                forecast,
                forecastHourly,
                this.aiAnalysisEn,
                this.aiAnalysisPl,
                this.embeddedMap,
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
                this.windfinderUrl,
                this.icmUrl,
                this.webcamUrl,
                this.locationUrl,
                this.currentConditions,
                this.forecast,
                forecastHourly,
                this.aiAnalysisEn,
                this.aiAnalysisPl,
                this.embeddedMap,
                this.spotPhotoUrl,
                this.coordinates,
                this.spotInfo,
                this.spotInfoPL,
                this.sponsors,
                this.lastUpdated
        );
    }

    public Spot withEmbeddedMap(String embeddedMap) {
        return new Spot(
                this.name,
                this.country,
                this.windguruUrl,
                this.windfinderUrl,
                this.icmUrl,
                this.webcamUrl,
                this.locationUrl,
                this.currentConditions,
                this.forecast,
                this.forecastHourly,
                this.aiAnalysisEn,
                this.aiAnalysisPl,
                embeddedMap,
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
            String windfinderUrl,
            String icmUrl,
            String webcamUrl,
            String locationUrl,
            CurrentConditions currentConditions,
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
                windfinderUrl,
                icmUrl,
                webcamUrl,
                locationUrl,
                currentConditions,
                forecast,
                forecastHourly,
                aiAnalysisEn,
                aiAnalysisPl,
                embeddedMap,
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
                this.windfinderUrl,
                this.icmUrl,
                this.webcamUrl,
                this.locationUrl,
                this.currentConditions,
                this.forecast,
                this.forecastHourly,
                this.aiAnalysisEn,
                this.aiAnalysisPl,
                this.embeddedMap,
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
                this.windfinderUrl,
                this.icmUrl,
                this.webcamUrl,
                this.locationUrl,
                this.currentConditions,
                this.forecast,
                this.forecastHourly,
                this.aiAnalysisEn,
                this.aiAnalysisPl,
                this.embeddedMap,
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
                this.windfinderUrl,
                this.icmUrl,
                this.webcamUrl,
                this.locationUrl,
                this.currentConditions,
                this.forecast,
                this.forecastHourly,
                this.aiAnalysisEn,
                this.aiAnalysisPl,
                this.embeddedMap,
                this.spotPhotoUrl,
                this.coordinates,
                this.spotInfo,
                this.spotInfoPL,
                sponsors,
                this.lastUpdated
        );
    }

    private static String currentTimestamp() {
        return ZonedDateTime.now().format(TIMESTAMP_FORMATTER);
    }
}
