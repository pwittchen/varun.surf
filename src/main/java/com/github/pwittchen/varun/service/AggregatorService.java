package com.github.pwittchen.varun.service;

import com.github.pwittchen.varun.config.CacheControlFilter;
import com.github.pwittchen.varun.data.spots.SpotsDataProvider;
import com.github.pwittchen.varun.exception.FetchingCurrentConditionsException;
import com.github.pwittchen.varun.exception.FetchingForecastException;
import com.github.pwittchen.varun.exception.FetchingForecastModelsException;
import com.github.pwittchen.varun.mapper.HourlyForecastMapper;
import com.github.pwittchen.varun.metrics.AggregatorServiceMetrics;
import com.github.pwittchen.varun.model.forecast.AvailableModel;
import com.github.pwittchen.varun.model.forecast.Forecast;
import com.github.pwittchen.varun.model.forecast.ForecastData;
import com.github.pwittchen.varun.model.forecast.ForecastModel;
import com.github.pwittchen.varun.model.forecast.HourlyForecast;
import com.github.pwittchen.varun.model.forecast.WindTimeline;
import com.github.pwittchen.varun.model.live.CurrentConditions;
import com.github.pwittchen.varun.model.live.filter.CurrentConditionsEmptyFilter;
import com.github.pwittchen.varun.model.map.Coordinates;
import com.github.pwittchen.varun.model.sponsor.Sponsor;
import com.github.pwittchen.varun.model.spot.Spot;
import com.github.pwittchen.varun.service.ai.AiService;
import com.github.pwittchen.varun.service.ai.AiServiceEn;
import com.github.pwittchen.varun.service.ai.AiServicePl;
import com.github.pwittchen.varun.service.forecast.ForecastAverageCalculator;
import com.github.pwittchen.varun.service.forecast.ForecastService;
import com.github.pwittchen.varun.service.forecast.IcmForecastVisionService;
import com.github.pwittchen.varun.service.forecast.IcmGridMapper;
import com.github.pwittchen.varun.service.live.CurrentConditionsService;
import com.github.pwittchen.varun.service.map.GoogleMapsService;
import com.github.pwittchen.varun.service.sponsors.SponsorsService;
import com.google.common.collect.EvictingQueue;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.javatuples.Pair;
import org.jspecify.annotations.NonNull;
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
import org.springframework.util.DigestUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;
import java.util.concurrent.StructuredTaskScope.Subtask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

@Service
@SuppressWarnings({"preview"})
public class AggregatorService {

    private static final Logger log = LoggerFactory.getLogger(AggregatorService.class);
    private static final List<String> SPOT_PHOTO_EXTENSIONS = List.of("jpg", "png");
    private static final int PHOTO_VERSION_LENGTH = 8;
    private static final int CURRENT_CONDITIONS_HISTORY_LIMIT_IN_MINUTES = 12 * 60;

    // Scheduling intervals
    private static final long FORECAST_FETCH_INTERVAL_MS = 3 * 60 * 60 * 1000;    // 3 hours
    private static final long CONDITIONS_FETCH_INTERVAL_MS = 60_000;              // 1 minute
    private static final long HOURLY_FORECAST_CACHE_TTL_HOURS = 3;

    // The AI analysis and the ICM meteogram reading are the only two things here
    // that cost money per call, and both used to run on a timer over the whole spot
    // list: a daily pass in two languages over ~800 spots, and a meteogram read
    // every three hours for every Polish and Czech spot. That spent the bulk of the
    // budget on spots nobody opened that day. Both are now generated only when a
    // visitor asks for one, and the result is held for a day - long enough that a
    // popular spot is paid for once, short enough that the text never trails the
    // forecast by more than a day.
    private static final long ON_DEMAND_TTL_MS = 24 * 60 * 60 * 1000;             // 24 hours

    // How often expired analyses and meteogram forecasts are dropped. Validity is
    // checked on read anyway, so this only reclaims the memory of what nobody came
    // back for; hourly keeps the caches from holding a day of stale text.
    private static final long ON_DEMAND_EVICTION_INTERVAL_MS = 60 * 60 * 1000;    // 1 hour

    // How far the map's hourly wind timeline reaches when the caller doesn't say.
    // Matched to the five days the daily forecast covers, which is as much as a
    // phone-sized slider has room to step through.
    private static final int WIND_TIMELINE_HOURS = 5 * 24;

    // Ceiling on a requested grid: Windguru's GFS export runs roughly sixteen days
    // out, and the grid is trimmed to the hours the forecast actually holds anyway,
    // so this only bounds how much work a caller can ask for.
    private static final int MAX_WIND_TIMELINE_HOURS = 16 * 24;

    // A pass covers hundreds of spots, so one line per spot would bury everything else in the log.
    // One line per this many keeps the sweep followable at INFO; the per-spot lines are at DEBUG.
    private static final int FORECAST_PROGRESS_LOG_EVERY = 50;

    // Concurrency limits
    private static final int FORECAST_SEMAPHORE_PERMITS = 32;
    private static final int CONDITIONS_SEMAPHORE_PERMITS = 32;
    private static final int DISCOVERY_SEMAPHORE_PERMITS = 16;

    @Value("${app.feature.ai.forecast.analysis.enabled}")
    private boolean aiForecastAnalysisEnabled;

    @Value("${app.feature.icm.vision.enabled}")
    private boolean icmVisionEnabled;

    /**
     * Every scheduled fetch and every startup warm-up hangs off this flag. Booting the application
     * loads ~800 spots and immediately resolves coordinates and forecasts for all of them, which is
     * exactly what a production instance should do and pure waste in a unit test: the test profile
     * turns it off so a Spring context boots without touching the network.
     */
    @Value("${app.background.tasks.enabled:true}")
    private boolean backgroundTasksEnabled = true;

    private final ConcurrentMap<Integer, Spot> spots;
    private final ConcurrentMap<Integer, ForecastData> forecastCache;
    private final ConcurrentMap<Integer, CurrentConditions> currentConditions;
    private final ConcurrentMap<Integer, EvictingQueue<CurrentConditions>> currentConditionsHistory;
    private final ConcurrentMap<Integer, String> aiAnalysisEn;
    private final ConcurrentMap<Integer, String> aiAnalysisPl;
    // When each on-demand result was generated, which is what makes it expire. Kept
    // beside the value rather than inside it so the analysis caches stay plain
    // strings for everything that only reads them.
    private final ConcurrentMap<Integer, Long> aiAnalysisEnCreatedAt;
    private final ConcurrentMap<Integer, Long> aiAnalysisPlCreatedAt;
    private final ConcurrentMap<Integer, Long> icmForecastCreatedAt;
    private final ConcurrentMap<Integer, Long> hourlyForecastCacheTimestamps;
    private final ConcurrentMap<Integer, Coordinates> locationCoordinates;
    private final ConcurrentMap<Integer, String> icmUrls;
    private final ConcurrentMap<Integer, String> spotPhotos;

