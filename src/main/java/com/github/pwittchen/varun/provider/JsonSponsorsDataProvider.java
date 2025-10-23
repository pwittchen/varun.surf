package com.github.pwittchen.varun.provider;

import com.github.pwittchen.varun.model.Sponsor;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.LinkedList;
import java.util.List;

@Component
public class JsonSponsorsDataProvider implements SponsorsDataProvider {
    public static final String RESOURCE_FILE = "sponsors.json";
    private final List<Sponsor> sponsors = new LinkedList<>();

    public JsonSponsorsDataProvider(Gson gson) throws Exception {
        try (Reader reader = new InputStreamReader(new ClassPathResource(RESOURCE_FILE).getInputStream())) {
            Type listType = new TypeToken<List<Sponsor>>() {
            }.getType();
            this.sponsors.addAll(gson.fromJson(reader, listType));
        }
    }

    @Override
    public Flux<Sponsor> getSponsors() {
        return Flux.fromIterable(sponsors);
    }
}