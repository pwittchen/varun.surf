package com.github.pwittchen.varun.service;

import com.github.pwittchen.varun.model.Forecast;
import com.github.pwittchen.varun.model.CurrentConditions;
import com.github.pwittchen.varun.model.Spot;
import com.github.pwittchen.varun.provider.SpotsDataProvider;
import jakarta.annotation.PostConstruct;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.StructuredTaskScope;
import java.util.stream.Collectors;

@Service
public class AggregatorService {

    private static final Logger log = LoggerFactory.getLogger(AggregatorService.class);

    private List<Spot> spots;
    private Map<Integer, List<Forecast>> forecasts;
    private Map<Integer, CurrentConditions> currentConditions;
    private Map<Integer, String> aiAnalysis;

    private final SpotsDataProvider spotsDataProvider;
    private final ForecastService forecastService;
    private final CurrentConditionsService currentConditionsService;
    private final AiService aiService;

    public AggregatorService(
            SpotsDataProvider spotsDataProvider,
            ForecastService forecastService,
            CurrentConditionsService currentConditionsService,
            AiService aiService) {
        this.spots = new ArrayList<>();
        this.forecasts = new HashMap<>();
        this.currentConditions = new HashMap<>();
        this.aiAnalysis = new HashMap<>();
        this.spotsDataProvider = spotsDataProvider;
        this.forecastService = forecastService;
        this.currentConditionsService = currentConditionsService;
        this.aiService = aiService;
    }

    @PostConstruct
    void init() {
        log.info("Loading spots");
        spots = spotsDataProvider
                .getSpots()
                .toStream()
                .toList();
    }

    @Scheduled(fixedRate = 3 * 60 * 60 * 1000)
    void fetchForecastsEveryThreeHours() {
        try {
            log.info("Fetching forecasts");
            fetchForecasts();
        } catch (Exception e) {
            //todo: consider using resilience4j and add retry here
            log.error("Could not fetch forecasts", e);
        }
    }

    public List<Spot> getSpots() {
        return spots;
    }

    @SuppressWarnings("preview")
    private void fetchForecasts() throws Exception {
        var spotWgIds = spots.stream().map(Spot::wgId).toList();

        try (var scope = new StructuredTaskScope.ShutdownOnFailure("forecast", Thread.ofVirtual().factory())) {
            var tasks = spotWgIds
                    .stream()
                    .map(id -> scope.fork(() -> Pair.with(id, forecastService.getForecast(id).block())))
                    .toList();

            scope.join().throwIfFailed();

            updateSpotsAndForecasts(tasks);
        }
    }

    @SuppressWarnings("preview")
    private void updateSpotsAndForecasts(List<StructuredTaskScope.Subtask<Pair<Integer, List<Forecast>>>> tasks) {
        forecasts.clear();
        forecasts = tasks
                .stream()
                .map(StructuredTaskScope.Subtask::get)
                .filter(pair -> !pair.getValue1().isEmpty())
                .collect(Collectors.toMap(Pair::getValue0, Pair::getValue1));

        spots.forEach(spot -> {
            spot.forecast().clear();
            spot.forecast().addAll(forecasts.get(spot.wgId()));
        });

        spots = spots
                .stream()
                .map(Spot::withUpdatedTimestamp)
                .toList();
    }
}