    private final SpotsDataProvider spotsDataProvider;
    private final ForecastService forecastService;
    private final CurrentConditionsService currentConditionsService;
    private final AiServiceEn aiServiceEn;
    private final AiServicePl aiServicePl;
    private final GoogleMapsService googleMapsService;
    private final IcmGridMapper icmGridMapper;
    private final HourlyForecastMapper hourlyForecastMapper;
    private final IcmForecastVisionService icmForecastVisionService;
    private final SponsorsService sponsorsService;
    private final AggregatorServiceMetrics metricsService;

    private Disposable spotsDisposable;
    private final Semaphore forecastLimiter = new Semaphore(FORECAST_SEMAPHORE_PERMITS);
    private final Semaphore currentConditionsLimiter = new Semaphore(CONDITIONS_SEMAPHORE_PERMITS);
    private final Semaphore discoveryLimiter = new Semaphore(DISCOVERY_SEMAPHORE_PERMITS);
    private final ConcurrentMap<Integer, Disposable> locationCoordinatesFetchSubscriptions;
    private final ConcurrentMap<Integer, Disposable> icmUrlResolutionSubscriptions;
    private final ConcurrentMap<Integer, Object> forecastModelsLocks;

    // A windy Saturday puts several visitors on the same spot within seconds of each
    // other, and each of them clicking "generate" would buy the same paragraph again.
    // The first call publishes its Mono here and everyone who arrives while it is
    // still running subscribes to that one instead of starting a second.
    private final ConcurrentMap<String, Mono<String>> aiAnalysisInFlight;
    private final ConcurrentMap<Integer, Mono<Boolean>> icmForecastInFlight;

    // Progress of the forecast sweep, read by /api/v1/status/forecast
    private final AtomicBoolean forecastFetchInProgress = new AtomicBoolean();
    private final AtomicInteger forecastFetchTotal = new AtomicInteger();
    private final AtomicInteger forecastFetchCompleted = new AtomicInteger();
    private final AtomicInteger forecastFetchSucceeded = new AtomicInteger();
    private final AtomicInteger forecastFetchEmpty = new AtomicInteger();
    private final AtomicInteger forecastFetchFailed = new AtomicInteger();
    private volatile long forecastFetchStartedAtMs;
    private volatile long forecastFetchFinishedAtMs;

    public AggregatorService(
            SpotsDataProvider spotsDataProvider,
            ForecastService forecastService,
            CurrentConditionsService currentConditionsService,
            AiServiceEn aiServiceEn,
            AiServicePl aiServicePl,
            GoogleMapsService googleMapsService,
            IcmGridMapper icmGridMapper,
            HourlyForecastMapper hourlyForecastMapper,
            IcmForecastVisionService icmForecastVisionService,
            SponsorsService sponsorsService,
            AggregatorServiceMetrics metricsService) {
        this.spots = new ConcurrentHashMap<>();
        this.forecastCache = new ConcurrentHashMap<>();
        this.currentConditions = new ConcurrentHashMap<>();
        this.currentConditionsHistory = new ConcurrentHashMap<>();
        this.aiAnalysisEn = new ConcurrentHashMap<>();
        this.aiAnalysisPl = new ConcurrentHashMap<>();
        this.aiAnalysisEnCreatedAt = new ConcurrentHashMap<>();
        this.aiAnalysisPlCreatedAt = new ConcurrentHashMap<>();
        this.icmForecastCreatedAt = new ConcurrentHashMap<>();
        this.aiAnalysisInFlight = new ConcurrentHashMap<>();
        this.icmForecastInFlight = new ConcurrentHashMap<>();
        this.hourlyForecastCacheTimestamps = new ConcurrentHashMap<>();
        this.locationCoordinatesFetchSubscriptions = new ConcurrentHashMap<>();
        this.icmUrlResolutionSubscriptions = new ConcurrentHashMap<>();
        this.locationCoordinates = new ConcurrentHashMap<>();
        this.icmUrls = new ConcurrentHashMap<>();
        this.spotPhotos = new ConcurrentHashMap<>();
        this.forecastModelsLocks = new ConcurrentHashMap<>();
        this.spotsDataProvider = spotsDataProvider;
        this.forecastService = forecastService;
        this.currentConditionsService = currentConditionsService;
        this.aiServiceEn = aiServiceEn;
        this.aiServicePl = aiServicePl;
        this.googleMapsService = googleMapsService;
        this.icmGridMapper = icmGridMapper;
        this.hourlyForecastMapper = hourlyForecastMapper;
        this.icmForecastVisionService = icmForecastVisionService;
        this.sponsorsService = sponsorsService;
        this.metricsService = metricsService;
    }

    @PostConstruct
    public void init() {
        spotsDisposable = spotsDataProvider
                .getSpots()
                .collectList()
                .doOnSubscribe(_ -> log.info("Loading spots"))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(spotsList -> {
                    this.spots.clear();
                    spotsList.forEach(spot -> this.spots.put(spot.wgId(), spot));
                    log.info("Loaded {} spots", this.spots.size());
                    updateMetricsGauges();
                    warmUpSpots();
                }, error -> log.error("Failed to load spots", error));
    }

    /**
     * Resolves everything that used to be resolved lazily on the first API call, so that a freshly
     * started instance serves complete spots (coordinates, ICM URLs, photos) without waiting for a
     * visitor to warm the caches up.
     */
    private void warmUpSpots() {
        if (!backgroundTasksEnabled) {
            return;
        }
        log.info("Warming up spots");
        spots.values().forEach(spot -> {
            spotPhotos.computeIfAbsent(spot.wgId(), this::loadSpotPhotoPath);
            var coords = locationCoordinates.get(spot.wgId());
            if (coords == null) {
                // the ICM URL is resolved as soon as the coordinates arrive
                scheduleLocationCoordinatesFetch(spot);
            } else if (!icmUrls.containsKey(spot.wgId())) {
                scheduleIcmUrlResolution(spot, coords);
            }
        });
    }

    private void updateMetricsGauges() {
        metricsService.updateGauges(
                countSpots(),
                countCountries(),
                countLiveStations(),
                forecastCache.size(),
                currentConditions.size()
        );
    }

    @PreDestroy
    public void cleanup() {
        if (spotsDisposable != null) {
            spotsDisposable.dispose();
        }
        locationCoordinatesFetchSubscriptions.values().forEach(Disposable::dispose);
        locationCoordinatesFetchSubscriptions.clear();
        icmUrlResolutionSubscriptions.values().forEach(Disposable::dispose);
        icmUrlResolutionSubscriptions.clear();
    }

