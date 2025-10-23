package com.github.pwittchen.varun.service;

import com.github.pwittchen.varun.exception.FetchingAiForecastAnalysisException;
import com.github.pwittchen.varun.exception.FetchingCurrentConditionsException;
import com.github.pwittchen.varun.exception.FetchingForecastException;
import com.github.pwittchen.varun.exception.FetchingForecastModelsException;
import com.github.pwittchen.varun.model.CurrentConditions;
import com.github.pwittchen.varun.model.Forecast;
import com.github.pwittchen.varun.model.ForecastData;
import com.github.pwittchen.varun.model.ForecastModel;
import com.github.pwittchen.varun.model.Spot;
import com.github.pwittchen.varun.model.filter.CurrentConditionsEmptyFilter;
import com.github.pwittchen.varun.provider.SpotsDataProvider;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@SuppressWarnings({"preview", "Since15"})
public class AggregatorService {

    private static final Logger log = LoggerFactory.getLogger(AggregatorService.class);

    @Value("${app.feature.ai.forecast.analysis.enabled}")
    private boolean aiForecastAnalysisEnabled;

    private final AtomicReference<List<Spot>> spots;
    private final Map<Integer, ForecastData> forecastCache;
    private final Map<Integer, CurrentConditions> currentConditions;
    private final Map<Integer, String> aiAnalysis;
    private final Map<Integer, Long> hourlyForecastCacheTimestamps;

    private Disposable spotsDisposable;
    private final SpotsDataProvider spotsDataProvider;
    private final ForecastService forecastService;
    private final CurrentConditionsService currentConditionsService;
    private final AiService aiService;

    private final Semaphore forecastLimiter = new Semaphore(32);
    private final Semaphore currentConditionsLimiter = new Semaphore(32);
    private final Semaphore aiLimiter = new Semaphore(16);

    public AggregatorService(
            SpotsDataProvider spotsDataProvider,
            ForecastService forecastService,
            CurrentConditionsService currentConditionsService,
            AiService aiService) {
        this.spots = new AtomicReference<>(new ArrayList<>());
        this.forecastCache = new ConcurrentHashMap<>();
        this.currentConditions = new ConcurrentHashMap<>();
        this.aiAnalysis = new ConcurrentHashMap<>();
        this.hourlyForecastCacheTimestamps = new ConcurrentHashMap<>();
        this.spotsDataProvider = spotsDataProvider;
        this.forecastService = forecastService;
        this.currentConditionsService = currentConditionsService;
        this.aiService = aiService;
    }

    @PostConstruct
    void init() {
        spotsDisposable = spotsDataProvider
                .getSpots()
                .collectList()
                .doOnSubscribe(_ -> log.info("Loading spots"))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(spots -> {
                    this.spots.set(spots);
                    log.info("Loaded {} spots", spots.size());
                }, error -> log.error("Failed to load spots", error));
    }

    @PreDestroy
    void cleanup() {
        spotsDisposable.dispose();
    }

    public List<Spot> getSpots() {
        return spots
                .get()
                .stream()
                .map(this::enrichSpotWithCachedData)
                .toList();
    }

    public Optional<Spot> getSpotById(int id) {
        return getSpotById(id, ForecastModel.GFS);
    }

    public Optional<Spot> getSpotById(int id, ForecastModel forecastModel) {
        return spots
                .get()
                .stream()
                .filter(spot -> spot.wgId() == id)
                .findFirst()
                .map(spot -> enrichSpotWithCachedData(spot, forecastModel));
    }

    private Spot enrichSpotWithCachedData(Spot spot) {
        return enrichSpotWithCachedData(spot, ForecastModel.GFS);
    }

    private Spot enrichSpotWithCachedData(Spot spot, ForecastModel forecastModel) {
        var enrichedSpot = spot;

        var data = forecastCache.get(spot.wgId());
        if (data != null) {
            if (forecastModel == ForecastModel.IFS && !data.hourlyIfs().isEmpty()) {
                enrichedSpot = enrichedSpot.withForecastHourly(data.hourlyIfs());
            } else if (!data.hourlyGfs().isEmpty()) {
                enrichedSpot = enrichedSpot.withForecastHourly(data.hourlyGfs());
            }
        }

        var conditions = currentConditions.get(spot.wgId());
        if (conditions != null) {
            enrichedSpot = enrichedSpot.withCurrentConditions(conditions);
        }

        var analysis = aiAnalysis.get(spot.wgId());
        if (analysis != null) {
            enrichedSpot = enrichedSpot.withAiAnalysis(analysis);
        }

        return enrichedSpot;
    }

