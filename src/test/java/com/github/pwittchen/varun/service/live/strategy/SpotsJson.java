package com.github.pwittchen.varun.service.live.strategy;

import com.github.pwittchen.varun.model.spot.Spot;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.List;

/**
 * Test helper reading spots.json, so that strategies binding themselves to a generated
 * wgId (spots without their own Windguru station) can be verified against real spot data.
 */
final class SpotsJson {

    private SpotsJson() {
    }

    static int wgIdOf(String spotName) {
        try (Reader reader = new InputStreamReader(new ClassPathResource("spots.json").getInputStream())) {
            Type listType = new TypeToken<List<Spot>>() {
            }.getType();
            List<Spot> spots = new Gson().fromJson(reader, listType);
            return spots
                    .stream()
                    .filter(spot -> spotName.equals(spot.name()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No spot named " + spotName + " in spots.json"))
                    .wgId();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read spots.json", e);
        }
    }
}
