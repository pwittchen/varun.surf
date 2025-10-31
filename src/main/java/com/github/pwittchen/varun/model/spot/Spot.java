package com.github.pwittchen.varun.model.spot;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.pwittchen.varun.model.live.CurrentConditions;
import com.github.pwittchen.varun.model.live.filter.CurrentConditionsEmptyFilter;
import com.github.pwittchen.varun.model.forecast.Forecast;

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
        String aiAnalysis,
        String embeddedMap,
        SpotInfo spotInfo,
        SpotInfo spotInfoPL,
        String lastUpdated
) {
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    public Spot {
        forecast = forecast == null ? new LinkedList<>() : new LinkedList<>(forecast);
        forecastHourly = forecastHourly == null ? new LinkedList<>() : new LinkedList<>(forecastHourly);
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
                this.aiAnalysis,
                this.embeddedMap,
                this.spotInfo,
                this.spotInfoPL,
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
                this.aiAnalysis,
                this.embeddedMap,
                this.spotInfo,
                this.spotInfoPL,
                CurrentConditionsEmptyFilter.isEmpty(currentConditions)
                        ? this.lastUpdated
                        : currentTimestamp()
        );
    }

    public Spot withAiAnalysis(String aiAnalysis) {
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
                aiAnalysis,
                this.embeddedMap,
                this.spotInfo,
                this.spotInfoPL,
                aiAnalysis != null && aiAnalysis.isEmpty()
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
                this.aiAnalysis,
                this.embeddedMap,
                this.spotInfo,
                this.spotInfoPL,
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
                this.aiAnalysis,
                this.embeddedMap,
                this.spotInfo,
                this.spotInfoPL,
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
                this.aiAnalysis,
                embeddedMap,
                this.spotInfo,
                this.spotInfoPL,
                this.lastUpdated
        );
    }

    private static String currentTimestamp() {
        return ZonedDateTime.now().format(TIMESTAMP_FORMATTER);
    }
}
