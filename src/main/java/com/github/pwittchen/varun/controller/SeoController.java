package com.github.pwittchen.varun.controller;

import com.github.pwittchen.varun.model.spot.Spot;
import com.github.pwittchen.varun.service.AggregatorService;
import com.github.pwittchen.varun.service.seo.SeoService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Serves the spot and country HTML pages with server-side rendered SEO metadata
 * (unique title, description, canonical, Open Graph, Twitter Card, JSON-LD), and
 * a dynamic sitemap covering every spot and country page. Without this, every
 * spot page would share the generic static {@code spot.html} metadata.
 */
@RestController
public class SeoController {

    private final AggregatorService aggregatorService;
    private final SeoService seoService;

    private volatile String spotTemplate;
    private volatile String indexTemplate;

    public SeoController(AggregatorService aggregatorService, SeoService seoService) {
        this.aggregatorService = aggregatorService;
        this.seoService = seoService;
    }

    @GetMapping("/spot/{id}")
    public Mono<ResponseEntity<String>> spotPage(@PathVariable int id) {
        String template = loadSpotTemplate();
        String html = aggregatorService.getSpotById(id)
                .map(spot -> seoService.injectSpotSeo(template, spot))
                .orElse(template);
        return Mono.just(htmlResponse(html));
    }

    @GetMapping("/country/{countryName}")
    public Mono<ResponseEntity<String>> countryPage(@PathVariable String countryName) {
        String template = loadIndexTemplate();
        List<Spot> spots = aggregatorService.getSpots();
        String slug = countryName.toLowerCase(Locale.ROOT);
        String html = spots.stream()
                .map(Spot::country)
                .filter(c -> c != null && seoService.normalizeCountry(c).equals(slug))
                .findFirst()
                .map(country -> {
                    int count = (int) spots.stream()
                            .filter(s -> country.equals(s.country())).count();
                    return seoService.injectCountrySeo(template, country, count);
                })
                .orElse(template);
        return Mono.just(htmlResponse(html));
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public Mono<String> sitemap() {
        return Mono.just(seoService.buildSitemap(aggregatorService.getSpots()));
    }

    private ResponseEntity<String> htmlResponse(String html) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    private String loadSpotTemplate() {
        if (spotTemplate == null) {
            spotTemplate = readResource("static/spot.html");
        }
        return spotTemplate;
    }

    private String loadIndexTemplate() {
        if (indexTemplate == null) {
            indexTemplate = readResource("static/index.html");
        }
        return indexTemplate;
    }

    private String readResource(String path) {
        try {
            return new ClassPathResource(path)
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load template: " + path, e);
        }
    }
}