    public List<Spot> getSpots() {
        return spots
                .values()
                .stream()
                .map(this::enrichSpotWithCachedData)
                .toList();
    }

    /**
     * Hourly wind for every spot at once, on one shared time grid.
     *
     * The map draws all spots for a single moment, so it reads the timeline
     * rather than the per-spot forecasts, which are only served one spot at a
     * time (and stripped from the all-spots response, being far too large).
     */
    public WindTimeline getWindTimeline() {
        return getWindTimeline(WIND_TIMELINE_HOURS);
    }

    /**
     * The same timeline over a caller-chosen span, so a wide screen can step
     * through the whole run the forecast holds while a phone keeps paying for the
     * few days its slider can address. The grid never runs past the forecast, so
     * asking for more hours than exist costs nothing but returns nothing either.
     *
     * @param hours how many hours to lay out, clamped to a sane span
     */
    public WindTimeline getWindTimeline(int hours) {
        final Map<Integer, List<Forecast>> hourlyBySpotId = new HashMap<>();
        forecastCache.forEach((spotId, data) -> {
            List<Forecast> hourly = data.hourly(ForecastModel.GFS);
            if (!hourly.isEmpty()) {
                hourlyBySpotId.put(spotId, hourly);
            }
        });

        final int gridHours = Math.max(1, Math.min(MAX_WIND_TIMELINE_HOURS, hours));
        return hourlyForecastMapper.toWindTimeline(hourlyBySpotId, LocalDateTime.now(), gridHours);
    }

    /**
     * One spot's full hourly forecast on the same grid - wind, temperature, rain,
     * cloud, pressure and waves. A single spot can afford the fields the all-spots
     * timeline has to leave out.
     *
     * An unknown spot is empty here, so the endpoint can answer 404. A known spot
     * whose forecast hasn't been fetched yet is a present but empty forecast,
     * which says "nothing for this spot yet" rather than "no such spot".
     *
     * @param wgId Windguru id of the spot
     * @return the spot's hourly forecast, or empty when no such spot exists
     */
    public Optional<HourlyForecast> getHourlyForecast(int wgId) {
        if (!spots.containsKey(wgId)) {
            return Optional.empty();
        }

        ForecastData data = forecastCache.get(wgId);
        List<Forecast> hourly = data == null ? List.of() : data.hourly(ForecastModel.GFS);

        return Optional.of(
                hourlyForecastMapper.toHourlyForecast(wgId, hourly, LocalDateTime.now(), WIND_TIMELINE_HOURS)
        );
    }

    public int countSpots() {
        return spots.size();
    }

    public int countCountries() {
        return (int) spots
                .values()
                .stream()
                .map(Spot::country)
                .distinct()
                .count();
    }

    public int countLiveStations() {
        return (int) currentConditions
                .values()
                .stream()
                .filter(conditions -> conditions != null && hasWindData(conditions))
                .count();
    }

    private boolean hasWindData(CurrentConditions conditions) {
        boolean hasWind = conditions.wind() > 0;
        boolean hasGusts = conditions.gusts() > 0;
        boolean hasDirection = conditions.direction() != null && !conditions.direction().isEmpty();
        return hasWind || hasGusts || hasDirection;
    }

    public Optional<Spot> getSpotById(int id) {
        return getSpotById(id, ForecastModel.GFS);
    }

    public Optional<Spot> getSpotById(int id, ForecastModel forecastModel) {
        return Optional
                .ofNullable(spots.get(id))
                .map(spot -> enrichSpotWithCachedData(spot, forecastModel));
    }

    public Optional<Spot> getSpotById(int id, String modelKey) {
        if (ForecastAverageCalculator.AVERAGE_MODEL_KEY.equals(modelKey)) {
            return Optional
                    .ofNullable(spots.get(id))
                    .map(spot -> {
                        var enriched = enrichSpotWithCachedData(spot, ForecastModel.GFS);
                        var data = forecastCache.get(spot.wgId());
                        if (data != null) {
                            var averaged = ForecastAverageCalculator.computeAverage(data);
                            if (!averaged.isEmpty()) {
                                enriched = enriched.withForecastHourly(averaged);
                            }
                        }
                        return enriched;
                    });
        }
        return getSpotById(id, ForecastModel.fromModelKey(modelKey));
    }

    private Spot enrichSpotWithCachedData(Spot spot) {
        return enrichSpotWithCachedData(spot, ForecastModel.GFS);
    }