    @Scheduled(fixedRate = 3 * 60 * 60 * 1000)
    @Retryable(retryFor = FetchingForecastException.class, maxAttempts = 5, backoff = @Backoff(delay = 3000))
    void fetchForecastsEveryThreeHours() throws FetchingForecastException {
        log.info("Fetching forecasts");
        fetchForecasts();
    }

    @Recover
    void recoverFromFetchingForecasts(FetchingForecastException e) {
        log.error("Failed while fetching forecasts after 3 attempts", e);
    }

    private void fetchForecasts() throws FetchingForecastException {
        var spotWgIds = spots.get().stream().map(Spot::wgId).toList();

        try (var scope = new StructuredTaskScope.ShutdownOnFailure("forecast", Thread.ofVirtual().factory())) {
            var tasks = spotWgIds
                    .stream()
                    .map(id -> scope.fork(() -> {
                        forecastLimiter.acquire();
                        try {
                            return Pair.with(id, forecastService.getForecastData(id).block());
                        } finally {
                            forecastLimiter.release();
                        }
                    }))
                    .toList();

            try {
                scope.join().throwIfFailed();
            } catch (Exception e) {
                log.error("Error while fetching forecasts", e);
                throw new FetchingForecastException(e.getMessage());
            }

            log.info("Forecasts fetched");
            updateSpotsAndForecasts(tasks);
        }
    }

    private void updateSpotsAndForecasts(List<StructuredTaskScope.Subtask<Pair<Integer, ForecastData>>> tasks) {
        Map<Integer, ForecastData> newForecasts = tasks
                .stream()
                .map(StructuredTaskScope.Subtask::get)
                .filter(pair -> pair.getValue1() != null && !pair.getValue1().daily().isEmpty())
                .collect(Collectors.toMap(Pair::getValue0, Pair::getValue1));

        forecastCache.putAll(newForecasts);

        spots.set(spots
                .get()
                .stream()
                .map(spot -> {
                    var data = forecastCache.get(spot.wgId());
                    if (data == null) {
                        return spot;
                    }
                    return spot.withForecasts(data.daily(), Collections.emptyList());
                })
                .toList()
        );
    }

    @Scheduled(fixedRate = 60_000)
    @Retryable(retryFor = FetchingCurrentConditionsException.class, maxAttempts = 5, backoff = @Backoff(delay = 5000))
    void fetchCurrentConditionsEveryOneMinute() throws FetchingCurrentConditionsException {
        log.info("Fetching current conditions");
        fetchCurrentConditions();
    }

    @Recover
    public void recoverFromFetchingCurrentConditions(FetchingCurrentConditionsException e) {
        log.error("Failed while fetching current conditions after 3 attempts", e);
    }

    private void fetchCurrentConditions() throws FetchingCurrentConditionsException {
        var spotWgIds = spots.get().stream().map(Spot::wgId).toList();

        try (var scope = new StructuredTaskScope<>("currentConditions", Thread.ofVirtual().factory())) {
            var tasks = spotWgIds
                    .stream()
                    .map(id -> scope.fork(() -> {
                        currentConditionsLimiter.acquire();
                        try {
                            var conditions = currentConditionsService.fetchCurrentConditions(id).block();
                            updateSpotCurrentConditions(id, conditions);
                            return Pair.with(id, conditions);
                        } finally {
                            currentConditionsLimiter.release();
                        }
                    }))
                    .toList();

            try {
                scope.join();
            } catch (Exception e) {
                log.error("Error while fetching current conditions", e);
                throw new FetchingCurrentConditionsException(e.getMessage());
            }

            tasks
                    .stream()
                    .filter(subtask -> subtask.state() == StructuredTaskScope.Subtask.State.FAILED)
                    .map(subtask -> subtask.exception().getMessage())
                    .forEach(log::warn);

            log.info("Current conditions fetched");
        }
    }

    private void updateSpotCurrentConditions(int spotId, CurrentConditions conditions) {
        if (!CurrentConditionsEmptyFilter.isEmpty(conditions)) {
            currentConditions.put(spotId, conditions);
        }
    }

