package com.github.pwittchen.varun.controller;

import com.github.pwittchen.varun.model.status.SourceHealthResult;
import com.github.pwittchen.varun.model.status.Uptime;
import com.github.pwittchen.varun.service.AggregatorService;
import com.github.pwittchen.varun.service.health.HealthCheckResult;
import com.github.pwittchen.varun.service.health.HealthHistoryService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.StructuredTaskScope;

@RestController
@RequestMapping("/api/v1/")
public class StatusController {

    private static final Logger log = LoggerFactory.getLogger(StatusController.class);

    private static final List<SourceDefinition> FORECAST_SOURCES = List.of(
            new SourceDefinition("Windguru", "https://www.windguru.cz", "windguru.cz"),
            new SourceDefinition("Windfinder", "https://www.windfinder.com", "windfinder.com"),
            new SourceDefinition("ICM Meteo", "https://www.meteo.pl", "meteo.pl")
    );

    private static final List<SourceDefinition> LIVE_STATION_SOURCES = List.of(
            new SourceDefinition("Wiatr Kadyny", "https://www.wiatrkadyny.pl/wiatrkadyny.txt", "wiatrkadyny.pl/wiatrkadyny.txt"),
            new SourceDefinition("Wiatr Kuźnica", "https://www.wiatrkadyny.pl/kuznica/wiatrkadyny.txt", "wiatrkadyny.pl/kuznica"),
            new SourceDefinition("Wiatr Draga", "https://www.wiatrkadyny.pl/draga/wiatrkadyny.txt", "wiatrkadyny.pl/draga"),
            new SourceDefinition("Wiatr Rewa", "https://www.wiatrkadyny.pl/rewa/wiatrkadyny.txt", "wiatrkadyny.pl/rewa"),
            new SourceDefinition("Wiatr Puck", "https://www.wiatrkadyny.pl/puck/realtimegauges.txt", "wiatrkadyny.pl/puck"),
            new SourceDefinition("Kiteriders Podersdorf", "https://www.kiteriders.at/wind/weatherstat_kn.html", "kiteriders.at"),
            new SourceDefinition("SC Podo Podersdorf", "https://scpodo.at/wind.php", "scpodo.at"),
            new SourceDefinition("Holfuy Góra Żar", "https://holfuy.com/en/weather/1612", "holfuy.com/en/weather/1612"),
            new SourceDefinition("Holfuy Svencele", "https://holfuy.com/en/weather/1515", "holfuy.com/en/weather/1515"),
            new SourceDefinition("Turawa Airmax", "https://airmax.pl/kamery/turawa", "airmax.pl/kamery/turawa"),
            new SourceDefinition("Mietkow WeeWX", "https://frog01-21064.wykr.es/weewx/inx.html", "frog01-21064.wykr.es"),
            new SourceDefinition("Tarifa Spotfav", "https://www.spotfav.com/public/meteo/weatherflow-4eee927b185476763900001b/update/", "spotfav.com"),
            new SourceDefinition("El Medano Bergfex", "https://cabezo.bergfex.at/wetterstation/", "cabezo.bergfex.at"),
            new SourceDefinition("Le Barcarès Winds-Up", "https://m.winds-up.com/spot/58", "winds-up.com/spot/58")
    );

    private final Instant startTime = Instant.now();
    private final AggregatorService aggregatorService;
    private final HealthHistoryService healthHistoryService;
    private final OkHttpClient okHttpClient;

    @Value("${spring.application.version:unknown}")
    private String version;

    public StatusController(
            AggregatorService aggregatorService,
            HealthHistoryService healthHistoryService,
            OkHttpClient okHttpClient
    ) {
        this.aggregatorService = aggregatorService;
        this.healthHistoryService = healthHistoryService;
        this.okHttpClient = okHttpClient;
    }

    @GetMapping("health")
    public Mono<Map<String, String>> health() {
        return Mono.just(Map.of("status", "UP"));
    }

    @GetMapping("status")
    public Mono<Map<String, Object>> status() {
        Uptime uptime = getUptime();
        int spotsCount = aggregatorService.countSpots();
        int countriesCount = aggregatorService.countCountries();
        int liveStations = aggregatorService.countLiveStations();
        return Mono.just(Map.of(
                "status", "UP",
                "version", version,
                "uptime", uptime.formatted(),
                "uptimeSeconds", uptime.seconds(),
                "startTime", startTime.toString(),
                "spotsCount", spotsCount,
                "countriesCount", countriesCount,
                "liveStations", liveStations
        ));
    }

    @GetMapping("status/history")
    public Mono<Map<String, Object>> healthHistory() {
        List<HealthCheckResult> history = healthHistoryService.getHistory();
        HealthHistoryService.HealthHistorySummary summary = healthHistoryService.getSummary();

        Map<String, Object> result = new HashMap<>();
        result.put("history", history);
        result.put("summary", Map.of(
                "totalChecks", summary.totalChecks(),
                "successfulChecks", summary.successfulChecks(),
                "uptimePercentage", summary.uptimePercentage(),
                "avgLatencyMs", summary.avgLatencyMs(),
                "oldestCheckTimestamp", summary.oldestCheckTimestamp()
        ));
        result.put("currentlyHealthy", healthHistoryService.isCurrentlyHealthy());

        return Mono.just(result);
    }

    @GetMapping("status/sources")
    public Mono<Map<String, Object>> sources() {
        return Mono.fromCallable(() -> {
            List<SourceHealthResult> forecastResults = pingSources(FORECAST_SOURCES);

            Map<String, Object> result = new HashMap<>();
            result.put("forecastSources", forecastResults);
            result.put("liveStationSources", LIVE_STATION_SOURCES);
            return result;
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    private List<SourceHealthResult> pingSources(List<SourceDefinition> sources) {
        try (var scope = StructuredTaskScope.open()) {
            List<StructuredTaskScope.Subtask<SourceHealthResult>> subtasks = sources.stream()
                    .map(source -> scope.fork(() -> pingSource(source)))
                    .toList();
            scope.join();
            return subtasks.stream()
                    .map(StructuredTaskScope.Subtask::get)
                    .toList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return sources.stream()
                    .map(s -> new SourceHealthResult(s.name(), s.url(), s.displayUrl(), false, -1))
                    .toList();
        }
    }

    private SourceHealthResult pingSource(SourceDefinition source) {
        long start = System.currentTimeMillis();
        try {
            Request request = new Request.Builder()
                    .url(source.url())
                    .head()
                    .build();
            try (Response response = okHttpClient.newCall(request).execute()) {
                long latency = System.currentTimeMillis() - start;
                boolean ok = response.isSuccessful() || response.isRedirect();
                return new SourceHealthResult(source.name(), source.url(), source.displayUrl(), ok, latency);
            }
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.debug("Failed to ping source {}: {}", source.name(), e.getMessage());
            return new SourceHealthResult(source.name(), source.url(), source.displayUrl(), false, latency);
        }
    }

    private record SourceDefinition(String name, String url, String displayUrl) {
    }

    private Uptime getUptime() {
        Duration uptime = Duration.between(startTime, Instant.now());
        long days = uptime.toDays();
        long hours = uptime.toHoursPart();
        long minutes = uptime.toMinutesPart();
        long seconds = uptime.toSecondsPart();
        String formattedUptime = String.format("%dd %dh %dm %ds", days, hours, minutes, seconds);
        return new Uptime(seconds, formattedUptime);
    }
}