    private Spot enrichSpotWithCachedData(Spot spot, ForecastModel forecastModel) {
        var enrichedSpot = spot;

        var data = forecastCache.get(spot.wgId());
        if (data != null) {
            var hourlyForecasts = data.hourly(forecastModel);
            if (!hourlyForecasts.isEmpty()) {
                enrichedSpot = enrichedSpot.withForecastHourly(hourlyForecasts);
            } else if (!data.hourly(ForecastModel.GFS).isEmpty()) {
                enrichedSpot = enrichedSpot.withForecastHourly(data.hourly(ForecastModel.GFS));
            }

            // Model discovery runs asynchronously once a spot is opened, so until it has finished
            // the cache may already hold a partial set of models (ICM is pre-fetched for Polish and
            // Czech spots by a separate scheduled job). Publishing that partial set would make the
            // frontend treat the model list as complete and stop waiting for the remaining models,
            // so only the default model is exposed until discovery completes.
            Stream<ForecastModel> discoveredModels = data.hourly().keySet().stream()
                    .filter(m -> !data.hourly(m).isEmpty())
                    // ICM is generated on demand and expires after a day. The eviction
                    // sweep drops it from the cache hourly, so between expiry and the
                    // next sweep the dropdown would still offer a forecast the frontend
                    // treats as gone.
                    .filter(m -> m != ForecastModel.ICM_METEO || hasValidIcmForecast(spot.wgId()));
            if (!hourlyForecastCacheTimestamps.containsKey(spot.wgId())) {
                discoveredModels = discoveredModels.filter(m -> m == ForecastModel.GFS);
            }

            List<AvailableModel> available = new ArrayList<>(discoveredModels
                    .sorted(Comparator.comparingInt(ForecastModel::ordinal))
                    .map(m -> new AvailableModel(m.modelKey(), m.displayName()))
                    .toList());
            if (available.size() >= 2) {
                available.add(new AvailableModel(
                        ForecastAverageCalculator.AVERAGE_MODEL_KEY,
                        ForecastAverageCalculator.AVERAGE_DISPLAY_NAME
                ));
            }
            if (!available.isEmpty()) {
                enrichedSpot = enrichedSpot.withAvailableModels(available);
            }
        }

        var conditions = currentConditions.get(spot.wgId());
        if (conditions != null) {
            enrichedSpot = enrichedSpot.withCurrentConditions(conditions);
        }

        var conditionsHistory = currentConditionsHistory.get(spot.wgId());
        if (conditionsHistory != null && !conditionsHistory.isEmpty()) {
            enrichedSpot = enrichedSpot.withCurrentConditionsHistory(new ArrayList<>(conditionsHistory));
        }

        // Only while it is still inside its day: an expired analysis is dropped by
        // the eviction sweep, but a spot read between expiry and the next sweep must
        // not carry it either, or the generate button would stay hidden behind text
        // the frontend already considers stale.
        var analysisEn = aiAnalysisEn.get(spot.wgId());
        if (analysisEn != null && hasValidAiAnalysis(spot.wgId(), "en")) {
            enrichedSpot = enrichedSpot.withAiAnalysisEn(analysisEn);
        }

        var analysisPl = aiAnalysisPl.get(spot.wgId());
        if (analysisPl != null && hasValidAiAnalysis(spot.wgId(), "pl")) {
            enrichedSpot = enrichedSpot.withAiAnalysisPl(analysisPl);
        }

        var spotPhotoUrl = spotPhotos.computeIfAbsent(spot.wgId(), this::loadSpotPhotoPath);
        if (!spotPhotoUrl.isEmpty()) {
            enrichedSpot = enrichedSpot.withSpotPhoto(spotPhotoUrl);
        }

        var coords = locationCoordinates.get(spot.wgId());
        if (coords != null) {
            enrichedSpot = enrichedSpot.withCoordinates(coords);
            // Only the cached value, resolving it validates ICM grid points over HTTP
            var icmUrl = icmUrls.get(spot.wgId());
            if (icmUrl != null) {
                enrichedSpot = enrichedSpot.withIcmUrl(icmUrl);
            } else {
                scheduleIcmUrlResolution(spot, coords);
            }
        } else {
            scheduleLocationCoordinatesFetch(spot);
        }

        List<Sponsor> sponsors = sponsorsService.getSponsorsForSpot(spot.wgId());
        if (!sponsors.isEmpty()) {
            enrichedSpot = enrichedSpot.withSponsors(sponsors);
        }

        return enrichedSpot;
    }

    private void scheduleLocationCoordinatesFetch(final Spot spot) {
        if (!backgroundTasksEnabled) {
            return;
        }
        locationCoordinatesFetchSubscriptions.computeIfAbsent(spot.wgId(), id ->
                loadCoordinates(spot)
                        .subscribeOn(Schedulers.boundedElastic())
                        .doOnNext(c -> {
                            locationCoordinates.put(id, c);
                            resolveAndCacheIcmUrl(id, spot, c);
                        })
                        .doOnError(error -> log.warn("Coordinates fetch failed for spot {}", id, error))
                        .doFinally(_ -> locationCoordinatesFetchSubscriptions.remove(id))
                        .subscribe()
        );
    }

    /**
     * Resolves the ICM meteogram URL off the request path. Snapping coordinates to a valid ICM grid
     * point costs a few HTTP calls, so it must never happen while serving a spot.
     */
    private void scheduleIcmUrlResolution(final Spot spot, final Coordinates coords) {
        if (!backgroundTasksEnabled) {
            return;
        }
        if (!icmGridMapper.isCountrySupported(spot.country())) {
            return;
        }
        icmUrlResolutionSubscriptions.computeIfAbsent(spot.wgId(), id ->
                Mono.fromRunnable(() -> resolveAndCacheIcmUrl(id, spot, coords))
                        .subscribeOn(Schedulers.boundedElastic())
                        .doOnError(error -> log.warn("ICM URL resolution failed for spot {}", id, error))
                        .onErrorComplete()
                        .doFinally(_ -> icmUrlResolutionSubscriptions.remove(id))
                        .subscribe()
        );
    }

