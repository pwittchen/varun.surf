package com.github.pwittchen.varun.provider;

import com.github.pwittchen.varun.model.Spot;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.LinkedList;
import java.util.List;

@Component
public class JsonSpotsDataProvider implements SpotsDataProvider {
    public static final String RESOURCE_FILE = "spots.json";
    private final List<Spot> spots = new LinkedList<>();

    public JsonSpotsDataProvider(Gson gson) throws Exception {
        try (Reader reader = new InputStreamReader(new ClassPathResource(RESOURCE_FILE).getInputStream())) {
            Type listType = new TypeToken<List<Spot>>() {
            }.getType();
            this.spots.addAll(gson.fromJson(reader, listType));
        }
    }

    @Override
    public Flux<Spot> getSpots() {
        return Flux
                .fromIterable(spots)
                .subscribeOn(Schedulers.boundedElastic());
    }
}
