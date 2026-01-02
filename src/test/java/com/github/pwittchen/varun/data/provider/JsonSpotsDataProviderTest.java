package com.github.pwittchen.varun.data.provider;

import com.github.pwittchen.varun.model.spot.Spot;
import com.github.pwittchen.varun.data.provider.spots.JsonSpotsDataProvider;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import reactor.test.StepVerifier;

import static com.google.common.truth.Truth.assertThat;

class JsonSpotsDataProviderTest {

    private JsonSpotsDataProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        Gson gson = new Gson();
        provider = new JsonSpotsDataProvider(gson);
    }

    @Test
    void shouldLoadDataRegardingFirstSpotFromTheProvidedJsonFile() {
        Spot spot = provider.getSpots().blockFirst();

        assertThat(spot).isNotNull();
        assertThat(spot.name()).isEqualTo("Jastarnia");
        assertThat(spot.country()).isEqualTo("Poland");
        assertThat(spot.windguruUrl()).isEqualTo("https://www.windguru.cz/500760");

        assertThat(spot.spotInfo()).isNotNull();
        assertThat(spot.spotInfo().bestWind()).isEqualTo("W, SW");
    }

    @Test
    void shouldLoadAllSpotsFromJsonFile() {
        StepVerifier
                .create(provider.getSpots())
                .expectNextCount(102)
                .verifyComplete();
    }

    @Test
    void shouldLoadSpotWithAllRequiredFields() {
        Spot spot = provider.getSpots().blockFirst();
        assertThat(spot).isNotNull();
        assertThat(spot.name()).isNotEmpty();
        assertThat(spot.country()).isNotEmpty();
        assertThat(spot.windguruUrl()).isNotEmpty();
        assertThat(spot.windfinderUrl()).isNotEmpty();
        assertThat(spot.spotInfo()).isNotNull();
    }

    @Test
    void shouldLoadSpotInfoWithAllFields() {
        Spot spot = provider.getSpots().blockFirst();
        assertThat(spot).isNotNull();
        assertThat(spot.spotInfo()).isNotNull();
        assertThat(spot.spotInfo().type()).isNotEmpty();
        assertThat(spot.spotInfo().bestWind()).isNotEmpty();
        assertThat(spot.spotInfo().waterTemp()).isNotEmpty();
        assertThat(spot.spotInfo().experience()).isNotEmpty();
        assertThat(spot.spotInfo().launch()).isNotEmpty();
        assertThat(spot.spotInfo().season()).isNotEmpty();
        assertThat(spot.spotInfo().description()).isNotEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "0, Jastarnia, Poland",
            "1, Chałupy, Poland",
            "2, Rewa, Poland",
            "3, Kuźnica, Poland"
    })
    void shouldLoadSpotAtIndexCorrectly(int index, String expectedName, String expectedCountry) {
        Spot spot = provider.getSpots().skip(index).blockFirst();
        assertThat(spot).isNotNull();
        assertThat(spot.name()).isEqualTo(expectedName);
        assertThat(spot.country()).isEqualTo(expectedCountry);
    }

    @Test
    void shouldInitializeForecastAsEmptyList() {
        Spot spot = provider.getSpots().blockFirst();
        assertThat(spot).isNotNull();
        assertThat(spot.forecast()).isNotNull();
        assertThat(spot.forecast()).isEmpty();
    }

    @Test
    void shouldLoadWgIdCorrectly() {
        Spot spot = provider.getSpots().blockFirst();
        assertThat(spot).isNotNull();
        assertThat(spot.wgId()).isEqualTo(500760);
    }
}