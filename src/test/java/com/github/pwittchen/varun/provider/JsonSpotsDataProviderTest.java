package com.github.pwittchen.varun.provider;

import com.github.pwittchen.varun.model.Spot;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;

class JsonSpotsDataProviderTest {
    @Test
    void shouldLoadDataRegardingFirstSpotFromTheProvidedJsonFile() throws Exception {
        Gson gson = new Gson();

        SpotsDataProvider provider = new JsonSpotsDataProvider(gson);
        Spot spot = provider.getSpots().blockFirst();

        assertThat(spot).isNotNull();
        assertThat(spot.name()).isEqualTo("Jastarnia");
        assertThat(spot.country()).isEqualTo("Poland");
        assertThat(spot.windguruUrl()).isEqualTo("https://www.windguru.cz/500760");

        assertThat(spot.spotInfo()).isNotNull();
        assertThat(spot.spotInfo().bestWind()).isEqualTo("W, SW");
    }
}