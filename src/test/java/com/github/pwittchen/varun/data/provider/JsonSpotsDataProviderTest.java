package com.github.pwittchen.varun.data.provider;

import com.github.pwittchen.varun.model.spot.Spot;
import com.github.pwittchen.varun.data.spots.JsonSpotsDataProvider;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;

class JsonSpotsDataProviderTest {

    // a dedicated test fixture is used instead of the production spots.json so that
    // these tests stay stable when spots.json is updated over time
    private static final String TEST_RESOURCE_FILE = "spots-test.json";

    private JsonSpotsDataProvider fixtureProvider;
    private JsonSpotsDataProvider productionProvider;

    @BeforeEach
    void setUp() throws Exception {
        Gson gson = new Gson();
        fixtureProvider = new JsonSpotsDataProvider(gson, TEST_RESOURCE_FILE);
        productionProvider = new JsonSpotsDataProvider(gson);
    }

    @Test
    void shouldLoadDataRegardingFirstSpotFromTheProvidedJsonFile() {
        Spot spot = fixtureProvider.getSpots().blockFirst();

        assertThat(spot).isNotNull();
        assertThat(spot.name()).isEqualTo("Test Spot One");
        assertThat(spot.country()).isEqualTo("Testland");
        assertThat(spot.windguruUrl()).isEqualTo("https://www.windguru.cz/100001");

        assertThat(spot.spotInfo()).isNotNull();
        assertThat(spot.spotInfo().bestWind()).isEqualTo("W, SW");
    }

    @Test
    void shouldLoadAllSpotsFromTheProvidedJsonFile() {
        List<Spot> spots = fixtureProvider.getSpots().collectList().block();

        assertThat(spots).isNotNull();
        assertThat(spots).hasSize(3);
        assertThat(spots.stream().map(Spot::name).toList())
                .containsExactly("Test Spot One", "Test Spot Two", "Test Spot Three")
                .inOrder();
    }

    @Test
    void shouldLoadWgIdCorrectly() {
        Spot spot = fixtureProvider.getSpots().blockFirst();
        assertThat(spot).isNotNull();
        assertThat(spot.wgId()).isEqualTo(100001);
    }

    @Test
    void shouldInitializeForecastAsEmptyList() {
        Spot spot = fixtureProvider.getSpots().blockFirst();
        assertThat(spot).isNotNull();
        assertThat(spot.forecast()).isNotNull();
        assertThat(spot.forecast()).isEmpty();
    }

    // the tests below run against the real production spots.json but only assert
    // structural invariants, so they keep validating the data without breaking
    // whenever individual spot entries are added, removed or edited

    @Test
    void shouldLoadProductionSpots() {
        List<Spot> spots = productionProvider.getSpots().collectList().block();
        assertThat(spots).isNotNull();
        assertThat(spots).isNotEmpty();
    }

    @Test
    void shouldLoadEveryProductionSpotWithAllRequiredFields() {
        List<Spot> spots = productionProvider.getSpots().collectList().block();
        assertThat(spots).isNotNull();

        // windguruUrl and windfinderUrl are optional (some spots have neither),
        // so only the always-present fields are asserted here
        for (Spot spot : spots) {
            assertThat(spot.name()).isNotEmpty();
            assertThat(spot.country()).isNotEmpty();
            assertThat(spot.spotInfo()).isNotNull();
        }
    }

    @Test
    void shouldLoadEveryProductionSpotInfoWithAllFields() {
        List<Spot> spots = productionProvider.getSpots().collectList().block();
        assertThat(spots).isNotNull();

        for (Spot spot : spots) {
            assertThat(spot.spotInfo()).isNotNull();
            assertThat(spot.spotInfo().type()).isNotEmpty();
            assertThat(spot.spotInfo().bestWind()).isNotEmpty();
            assertThat(spot.spotInfo().waterTemp()).isNotEmpty();
            assertThat(spot.spotInfo().experience()).isNotEmpty();
            assertThat(spot.spotInfo().launch()).isNotEmpty();
            assertThat(spot.spotInfo().season()).isNotEmpty();
            assertThat(spot.spotInfo().description()).isNotEmpty();
        }
    }

    @Test
    void shouldInitializeForecastAsEmptyListForEveryProductionSpot() {
        List<Spot> spots = productionProvider.getSpots().collectList().block();
        assertThat(spots).isNotNull();

        for (Spot spot : spots) {
            assertThat(spot.forecast()).isNotNull();
            assertThat(spot.forecast()).isEmpty();
        }
    }
}
