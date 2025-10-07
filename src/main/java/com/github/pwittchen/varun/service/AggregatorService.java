package com.github.pwittchen.varun.service;

import com.github.pwittchen.varun.exception.FetchingAiForecastAnalysisException;
import com.github.pwittchen.varun.exception.FetchingCurrentConditionsException;
import com.github.pwittchen.varun.exception.FetchingForecastException;
import com.github.pwittchen.varun.model.CurrentConditions;
import com.github.pwittchen.varun.model.Forecast;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private Map<Integer, List<Forecast>> forecasts;
    private Map<Integer, CurrentConditions> currentConditions;
    private Map<Integer, String> aiAnalysis;

    private Disposable spotsDisposable;
    private final SpotsDataProvider spotsDataProvider;
    private final ForecastService forecastService;
    private final CurrentConditionsService currentConditionsService;
    private final AiService aiService;

    private final Semaphore limiter = new Semaphore(32);

    public AggregatorService(
            SpotsDataProvider spotsDataProvider,
            ForecastService forecastService,
            CurrentConditionsService currentConditionsService,
            AiService aiService) {
        this.spots = new AtomicReference<>(new ArrayList<>());
        this.forecasts = new ConcurrentHashMap<>();
        this.currentConditions = new ConcurrentHashMap<>();
        this.aiAnalysis = new ConcurrentHashMap<>();
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
        return spots.get();
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
                        limiter.acquire();
                        try {
                            return Pair.with(id, forecastService.getForecast(id).block());
                        } finally {
                            limiter.release();
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

    private void updateSpotsAndForecasts(List<StructuredTaskScope.Subtask<Pair<Integer, List<Forecast>>>> tasks) {
        forecasts.clear();
        forecasts = tasks
                .stream()
                .map(StructuredTaskScope.Subtask::get)
                .filter(pair -> !pair.getValue1().isEmpty())
                .collect(Collectors.toMap(Pair::getValue0, Pair::getValue1));

        spots.set(spots
                .get()
                .stream()
                .peek(spot -> {
                    spot.forecast().clear();
                    spot.forecast().addAll(forecasts.get(spot.wgId()));
                })
                .map(Spot::withUpdatedTimestamp)
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
                        limiter.acquire();
                        try {
                            return Pair.with(id, currentConditionsService.fetchCurrentConditions(id).block());
                        } finally {
                            limiter.release();
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

            var successfulTasks = tasks
                    .stream()
                    .filter(subtask -> subtask.state() == StructuredTaskScope.Subtask.State.SUCCESS)
                    .toList();

            log.info("Current conditions fetched");
            updateSpotsAndCurrentConditions(successfulTasks);
        }
    }

    private void updateSpotsAndCurrentConditions(List<StructuredTaskScope.Subtask<Pair<Integer, CurrentConditions>>> tasks) {
        currentConditions.clear();
        currentConditions = tasks
                .stream()
                .map(StructuredTaskScope.Subtask::get)
                .filter(pair -> !CurrentConditionsEmptyFilter.isEmpty(pair.getValue1()))
                .collect(Collectors.toMap(Pair::getValue0, Pair::getValue1));

        spots.set(spots
                .get()
                .stream()
                .map(spot -> spot.withCurrentConditions(currentConditions.get(spot.wgId())))
                .toList()
        );
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
        try (var scope = new StructuredTaskScope.ShutdownOnFailure("aianalysis", Thread.ofVirtual().factory())) {
            var tasks = spots
                    .get()
                    .stream()
                    .map(spot -> scope.fork(() -> {
                        limiter.acquire();
                        try {
                            return Pair.with(spot, aiService.fetchAiAnalysis(spot).block());
                        } finally {
                            limiter.release();
                        }
                    }))
                    .toList();

            try {
                scope.join().throwIfFailed();
            } catch (Exception e) {
                log.error("Error while fetching AI forecast analysis", e);
                throw new FetchingAiForecastAnalysisException(e.getMessage());
            }

            log.info("AI forecast analysis fetched");
            updateSpotsAndAiForecastAnalysis(tasks);
        }
    }

    private void updateSpotsAndAiForecastAnalysis(List<StructuredTaskScope.Subtask<Pair<Spot, String>>> tasks) {
        aiAnalysis.clear();
        aiAnalysis = tasks
                .stream()
                .map(StructuredTaskScope.Subtask::get)
                .filter(pair -> pair.getValue1() != null && !pair.getValue1().isEmpty())
                .map(pair -> Pair.with(pair.getValue0().wgId(), pair.getValue1()))
                .collect(Collectors.toMap(Pair::getValue0, Pair::getValue1));

        spots.set(spots
                .get()
                .stream()
                .map(spot -> spot.withAiAnalysis(aiAnalysis.get(spot.wgId())))
                .toList()
        );
    }
}
