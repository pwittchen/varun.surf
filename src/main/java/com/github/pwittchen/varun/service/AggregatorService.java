package com.github.pwittchen.varun.service;

import com.github.pwittchen.varun.data.provider.spots.SpotsDataProvider;
import com.github.pwittchen.varun.exception.FetchingAiForecastAnalysisException;
import com.github.pwittchen.varun.exception.FetchingCurrentConditionsException;
import com.github.pwittchen.varun.exception.FetchingForecastException;
import com.github.pwittchen.varun.exception.FetchingForecastModelsException;
import com.github.pwittchen.varun.model.forecast.Forecast;
import com.github.pwittchen.varun.model.forecast.ForecastData;
import com.github.pwittchen.varun.model.forecast.ForecastModel;
import com.github.pwittchen.varun.model.live.CurrentConditions;
import com.github.pwittchen.varun.model.live.filter.CurrentConditionsEmptyFilter;
import com.github.pwittchen.varun.model.map.Coordinates;
import com.github.pwittchen.varun.model.sponsor.Sponsor;
import com.github.pwittchen.varun.model.spot.Spot;
import com.github.pwittchen.varun.service.ai.AiServiceEn;
import com.github.pwittchen.varun.service.ai.AiServicePl;
import com.github.pwittchen.varun.service.forecast.ForecastService;
import com.github.pwittchen.varun.service.live.CurrentConditionsService;
import com.github.pwittchen.varun.service.map.GoogleMapsService;
import com.github.pwittchen.varun.service.sponsors.SponsorsService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@SuppressWarnings({"preview", "Since15"})
public class AggregatorService {

    private static final Logger log = LoggerFactory.getLogger(AggregatorService.class);
    private static final List<String> SPOT_PHOTO_EXTENSIONS = List.of("jpg", "png");

    @Value("${app.feature.ai.forecast.analysis.enabled}")
    private boolean aiForecastAnalysisEnabled;

    private final AtomicReference<List<Spot>> spots;
    private final ConcurrentMap<Integer, ForecastData> forecastCache;
    private final ConcurrentMap<Integer, CurrentConditions> currentConditions;
    private final ConcurrentMap<Integer, String> aiAnalysisEn;
    private final ConcurrentMap<Integer, String> aiAnalysisPl;
    private final ConcurrentMap<Integer, Long> hourlyForecastCacheTimestamps;
    private final ConcurrentMap<Integer, Coordinates> locationCoordinates;
    private final ConcurrentMap<Integer, String> spotPhotos;

    private final SpotsDataProvider spotsDataProvider;
    private final ForecastService forecastService;
    private final CurrentConditionsService currentConditionsService;
    private final AiServiceEn aiServiceEn;
    private final AiServicePl aiServicePl;
    private final GoogleMapsService googleMapsService;
    private final SponsorsService sponsorsService;

    private Disposable spotsDisposable;
    private final Semaphore forecastLimiter = new Semaphore(32);
    private final Semaphore currentConditionsLimiter = new Semaphore(32);
    private final Semaphore aiLimiter = new Semaphore(16);
    private final ConcurrentMap<Integer, Disposable> locationCoordinatesFetchSubscriptions;
    private final ConcurrentMap<Integer, Object> forecastModelsLocks;

    public AggregatorService(
            SpotsDataProvider spotsDataProvider,
            ForecastService forecastService,
            CurrentConditionsService currentConditionsService,
            AiServiceEn aiServiceEn,
            AiServicePl aiServicePl,
            GoogleMapsService googleMapsService,
            SponsorsService sponsorsService) {
        this.spots = new AtomicReference<>(new ArrayList<>());
        this.forecastCache = new ConcurrentHashMap<>();
        this.currentConditions = new ConcurrentHashMap<>();
        this.aiAnalysisEn = new ConcurrentHashMap<>();
        this.aiAnalysisPl = new ConcurrentHashMap<>();
        this.hourlyForecastCacheTimestamps = new ConcurrentHashMap<>();
        this.locationCoordinatesFetchSubscriptions = new ConcurrentHashMap<>();
        this.locationCoordinates = new ConcurrentHashMap<>();
        this.spotPhotos = new ConcurrentHashMap<>();
        this.forecastModelsLocks = new ConcurrentHashMap<>();
        this.spotsDataProvider = spotsDataProvider;
        this.forecastService = forecastService;
        this.currentConditionsService = currentConditionsService;
        this.aiServiceEn = aiServiceEn;
        this.aiServicePl = aiServicePl;
        this.googleMapsService = googleMapsService;
        this.sponsorsService = sponsorsService;
    }