    @Async
    public void fetchForecastsForAllModels(int spotId) {
        if (isHourlyForecastCacheTimestampNotExpired(spotId)) {
            log.info("Hourly forecast cache timestamp for spot {} is not expired yet, no need to fetch it again", spotId);
            return;
        }

        if (areForecastsAlreadyFetched(spotId)) {
            log.info("Forecast models for spot {} are already fetched, no need to fetch it again", spotId);
            return;
        }

        log.info("Fetching forecast models for the spot {}", spotId);
        final List<ForecastModel> models = Arrays.stream(ForecastModel.values()).toList();

        try (var scope = new StructuredTaskScope<>("singleSpotForecastModels", Thread.ofVirtual().factory())) {
            var tasks = models
                    .stream()
                    .map(forecastModel -> scope.fork(() -> {
                        forecastLimiter.acquire();
                        try {
                            return Pair.with(forecastModel, forecastService.getForecastData(spotId, forecastModel.name().toLowerCase()).block());
                        } finally {
                            forecastLimiter.release();
                        }
                    }))
                    .toList();

            try {
                scope.join();
            } catch (Exception e) {
                log.error("Error while fetching forecast models for the spot", e);
                throw new FetchingForecastModelsException(e.getMessage());
            }

            log.info("Forecast models for the spot {} fetched", spotId);
            updateSpotAndForecastModels(spotId, tasks);
        }
    }

    private boolean isHourlyForecastCacheTimestampNotExpired(int spotId) {
        long timestamp = hourlyForecastCacheTimestamps.getOrDefault(spotId, 0L);
        if (timestamp == 0L) {
            return false;
        }
        Instant created = Instant.ofEpochMilli(timestamp);
        Instant now = Instant.now();
        return Duration.between(created, now).toHours() < 3;
    }

    private boolean areForecastsAlreadyFetched(int spotId) {
        ForecastData data = forecastCache.get(spotId);
        if (data == null) {
            return false;
        }
        boolean gfsEmpty = data.hourlyGfs().isEmpty();
        boolean ifsEmpty = data.hourlyIfs().isEmpty();
        return !gfsEmpty && !ifsEmpty;
    }

    private void updateSpotAndForecastModels(
            int spotId,
            List<StructuredTaskScope.Subtask<Pair<ForecastModel, ForecastData>>> tasks
    ) {
        List<Pair<ForecastModel, ForecastData>> forecasts = tasks
                .stream()
                .map(StructuredTaskScope.Subtask::get)
                .toList();

        List<Forecast> hourlyIfs = forecasts
                .stream()
                .map(Pair::getValue1)
                .map(ForecastData::hourlyIfs)
                .flatMap(List::stream)
                .toList();

        final ForecastData data = new ForecastData(
                forecastCache.get(spotId).daily(),
                forecastCache.get(spotId).hourlyGfs(),
                hourlyIfs
        );

        forecastCache.put(spotId, data);
        hourlyForecastCacheTimestamps.put(spotId, System.currentTimeMillis());
    }

    @Scheduled(fixedRate = 8 * 60 * 60 * 1000)
    @Retryable(retryFor = FetchingForecastException.class, maxAttempts = 3, backoff = @Backoff(delay = 7000))
    void fetchAiAnalysisEveryEightHours() throws FetchingForecastException {
        if (aiForecastAnalysisEnabled) {
            log.info("Fetching AI forecast analysis");
            fetchAiForecastAnalysis();
        } else {
            log.info("Fetching AI forecast analysis is DISABLED");
        }
    }

    @Recover
    void recoverFromFetchingAiAnalysis(FetchingAiForecastAnalysisException e) {
        log.error("Failed while fetching AI forecast analysis after 3 attempts", e);
    }

    private void fetchAiForecastAnalysis() throws FetchingAiForecastAnalysisException {
        try (var scope = new StructuredTaskScope<>("aianalysis", Thread.ofVirtual().factory())) {
            var tasks = spots
                    .get()
                    .stream()
                    .map(spot -> scope.fork(() -> {
                        aiLimiter.acquire();
                        try {
                            var analysis = aiService.fetchAiAnalysis(spot).block();
                            updateSpotAiAnalysis(spot.wgId(), analysis);
                            return Pair.with(spot.wgId(), analysis);
                        } finally {
                            aiLimiter.release();
                        }
                    }))
                    .toList();

            try {
                scope.join();
            } catch (Exception e) {
                log.error("Error while fetching AI forecast analysis", e);
            }

            tasks
                    .stream()
                    .filter(subtask -> subtask.state() == StructuredTaskScope.Subtask.State.FAILED)
                    .map(subtask -> subtask.exception().getMessage())
                    .forEach(log::warn);

            log.info("AI forecast analysis fetched");
        }
    }

    private void updateSpotAiAnalysis(int spotId, String analysis) {
        if (analysis != null && !analysis.isEmpty()) {
            aiAnalysis.put(spotId, analysis);
        }
    }
}
