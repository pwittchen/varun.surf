package com.github.pwittchen.varun.controller;

import com.github.pwittchen.varun.metrics.SpotsControllerMetrics;
import com.github.pwittchen.varun.model.forecast.HourlyForecast;
import com.github.pwittchen.varun.model.forecast.WindTimeline;
import com.github.pwittchen.varun.model.spot.Spot;
import com.github.pwittchen.varun.service.AggregatorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/")
public class SpotsController {

    private final AggregatorService aggregatorService;
    private final SpotsControllerMetrics metrics;

    public SpotsController(AggregatorService aggregatorService, SpotsControllerMetrics metrics) {
        this.aggregatorService = aggregatorService;
        this.metrics = metrics;
    }

    @GetMapping("spots")
    public Flux<Spot> spots() {
        metrics.incrementSpotsRequestCounter();
        return Flux
                .fromIterable(aggregatorService.getSpots())
                .map(Spot::withoutCurrentConditionsHistoryAndForecastHourly);
    }

    /**
     * Hourly wind for every spot on one shared time grid, which is what the map's
     * forecast timeline steps through. Kept apart from the spots response, whose
     * per-spot hourly forecasts are stripped precisely because serving them for
     * the whole database at once would be megabytes.
     *
     * The optional {@code hours} parameter says how far the grid should reach: a
     * desktop map asks for the whole forecast run, a phone for the few days its
     * slider can address, and neither pays for the other's payload. The grid is
     * trimmed to the hours the forecast actually holds, so an over-long request is
     * answered with what there is.
     */
    @GetMapping("wind")
    public Mono<WindTimeline> wind(@RequestParam(value = "hours", required = false) Integer hours) {
        metrics.incrementWindRequestCounter();
        return Mono.fromSupplier(() -> hours == null
                ? aggregatorService.getWindTimeline()
                : aggregatorService.getWindTimeline(hours));
    }

    /**
     * One spot's hourly forecast on the same grid, with every field the all-spots
     * wind timeline has to leave out for size: temperature, rain, cloud, pressure
     * and waves alongside the wind. A spot with no forecast cached yet answers with
     * an empty list of hours; an unknown one 404s.
     */
    @GetMapping("forecast/{wgId}")
    public Mono<ResponseEntity<HourlyForecast>> forecast(@PathVariable int wgId) {
        metrics.incrementForecastRequestCounter();
        return Mono
                .fromSupplier(() -> aggregatorService.getHourlyForecast(wgId))
                .map(forecast -> forecast
                        .map(ResponseEntity::ok)
                        .orElseGet(() -> ResponseEntity.notFound().build()));
    }

    @GetMapping("spots/{id}")
    public Mono<ResponseEntity<Spot>> spot(@PathVariable int id) {
        metrics.incrementSpotByIdRequestCounter();
        return Mono
                .justOrEmpty(aggregatorService.getSpotById(id))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .doOnSuccess(_ -> aggregatorService.fetchForecastsForAllModels(id));
    }

    @GetMapping("spots/{id}/{model}")
    public Mono<ResponseEntity<Spot>> spot(@PathVariable int id, @PathVariable String model) {
        metrics.incrementSpotByIdRequestCounter();
        return Mono
                .justOrEmpty(aggregatorService.getSpotById(id, model))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .doOnSuccess(_ -> aggregatorService.fetchForecastsForAllModels(id));
    }

    /**
     * Writes one spot's AI analysis, and answers with the spot carrying it.
     *
     * This is the only thing that spends an LLM call on an analysis: nothing
     * generates one in the background any more. A spot whose analysis is still
     * inside its day-long lifetime is answered from the cache without reaching the
     * model, so a reload - or a second visitor - costs nothing.
     *
     * @param id   Windguru id of the spot
     * @param lang language to write the analysis in ("pl", anything else is English)
     * @return the spot as {@code GET spots/{id}} would serve it, with the analysis
     * filled in; 404 for an unknown spot, 503 when the analysis could not be written
     */
    @PostMapping("spots/{id}/analysis")
    public Mono<ResponseEntity<Spot>> generateAiAnalysis(
            @PathVariable int id,
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang) {
        metrics.incrementAiAnalysisRequestCounter();

        if (aggregatorService.getSpotById(id).isEmpty()) {
            return Mono.just(ResponseEntity.notFound().build());
        }

        return aggregatorService
                .generateAiAnalysis(id, lang)
                .map(_ -> aggregatorService
                        .getSpotById(id)
                        .map(ResponseEntity::ok)
                        .orElseGet(() -> ResponseEntity.notFound().build()))
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build());
    }

    /**
     * Reads one spot's ICM meteogram through the vision model, and answers with the
     * spot carrying the result - so the caller can repopulate its model dropdown
     * from {@code availableModels} without a second request.
     *
     * Like the analysis above, this is the only path that pays for a meteogram
     * reading, and a spot that already has one from within the day is answered from
     * the cache.
     *
     * @param id Windguru id of the spot
     * @return the spot with ICM among its models; 404 for an unknown spot, 503 when
     * the meteogram could not be read (no ICM grid point, feature off, or the model
     * read nothing)
     */
    @PostMapping("spots/{id}/icm")
    public Mono<ResponseEntity<Spot>> generateIcmForecast(@PathVariable int id) {
        metrics.incrementIcmAnalysisRequestCounter();

        if (aggregatorService.getSpotById(id).isEmpty()) {
            return Mono.just(ResponseEntity.notFound().build());
        }

        return aggregatorService
                .generateIcmForecast(id)
                .map(generated -> {
                    if (!generated) {
                        return ResponseEntity.<Spot>status(HttpStatus.SERVICE_UNAVAILABLE).build();
                    }
                    return aggregatorService
                            .getSpotById(id)
                            .map(ResponseEntity::ok)
                            .orElseGet(() -> ResponseEntity.notFound().build());
                });
    }
}