    @PostConstruct
    public void init() {
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
    public void cleanup() {
        if (spotsDisposable != null) {
            spotsDisposable.dispose();
        }
        locationCoordinatesFetchSubscriptions.values().forEach(Disposable::dispose);
        locationCoordinatesFetchSubscriptions.clear();
    }

    public List<Spot> getSpots() {
        return spots
                .get()
                .stream()
                .map(this::enrichSpotWithCachedData)
                .toList();
    }

    public int countSpots() {
        return spots.get().size();
    }

    public int countCountries() {
        return Long.valueOf(spots
                .get()
                .stream()
                .map(Spot::country)
                .distinct()
                .count()).intValue();
    }

    public int countLiveStations() {
        return Long.valueOf(currentConditions
                .values()
                .stream()
                .filter(conditions -> conditions != null && !isEmptyConditions(conditions))
                .count()).intValue();
    }

    public int getForecastCacheSize() {
        return forecastCache.size();
    }

    public int getCurrentConditionsCacheSize() {
        return currentConditions.size();
    }

    public int getAiAnalysisCacheSize() {
        return aiAnalysisEn.size() + aiAnalysisPl.size();
    }

    public int getCoordinatesCacheSize() {
        return locationCoordinates.size();
    }

    private boolean isEmptyConditions(CurrentConditions conditions) {
        return conditions.wind() == 0 &&
                conditions.gusts() == 0 &&
                (conditions.direction() == null || conditions.direction().isEmpty());
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

        var analysisEn = aiAnalysisEn.get(spot.wgId());
        if (analysisEn != null) {
            enrichedSpot = enrichedSpot.withAiAnalysisEn(analysisEn);
        }

        var analysisPl = aiAnalysisPl.get(spot.wgId());
        if (analysisPl != null) {
            enrichedSpot = enrichedSpot.withAiAnalysisPl(analysisPl);
        }

        var spotPhotoUrl = spotPhotos.computeIfAbsent(spot.wgId(), this::loadSpotPhotoPath);
        if (!spotPhotoUrl.isEmpty()) {
            enrichedSpot = enrichedSpot.withSpotPhoto(spotPhotoUrl);
        }

        var coords = locationCoordinates.get(spot.wgId());
        if (coords != null) {
            enrichedSpot = enrichedSpot.withCoordinates(coords);
        } else {
            scheduleLocationCoordinatesFetch(spot);
        }

        List<Sponsor> sponsors = sponsorsService.getSponsors()
                .stream()
                .filter(sponsor -> sponsor.id() == spot.wgId())
                .toList();

        if (!sponsors.isEmpty()) {
            enrichedSpot = enrichedSpot.withSponsors(sponsors);
        }

        return enrichedSpot;
    }

    private void scheduleLocationCoordinatesFetch(final Spot spot) {
        locationCoordinatesFetchSubscriptions.computeIfAbsent(spot.wgId(), id ->
                loadCoordinates(spot)
                        .subscribeOn(Schedulers.boundedElastic())
                        .doOnNext(c -> locationCoordinates.put(id, c))
                        .doOnError(error -> log.warn("Coordinates fetch failed for spot {}", id, error))
                        .doFinally(_ -> locationCoordinatesFetchSubscriptions.remove(id))
                        .subscribe()
        );
    }

    private Mono<Coordinates> loadCoordinates(Spot spot) {
        if (spot.locationUrl() == null || spot.locationUrl().isEmpty()) {
            return Mono.empty();
        }

        return googleMapsService
                .getCoordinates(spot)
                .timeout(Duration.ofSeconds(5))
                .filter(Objects::nonNull)
                .onErrorResume(error -> {
                    log.warn("Failed to load coordinates for spot {} within timeout", spot.wgId(), error);
                    return Mono.empty();
                });
    }

    private String loadSpotPhotoPath(int spotId) {
        for (String extension : SPOT_PHOTO_EXTENSIONS) {
            String resourcePath = "static/images/spots/" + spotId + "." + extension;
            ClassPathResource resource = new ClassPathResource(resourcePath);
            if (resource.exists()) {
                return "/images/spots/" + spotId + "." + extension;
            }
        }
        return "";
    }

    @Scheduled(fixedRate = 3 * 60 * 60 * 1000)
    @Retryable(retryFor = FetchingForecastException.class, maxAttempts = 5, backoff = @Backoff(delay = 3000))
    public void fetchForecastsEveryThreeHours() throws FetchingForecastException {
        log.info("Fetching forecasts");
        fetchForecasts();
    }

    @Recover
    public void recoverFromFetchingForecasts(FetchingForecastException e) {
        log.error("Failed while fetching forecasts after 3 attempts", e);
    }

    @Async
    public void fetchForecasts() throws FetchingForecastException {
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
    public void fetchCurrentConditionsEveryOneMinute() throws FetchingCurrentConditionsException {
        log.info("Fetching current conditions");
        fetchCurrentConditions();
    }

    @Recover
    public void recoverFromFetchingCurrentConditions(FetchingCurrentConditionsException e) {
        log.error("Failed while fetching current conditions after 3 attempts", e);
    }

    @Async
    public void fetchCurrentConditions() throws FetchingCurrentConditionsException {
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
            log.info("Hourly forecast cache timestamp for spot {} is not expired yet", spotId);
            return;
        }

        if (areForecastsAlreadyFetched(spotId)) {
            log.info("Forecast models for spot {} are already in cache", spotId);
            return;
        }

        Object lock = forecastModelsLocks.computeIfAbsent(spotId, _ -> new Object());

        synchronized (lock) {
            if (isHourlyForecastCacheTimestampNotExpired(spotId)) {
                log.info("Hourly forecast cache timestamp for spot {} is not expired yet", spotId);
                return;
            }

            if (areForecastsAlreadyFetched(spotId)) {
                log.info("Forecast models for spot {} are already in cache", spotId);
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
        forecastModelsLocks.remove(spotId);
    }

    @Scheduled(fixedRate = 8 * 60 * 60 * 1000, initialDelay = 5 * 60 * 1000)
    @Retryable(retryFor = FetchingForecastException.class, maxAttempts = 2, backoff = @Backoff(delay = 7000))
    public void fetchAiAnalysisEveryEightHoursEn() throws FetchingForecastException {
        if (aiForecastAnalysisEnabled) {
            log.info("Fetching AI forecast analysis in EN");
            fetchAiForecastAnalysisEn();
        } else {
            log.info("Fetching AI forecast analysis (EN) is DISABLED");
        }
    }

    @Scheduled(fixedRate = 8 * 60 * 60 * 1000, initialDelay = 5 * 60 * 1000)
    @Retryable(retryFor = FetchingForecastException.class, maxAttempts = 2, backoff = @Backoff(delay = 7000))
    public void fetchAiAnalysisEveryEightHoursPl() throws FetchingForecastException {
        if (aiForecastAnalysisEnabled) {
            log.info("Fetching AI forecast analysis in PL");
            fetchAiForecastAnalysisPl();
        } else {
            log.info("Fetching AI forecast analysis (PL) is DISABLED");
        }
    }


    @Recover
    public void recoverFromFetchingAiAnalysis(FetchingAiForecastAnalysisException e) {
        log.error("Failed while fetching AI forecast analysis after 3 attempts", e);
    }

    @Async
    public void fetchAiForecastAnalysisEn() throws FetchingAiForecastAnalysisException {
        try (var scope = new StructuredTaskScope<>("aiAnalysisEn", Thread.ofVirtual().factory())) {
            var tasks = spots
                    .get()
                    .stream()
                    .map(spot -> scope.fork(() -> {
                        aiLimiter.acquire();
                        try {
                            var analysis = aiServiceEn.fetchAiAnalysis(spot).block();
                            updateSpotAiAnalysisEn(spot.wgId(), analysis);
                            return Pair.with(spot.wgId(), analysis);
                        } finally {
                            aiLimiter.release();
                        }
                    }))
                    .toList();

            try {
                scope.join();
            } catch (Exception e) {
                log.error("Error while fetching AI forecast analysis (EN)", e);
            }

            tasks
                    .stream()
                    .filter(subtask -> subtask.state() == StructuredTaskScope.Subtask.State.FAILED)
                    .map(subtask -> subtask.exception().getMessage())
                    .forEach(log::warn);

            log.info("AI forecast analysis fetched (EN)");
        }
    }

    @Async
    public void fetchAiForecastAnalysisPl() throws FetchingAiForecastAnalysisException {
        try (var scope = new StructuredTaskScope<>("aiAnalysisPl", Thread.ofVirtual().factory())) {
            var tasks = spots
                    .get()
                    .stream()
                    .map(spot -> scope.fork(() -> {
                        aiLimiter.acquire();
                        try {
                            var analysis = aiServicePl.fetchAiAnalysis(spot).block();
                            updateSpotAiAnalysisPl(spot.wgId(), analysis);
                            return Pair.with(spot.wgId(), analysis);
                        } finally {
                            aiLimiter.release();
                        }
                    }))
                    .toList();

            try {
                scope.join();
            } catch (Exception e) {
                log.error("Error while fetching AI forecast analysis (PL)", e);
            }

            tasks
                    .stream()
                    .filter(subtask -> subtask.state() == StructuredTaskScope.Subtask.State.FAILED)
                    .map(subtask -> subtask.exception().getMessage())
                    .forEach(log::warn);

            log.info("AI forecast analysis fetched (PL)");
        }
    }

    private void updateSpotAiAnalysisEn(int spotId, String analysis) {
        if (analysis != null && !analysis.isEmpty()) {
            aiAnalysisEn.put(spotId, analysis);
        }
    }

    private void updateSpotAiAnalysisPl(int spotId, String analysis) {
        if (analysis != null && !analysis.isEmpty()) {
            aiAnalysisPl.put(spotId, analysis);
        }
    }
}
