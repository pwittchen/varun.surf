package com.github.pwittchen.varun.service;

import com.github.pwittchen.varun.model.Forecast;
import com.github.pwittchen.varun.model.LiveConditions;
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
    private Map<Integer, LiveConditions> liveConditions;
    private Map<Integer, String> aiAnalysis;

    private final SpotsDataProvider spotsDataProvider;
    private final ForecastService forecastService;
    private final LiveConditionsService liveConditionsService;

    public AggregatorService(
            SpotsDataProvider spotsDataProvider,
            ForecastService forecastService,
            LiveConditionsService liveConditionsService) {
        this.spots = new ArrayList<>();
        this.forecasts = new HashMap<>();
        this.liveConditions = new HashMap<>();
        this.aiAnalysis = new HashMap<>();
        this.spotsDataProvider = spotsDataProvider;
        this.forecastService = forecastService;
        this.liveConditionsService = liveConditionsService;
    }

    @PostConstruct
    public void init() {
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
            forecasts.clear();
            forecasts = fetchForecasts();
        } catch (Exception e) {
            log.error("Could not fetch forecasts", e);
        }
    }

    @SuppressWarnings("preview")
    public Map<Integer, List<Forecast>> fetchForecasts() throws Exception {
        var spotWgIds = spots.stream().map(Spot::wgId).toList();

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            var tasks = spotWgIds
                    .stream()
                    .map(id -> scope.fork(() -> Pair.with(id, forecastService.getForecast(id).block())))
                    .toList();

            scope.join().throwIfFailed();

            return tasks
                    .stream()
                    .map(StructuredTaskScope.Subtask::get)
                    .filter(pair -> pair.getValue1() != null)
                    .collect(Collectors.toMap(Pair::getValue0, Pair::getValue1));
        }
    }
}