    private Optional<String> resolveAndCacheIcmUrl(int spotId, Spot spot, Coordinates coords) {
        String cached = icmUrls.get(spotId);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<String> icmUrl = icmGridMapper.toIcmUrl(coords.lat(), coords.lon(), spot.country());
        icmUrl.ifPresent(url -> icmUrls.put(spotId, url));
        return icmUrl;
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
                String path = "/images/spots/" + spotId + "." + extension;
                return contentVersionOf(resource)
                        .map(version -> path + "?" + CacheControlFilter.VERSION_PARAM + "=" + version)
                        .orElse(path);
            }
        }
        return "";
    }

    /**
     * Content hash appended to a photo URL, so that replacing the file changes the URL and
     * the new photo shows up immediately instead of waiting for a CDN or browser cache to expire.
     */
    private Optional<String> contentVersionOf(ClassPathResource resource) {
        try (InputStream stream = resource.getInputStream()) {
            return Optional.of(DigestUtils.md5DigestAsHex(stream).substring(0, PHOTO_VERSION_LENGTH));
        } catch (IOException e) {
            log.warn("Failed to compute content hash for {}", resource.getPath(), e);
            return Optional.empty();
        }
    }

    @Scheduled(fixedRate = FORECAST_FETCH_INTERVAL_MS)
    @Retryable(retryFor = FetchingForecastException.class, maxAttempts = 5, backoff = @Backoff(delay = 3000))
    public void fetchForecastsEveryThreeHours() throws FetchingForecastException {
        if (!backgroundTasksEnabled) {
            return;
        }
        log.info("Fetching forecasts");
        fetchForecasts();
    }

    @Recover
    public void recoverFromFetchingForecasts(FetchingForecastException e) {
        log.error("Failed while fetching forecasts after 3 attempts", e);
    }

    @Async
    public void fetchForecasts() throws FetchingForecastException {
        metricsService.incrementForecastFetchCounter();
        var startTime = System.nanoTime();
        final int total = spots.size();
        beginForecastFetchProgress(total);

        // Deliberately not a fail-fast scope. A pass over the whole spot list takes minutes, and
        // aborting all of it because one spot's Windguru export timed out threw that work away and
        // left the cache untouched - then @Retryable started the same minutes-long pass over again.
        // Failures are counted and logged per spot instead, everything that did arrive stays, and
        // the spots that failed get one more attempt once the rest of the pass is through.
        try (var scope = openScope("forecast")) {
            // The spots whose fetch threw, retried once the rest of the pass is through.
            final Queue<Spot> failedSpots = new ConcurrentLinkedQueue<>();

            spots.values().forEach(spot -> scope.fork(() -> {
                forecastLimiter.acquire();
                try {
                    // Use forecastWgId() for fetching (extracts ID from fallback URL if needed)
                    // but use wgId() for caching (unique deterministic ID for the spot)
                    int forecastId = spot.forecastWgId();
                    int cacheId = spot.wgId();
                    var data = forecastService.getForecastData(forecastId).block();
                    publishForecast(spot, data, total);
                    return Pair.with(cacheId, data);
                } catch (Exception e) {
                    recordForecastFetchFailure(spot, total, e);
                    failedSpots.add(spot);
                    throw e;
                } finally {
                    forecastLimiter.release();
                }
            }));

            try {
                scope.join();
            } catch (Exception e) {
                log.error("Error while fetching forecasts", e);
                metricsService.incrementForecastFetchFailureCounter();
                throw new FetchingForecastException(e.getMessage());
            }

            retryFailedForecasts(failedSpots);

            long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
            log.info("Forecasts fetched: {} of {} spots in {} ms ({} failed, cache holds {})",
                    forecastFetchSucceeded.get(), total, elapsedMs, forecastFetchFailed.get(), forecastCache.size());
            metricsService.incrementForecastFetchSuccessCounter();
            metricsService.updateLastForecastFetchTimestamp();
            updateMetricsGauges();
        } finally {
            endForecastFetchProgress();
            metricsService.recordForecastFetchDuration(startTime);
        }
    }

    /**
     * Puts one spot's forecast into the cache as soon as it arrives rather than collecting every
     * result and writing them in one batch at the end of the pass. The batch write meant nothing
     * was readable until the slowest spot in the list finished, so a freshly started instance
     * served ~800 spots with no forecast at all for the length of a whole sweep.
     */
    private void publishForecast(final Spot spot, final ForecastData data, final int total) {
        cacheForecast(spot, data);
        logForecastFetchProgress(total);
    }

    /**
     * @return whether the spot's forecast made it into the cache; an export that came back with
     * nothing is counted as empty and cached as nothing, which is neither a success nor a failure
     */
    private boolean cacheForecast(final Spot spot, final ForecastData data) {
        int cacheId = spot.wgId();
        if (data == null || data.daily().isEmpty()) {
            forecastFetchEmpty.incrementAndGet();
            log.debug("No forecast data for spot {} ({}, forecastWgId: {})", cacheId, spot.name(), spot.forecastWgId());
            return false;
        }
        // Merge instead of overwrite, so the models fetched outside this cycle (ICM, on-demand
        // Windguru models) survive the periodic GFS refresh.
        forecastCache.merge(cacheId, data, AggregatorService::mergeForecastData);
        spots.computeIfPresent(cacheId, (_, cached) -> Optional
                .ofNullable(forecastCache.get(cacheId))
                .map(merged -> cached.withForecasts(merged.daily(), Collections.emptyList()))
                .orElse(cached));
        forecastFetchSucceeded.incrementAndGet();
        log.debug("Fetched forecast for spot {} ({}): {} daily entries, {} models",
                cacheId, spot.name(), data.daily().size(), data.hourly().size());
        return true;
    }

    /**
     * One more attempt at the spots whose fetch threw, made once the rest of the pass is through.
     * A sweep hits a third-party export several hundred times and a handful of those time out or
     * come back malformed on any given run; without a second attempt those spots keep a forecast
     * three hours old - or none at all, on a freshly started instance - until the next sweep.
     *
     * <p>The retry belongs to the same pass rather than being a new one: a spot that answers on the
     * second attempt stops counting as a failure, one that fails again is left exactly as it was,
     * and the totals the status page reads keep adding up. It shares the same semaphore, so it
     * never fetches harder than the pass it follows.
     */
    private void retryFailedForecasts(final Collection<Spot> failedSpots) {
        if (failedSpots.isEmpty()) {
            return;
        }

        final int attempted = failedSpots.size();
        final AtomicInteger recovered = new AtomicInteger();
        log.info("Retrying forecasts for {} spots that failed in this pass", attempted);

        try (var scope = openScope("forecastRetry")) {
            failedSpots.forEach(spot -> scope.fork(() -> {
                forecastLimiter.acquire();
                try {
                    var data = forecastService.getForecastData(spot.forecastWgId()).block();
                    // The spot is no longer a failure however the retry turned out: it either
                    // carries a forecast now or answered with nothing, which is counted as empty.
                    forecastFetchFailed.decrementAndGet();
                    if (cacheForecast(spot, data)) {
                        recovered.incrementAndGet();
                    }
                    return Pair.with(spot.wgId(), data);
                } catch (Exception e) {
                    log.warn("Retry failed for spot {} ({}, forecastWgId: {}): {}",
                            spot.wgId(), spot.name(), spot.forecastWgId(), e.getMessage());
                    throw e;
                } finally {
                    forecastLimiter.release();
                }
            }));

            scope.join();
        } catch (Exception e) {
            // A retry that falls over leaves the pass exactly where it was, so it is logged and
            // nothing more: the spots it could not recover keep the forecast they already had.
            log.error("Error while retrying failed forecasts", e);
        }

        log.info("Retried forecasts for {} spots: {} recovered, {} still failing",
                attempted, recovered.get(), forecastFetchFailed.get());
    }

    private void recordForecastFetchFailure(final Spot spot, final int total, final Exception e) {
        forecastFetchFailed.incrementAndGet();
        log.warn("Failed to fetch forecast for spot {} ({}, forecastWgId: {}): {}",
                spot.wgId(), spot.name(), spot.forecastWgId(), e.getMessage());
        logForecastFetchProgress(total);
    }

    private void logForecastFetchProgress(final int total) {
        int done = forecastFetchCompleted.incrementAndGet();
        if (done % FORECAST_PROGRESS_LOG_EVERY != 0 && done != total) {
            return;
        }
        long elapsedMs = System.currentTimeMillis() - forecastFetchStartedAtMs;
        log.info("Fetching forecasts: {}/{} spots ({}%) - {} with data, {} empty, {} failed, {} s elapsed",
                done, total, total == 0 ? 100 : done * 100 / total,
                forecastFetchSucceeded.get(), forecastFetchEmpty.get(), forecastFetchFailed.get(),
                elapsedMs / 1000);
    }

    private void beginForecastFetchProgress(final int total) {
        forecastFetchTotal.set(total);
        forecastFetchCompleted.set(0);
        forecastFetchSucceeded.set(0);
        forecastFetchEmpty.set(0);
        forecastFetchFailed.set(0);
        forecastFetchStartedAtMs = System.currentTimeMillis();
        forecastFetchInProgress.set(true);
    }

    private void endForecastFetchProgress() {
        forecastFetchInProgress.set(false);
        forecastFetchFinishedAtMs = System.currentTimeMillis();
    }

    /**
     * How far the running (or last) forecast pass got. A pass takes minutes and publishes spot by
     * spot, so the frontend reads this to say how much of the list already carries a forecast
     * instead of showing a list that silently fills itself in.
     */
    public ForecastFetchProgress getForecastFetchProgress() {
        boolean inProgress = forecastFetchInProgress.get();
        long startedAt = forecastFetchStartedAtMs;
        long finishedAt = forecastFetchFinishedAtMs;
        long elapsedMs = startedAt == 0 ? 0 : (inProgress ? System.currentTimeMillis() : finishedAt) - startedAt;
        return new ForecastFetchProgress(
                inProgress,
                forecastFetchTotal.get(),
                forecastFetchCompleted.get(),
                forecastFetchSucceeded.get(),
                forecastFetchEmpty.get(),
                forecastFetchFailed.get(),
                forecastCache.size(),
                startedAt,
                finishedAt,
                elapsedMs
        );
    }

    /**
     * @param inProgress whether a pass is running right now
     * @param total      spots the running (or last) pass set out to fetch
     * @param completed  spots it has finished with, however they turned out
     * @param fetched    spots that came back with a forecast
     * @param empty      spots that answered with nothing to cache
     * @param failed     spots whose fetch threw
     * @param cached     spots the forecast cache holds, this pass and every earlier one
     */
    public record ForecastFetchProgress(
            boolean inProgress,
            int total,
            int completed,
            int fetched,
            int empty,
            int failed,
            int cached,
            long startedAt,
            long finishedAt,
            long elapsedMs
    ) {
    }

    private static ForecastData mergeForecastData(ForecastData existing, ForecastData fresh) {
        Map<ForecastModel, List<Forecast>> hourly = new HashMap<>(existing.hourly());
        hourly.putAll(fresh.hourly());
        return new ForecastData(fresh.daily(), hourly);
    }

    @Scheduled(fixedRate = CONDITIONS_FETCH_INTERVAL_MS)
    @Retryable(retryFor = FetchingCurrentConditionsException.class, maxAttempts = 5, backoff = @Backoff(delay = 5000))
    public void fetchCurrentConditionsEveryOneMinute() throws FetchingCurrentConditionsException {
        if (!backgroundTasksEnabled) {
            return;
        }
        log.info("Fetching current conditions");
        fetchCurrentConditions();
    }

    @Recover
    public void recoverFromFetchingCurrentConditions(FetchingCurrentConditionsException e) {
        log.error("Failed while fetching current conditions after 3 attempts", e);
    }

    @Async
    public void fetchCurrentConditions() throws FetchingCurrentConditionsException {
        metricsService.incrementConditionsFetchCounter();
        var startTime = System.nanoTime();

        try (var scope = openScope("currentConditions")) {
            var tasks = spots
                    .keySet()
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
                metricsService.incrementConditionsFetchFailureCounter();
                throw new FetchingCurrentConditionsException(e.getMessage());
            }

            tasks
                    .stream()
                    .filter(subtask -> subtask.state() == Subtask.State.FAILED)
                    .map(subtask -> subtask.exception().getMessage())
                    .forEach(log::warn);

            log.info("Current conditions fetched");
            metricsService.incrementConditionsFetchSuccessCounter();
            metricsService.updateLastConditionsFetchTimestamp();
            updateMetricsGauges();
        } finally {
            metricsService.recordConditionsFetchDuration(startTime);
        }
    }

    private void updateSpotCurrentConditions(int spotId, CurrentConditions conditions) {
        if (!CurrentConditionsEmptyFilter.isEmpty(conditions)) {
            currentConditions.put(spotId, conditions);
            currentConditionsHistory
                    .computeIfAbsent(spotId, _ -> EvictingQueue.create(CURRENT_CONDITIONS_HISTORY_LIMIT_IN_MINUTES))
                    .add(conditions);
        }
    }

    private Optional<List<Forecast>> fetchIcmForecast(String icmUrl) {
        return icmForecastVisionService
                .extractForecastFromMeteogram(icmUrl)
                .filter(forecasts -> !forecasts.isEmpty());
    }

    /**
     * Merges ICM forecasts into the cached forecast data of a spot, leaving the other models and the
     * daily forecast untouched. Deliberately does not touch hourlyForecastCacheTimestamps, so opening
     * a spot still triggers the discovery of the remaining Windguru models.
     */
    private boolean updateIcmForecast(int spotId, Optional<List<Forecast>> forecasts) {
        if (forecasts.isEmpty()) {
            return false;
        }
        forecastCache.compute(spotId, (_, existing) -> {
            Map<ForecastModel, List<Forecast>> hourly = existing != null
                    ? new HashMap<>(existing.hourly())
                    : new HashMap<>();
            hourly.put(ForecastModel.ICM_METEO, forecasts.get());
            return new ForecastData(existing != null ? existing.daily() : List.of(), hourly);
        });
        return true;
    }

    @Async
    public void fetchForecastsForAllModels(int spotId) {
        if (isHourlyForecastCacheTimestampNotExpired(spotId)) {
            log.info("Hourly forecast cache timestamp for spot {} is not expired yet", spotId);
            return;
        }

        Object lock = forecastModelsLocks.computeIfAbsent(spotId, _ -> new Object());

        synchronized (lock) {
            if (isHourlyForecastCacheTimestampNotExpired(spotId)) {
                log.info("Hourly forecast cache timestamp for spot {} is not expired yet", spotId);
                return;
            }

            Spot spot = spots.get(spotId);

            // Find the spot to get the forecastWgId (which may differ from spotId for fallback URLs)
            int forecastId = Optional
                    .ofNullable(spot)
                    .map(Spot::forecastWgId)
                    .orElse(spotId);

            log.info("Fetching forecast models for the spot {} (forecastId: {})", spotId, forecastId);

            // Filter out ICM_METEO from Windguru models (it doesn't use Windguru API)
            final List<ForecastModel> windguruModels = Arrays.stream(ForecastModel.values())
                    .filter(m -> m != ForecastModel.ICM_METEO)
                    .toList();

            try (var scope = openScope("singleSpotForecastModels")) {
                var tasks = new ArrayList<>(windguruModels
                        .stream()
                        .map(forecastModel -> scope.fork(() -> {
                            discoveryLimiter.acquire();
                            try {
                                return Pair.with(forecastModel, forecastService.getForecastData(forecastId, forecastModel).block());
                            } finally {
                                discoveryLimiter.release();
                            }
                        }))
                        .toList());

                // ICM is deliberately not fetched here. Reading a meteogram costs a
                // vision call, and this runs on every spot anyone opens - which used
                // to buy one for every Polish and Czech spot a visitor merely looked
                // at. It is generated only when someone asks for it by name, through
                // generateIcmForecast.

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

    private Optional<String> resolveIcmUrl(int spotId, Spot spot) {
        String cached = icmUrls.get(spotId);
        if (cached != null) {
            return Optional.of(cached);
        }
        Coordinates coords = locationCoordinates.get(spotId);
        if (coords == null) {
            log.info("Coordinates not cached for spot {}, loading synchronously for ICM resolution", spotId);
            coords = loadCoordinates(spot).block();
            if (coords != null) {
                locationCoordinates.put(spotId, coords);
            }
        }
        if (coords == null) {
            return Optional.empty();
        }
        return resolveAndCacheIcmUrl(spotId, spot, coords);
    }

    private boolean isHourlyForecastCacheTimestampNotExpired(int spotId) {
        long timestamp = hourlyForecastCacheTimestamps.getOrDefault(spotId, 0L);
        if (timestamp == 0L) {
            return false;
        }
        Instant created = Instant.ofEpochMilli(timestamp);
        Instant now = Instant.now();
        return Duration.between(created, now).toHours() < HOURLY_FORECAST_CACHE_TTL_HOURS;
    }

    private void updateSpotAndForecastModels(
            int spotId,
            List<Subtask<Pair<ForecastModel, ForecastData>>> tasks
    ) {
        List<Pair<ForecastModel, ForecastData>> forecasts = tasks
                .stream()
                .filter(t -> t.state() == Subtask.State.SUCCESS)
                .map(Subtask::get)
                .toList();

        tasks.stream()
                .filter(t -> t.state() == Subtask.State.FAILED)
                .forEach(t -> log.debug("Failed to fetch model for spot: {}", t.exception().getMessage()));

        ForecastData existing = forecastCache.get(spotId);
        final ForecastData data = getForecastData(existing, forecasts);
        logFetchedModels(spotId, data);

        forecastCache.put(spotId, data);
        hourlyForecastCacheTimestamps.put(spotId, System.currentTimeMillis());
        forecastModelsLocks.remove(spotId);
    }

    private static @NonNull ForecastData getForecastData(ForecastData existing, List<Pair<ForecastModel, ForecastData>> forecasts) {
        Map<ForecastModel, List<Forecast>> hourlyMap = existing != null
                ? new HashMap<>(existing.hourly())
                : new HashMap<>();

        for (Pair<ForecastModel, ForecastData> pair : forecasts) {
            ForecastModel model = pair.getValue0();
            ForecastData fetchedData = pair.getValue1();
            List<Forecast> hourlyForecasts = fetchedData.hourly(model);
            if (!hourlyForecasts.isEmpty()) {
                hourlyMap.put(model, hourlyForecasts);
            }
        }

        List<Forecast> daily = existing != null ? existing.daily() : List.of();
        return new ForecastData(daily, hourlyMap);
    }

    private static void logFetchedModels(int spotId, ForecastData data) {
        List<String> fetchedModelKeys = data.hourly().entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .sorted(Comparator.comparingInt(e -> e.getKey().ordinal()))
                .map(e -> e.getKey().modelKey())
                .toList();
        log.info("Spot {} - fetched {} models: {}", spotId, fetchedModelKeys.size(), fetchedModelKeys);
    }

    // ========================================================================
    // ON-DEMAND GENERATION (AI ANALYSIS AND ICM METEOGRAM)
    //
    // Both cost an LLM call per spot, so neither runs on a timer any more. A
    // visitor asks for one, it is generated once, and it stays valid for a day.
    // ========================================================================

    /**
     * Whether this spot already carries an analysis in this language that is still
     * inside its day-long lifetime.
     *
     * The frontend hides its generate button on the strength of this, and the
     * endpoint refuses to spend a second call while it holds - which is what keeps
     * a reloaded page, or a second visitor, from paying for the same paragraph.
     */
    public boolean hasValidAiAnalysis(int wgId, String language) {
        return isOnDemandEntryValid(aiAnalysisCreatedAt(language).get(wgId));
    }

    /**
     * Whether this spot's ICM meteogram has been read within the last day.
     */
    public boolean hasValidIcmForecast(int wgId) {
        return isOnDemandEntryValid(icmForecastCreatedAt.get(wgId));
    }

    /**
     * Generates one spot's AI analysis, or hands back the one already cached.
     *
     * A spot whose analysis is still valid never reaches the model: the cached text
     * is returned as it is. Concurrent callers for the same spot and language share
     * a single generation rather than each starting their own.
     *
     * @return the analysis, or an empty Mono when the spot is unknown, the feature
     * is switched off, or the model returned nothing to say
     */
    public Mono<String> generateAiAnalysis(int wgId, String language) {
        if (!spots.containsKey(wgId)) {
            return Mono.empty();
        }

        if (!aiForecastAnalysisEnabled) {
            log.info("AI forecast analysis is DISABLED, skipping generation for spot {}", wgId);
            return Mono.empty();
        }

        ConcurrentMap<Integer, String> cache = aiAnalysisCache(language);
        if (hasValidAiAnalysis(wgId, language)) {
            log.info("AI analysis for spot {} ({}) is still valid, serving the cached one", wgId, language);
            return Mono.justOrEmpty(cache.get(wgId));
        }

        String key = wgId + ":" + languageCode(language);
        return aiAnalysisInFlight
                .computeIfAbsent(key, _ -> generateAiAnalysisOnce(wgId, language)
                        .doFinally(_ -> aiAnalysisInFlight.remove(key))
                        .cache());
    }

    private Mono<String> generateAiAnalysisOnce(int wgId, String language) {
        AiService aiService = isPolish(language) ? aiServicePl : aiServiceEn;
        ConcurrentMap<Integer, String> cache = aiAnalysisCache(language);
        String languageCode = languageCode(language);
        AtomicLong startedAt = new AtomicLong();

        return Mono
                .fromCallable(() -> spots.get(wgId))
                .flatMap(spot -> {
                    // The spots held here carry the daily rows only (hourly forecasts
                    // are deliberately not kept on them), so the hourly forecast comes
                    // from the same source /api/v1/forecast/{wgId} serves - which is
                    // what lets the summary name hours instead of days.
                    HourlyForecast hourly = getHourlyForecast(wgId)
                            .orElseGet(() -> new HourlyForecast(wgId, List.of()));
                    return aiService.fetchAiAnalysis(spot, hourly);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSubscribe(_ -> {
                    startedAt.set(System.currentTimeMillis());
                    log.info("Generating AI analysis for spot {} ({})", wgId, languageCode);
                    metricsService.incrementAiAnalysisCounter();
                })
                .filter(analysis -> analysis != null && !analysis.isBlank())
                .doOnNext(analysis -> {
                    cache.put(wgId, analysis);
                    aiAnalysisCreatedAt(language).put(wgId, System.currentTimeMillis());
                    log.info("AI analysis for spot {} ({}) generated", wgId, languageCode);
                    metricsService.incrementAiAnalysisSuccessCounter();
                })
                .onErrorResume(error -> {
                    log.warn("Failed to generate AI analysis for spot {} ({})", wgId, languageCode, error);
                    return Mono.empty();
                })
                // One failure per generation, counted in one place: an error and a
                // model with nothing to say both end here, and both look the same to
                // the visitor - the button came back with nothing to show for it.
                .switchIfEmpty(Mono.<String>fromRunnable(metricsService::incrementAiAnalysisFailureCounter))
                // Timed in doFinally rather than off an emitted element, so a
                // generation that failed still lands in the timer - otherwise the
                // average would only ever describe the calls that worked.
                .doFinally(_ -> metricsService.recordAiAnalysisDuration(
                        System.currentTimeMillis() - startedAt.get()));
    }

    /**
     * Reads one spot's ICM meteogram through the vision model and merges the result
     * into its cached forecasts, where it shows up as another entry in the model
     * dropdown. Held for a day like the analysis, and shared between concurrent
     * callers the same way.
     *
     * @return true when the spot now carries an ICM forecast, false when it could
     * not be produced (no grid point, feature off, or the model read nothing)
     */
    public Mono<Boolean> generateIcmForecast(int wgId) {
        Spot spot = spots.get(wgId);
        if (spot == null) {
            return Mono.just(false);
        }

        if (!icmVisionEnabled) {
            log.info("ICM vision is DISABLED, skipping generation for spot {}", wgId);
            return Mono.just(false);
        }

        if (!icmGridMapper.isCountrySupported(spot.country())) {
            return Mono.just(false);
        }

        if (hasValidIcmForecast(wgId)) {
            log.info("ICM forecast for spot {} is still valid, not generating another", wgId);
            return Mono.just(true);
        }

        return icmForecastInFlight
                .computeIfAbsent(wgId, _ -> generateIcmForecastOnce(wgId, spot)
                        .doFinally(_ -> icmForecastInFlight.remove(wgId))
                        .cache());
    }

    private Mono<Boolean> generateIcmForecastOnce(int wgId, Spot spot) {
        return Mono
                .fromCallable(() -> {
                    Optional<String> icmUrl = resolveIcmUrl(wgId, spot);
                    if (icmUrl.isEmpty()) {
                        log.warn("No ICM grid point for spot {}, cannot generate a forecast", wgId);
                        return false;
                    }
                    boolean updated = updateIcmForecast(wgId, fetchIcmForecast(icmUrl.get()));
                    if (updated) {
                        icmForecastCreatedAt.put(wgId, System.currentTimeMillis());
                        log.info("ICM forecast for spot {} generated", wgId);
                    }
                    return updated;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSubscribe(_ -> {
                    log.info("Generating ICM forecast for spot {}", wgId);
                    metricsService.incrementIcmAnalysisCounter();
                })
                .doOnNext(updated -> {
                    if (updated) {
                        metricsService.incrementIcmAnalysisSuccessCounter();
                    } else {
                        metricsService.incrementIcmAnalysisFailureCounter();
                    }
                })
                .doOnError(error -> {
                    log.warn("Failed to generate ICM forecast for spot {}", wgId, error);
                    metricsService.incrementIcmAnalysisFailureCounter();
                })
                .onErrorReturn(false)
                .elapsed()
                .doOnNext(timed -> metricsService.recordIcmAnalysisDuration(timed.getT1()))
                .map(Tuple2::getT2);
    }

    /**
     * Drops what has outlived its day. Validity is checked on read anyway, so this
     * only reclaims the memory of analyses and meteogram forecasts nobody came back
     * for - and, for ICM, takes the model back out of the spot's dropdown so the
     * list never offers a forecast that is no longer there.
     */
    @Scheduled(fixedRate = ON_DEMAND_EVICTION_INTERVAL_MS, initialDelay = ON_DEMAND_EVICTION_INTERVAL_MS)
    public void evictExpiredOnDemandData() {
        int analyses = evictExpiredAnalyses(aiAnalysisEn, aiAnalysisEnCreatedAt)
                + evictExpiredAnalyses(aiAnalysisPl, aiAnalysisPlCreatedAt);

        int icmForecasts = 0;
        for (Integer wgId : List.copyOf(icmForecastCreatedAt.keySet())) {
            if (!isOnDemandEntryValid(icmForecastCreatedAt.get(wgId))) {
                icmForecastCreatedAt.remove(wgId);
                removeIcmForecast(wgId);
                icmForecasts++;
            }
        }

        if (analyses > 0 || icmForecasts > 0) {
            log.info("Evicted {} expired AI analyses and {} expired ICM forecasts", analyses, icmForecasts);
        }
    }

    private int evictExpiredAnalyses(
            ConcurrentMap<Integer, String> cache,
            ConcurrentMap<Integer, Long> createdAt
    ) {
        int evicted = 0;
        for (Integer wgId : List.copyOf(createdAt.keySet())) {
            if (!isOnDemandEntryValid(createdAt.get(wgId))) {
                createdAt.remove(wgId);
                cache.remove(wgId);
                evicted++;
            }
        }
        return evicted;
    }

    /**
     * Takes the ICM model back out of a spot's cached forecasts, leaving every other
     * model and the daily rows untouched.
     */
    private void removeIcmForecast(int wgId) {
        forecastCache.computeIfPresent(wgId, (_, existing) -> {
            Map<ForecastModel, List<Forecast>> hourly = new HashMap<>(existing.hourly());
            hourly.remove(ForecastModel.ICM_METEO);
            return new ForecastData(existing.daily(), hourly);
        });
    }

    private boolean isOnDemandEntryValid(Long createdAtMs) {
        return createdAtMs != null && System.currentTimeMillis() - createdAtMs < ON_DEMAND_TTL_MS;
    }

    private static boolean isPolish(String language) {
        return language != null && language.toLowerCase(Locale.ROOT).startsWith("pl");
    }

    private static String languageCode(String language) {
        return isPolish(language) ? "pl" : "en";
    }

    private ConcurrentMap<Integer, String> aiAnalysisCache(String language) {
        return isPolish(language) ? aiAnalysisPl : aiAnalysisEn;
    }

    private ConcurrentMap<Integer, Long> aiAnalysisCreatedAt(String language) {
        return isPolish(language) ? aiAnalysisPlCreatedAt : aiAnalysisEnCreatedAt;
    }

    private static <T> StructuredTaskScope<T, Void> openScope(String name) {
        return StructuredTaskScope.open(
                Joiner.awaitAll(),
                configuration -> configuration
                        .withName(name)
                        .withThreadFactory(Thread.ofVirtual().factory())
        );
    }
}
