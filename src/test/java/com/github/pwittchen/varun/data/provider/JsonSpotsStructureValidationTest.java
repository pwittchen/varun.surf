package com.github.pwittchen.varun.data.provider;

import com.github.pwittchen.varun.model.spot.Spot;
import com.github.pwittchen.varun.model.spot.SpotInfo;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class JsonSpotsStructureValidationTest {

    private Gson gson;

    @BeforeEach
    void setUp() {
        gson = new Gson();
    }

    @Test
    void shouldSuccessfullyParseValidSpotsJsonFile() {
        assertDoesNotThrow(() -> {
            try (Reader reader = new InputStreamReader(new ClassPathResource("spots.json").getInputStream())) {
                Type listType = new TypeToken<List<Spot>>() {}.getType();
                List<Spot> spots = gson.fromJson(reader, listType);
                assertThat(spots).isNotNull();
                assertThat(spots).isNotEmpty();
            }
        });
    }

    @Test
    void shouldValidateAllSpotsHaveRequiredFields() throws Exception {
        try (Reader reader = new InputStreamReader(new ClassPathResource("spots.json").getInputStream())) {
            Type listType = new TypeToken<List<Spot>>() {}.getType();
            List<Spot> spots = gson.fromJson(reader, listType);

            for (int i = 0; i < spots.size(); i++) {
                Spot spot = spots.get(i);
                String spotIdentifier = "Spot at index " + i + (spot.name() != null ? " (" + spot.name() + ")" : "");

                // Validate required string fields are not null
                assertThat(spot.name()).isNotNull();
                assertThat(spot.country()).isNotNull();
                assertThat(spot.windguruUrl()).isNotNull();
                assertThat(spot.windfinderUrl()).isNotNull();
                assertThat(spot.icmUrl()).isNotNull();
                assertThat(spot.webcamUrl()).isNotNull();
                assertThat(spot.locationUrl()).isNotNull();
                assertThat(spot.spotInfo()).isNotNull();
            }
        }
    }

    @Test
    void shouldValidateAllSpotsHaveNonEmptyRequiredFields() throws Exception {
        try (Reader reader = new InputStreamReader(new ClassPathResource("spots.json").getInputStream())) {
            Type listType = new TypeToken<List<Spot>>() {}.getType();
            List<Spot> spots = gson.fromJson(reader, listType);

            for (Spot spot : spots) {
                // Validate required string fields are not empty (allow empty for optional fields)
                assertThat(spot.name()).isNotEmpty();
                assertThat(spot.country()).isNotEmpty();
                // windguruUrl can be empty if windguruFallbackUrl is provided
                if (spot.windguruUrl() == null || spot.windguruUrl().isEmpty()) {
                    assertThat(spot.windguruFallbackUrl()).isNotEmpty();
                }
            }
        }
    }

    @Test
    void shouldValidateAllSpotInfoHaveRequiredFields() throws Exception {
        try (Reader reader = new InputStreamReader(new ClassPathResource("spots.json").getInputStream())) {
            Type listType = new TypeToken<List<Spot>>() {}.getType();
            List<Spot> spots = gson.fromJson(reader, listType);

            for (Spot spot : spots) {
                SpotInfo spotInfo = spot.spotInfo();

                assertThat(spotInfo).isNotNull();

                // Validate all SpotInfo fields are not null
                assertThat(spotInfo.type()).isNotNull();
                assertThat(spotInfo.bestWind()).isNotNull();
                assertThat(spotInfo.waterTemp()).isNotNull();
                assertThat(spotInfo.experience()).isNotNull();
                assertThat(spotInfo.launch()).isNotNull();
                assertThat(spotInfo.hazards()).isNotNull();
                assertThat(spotInfo.season()).isNotNull();
                assertThat(spotInfo.description()).isNotNull();
            }
        }
    }

    @Test
    void shouldValidateAllSpotInfoHaveNonEmptyRequiredFields() throws Exception {
        try (Reader reader = new InputStreamReader(new ClassPathResource("spots.json").getInputStream())) {
            Type listType = new TypeToken<List<Spot>>() {}.getType();
            List<Spot> spots = gson.fromJson(reader, listType);

            for (Spot spot : spots) {
                SpotInfo spotInfo = spot.spotInfo();

                // Validate critical SpotInfo fields are not empty (allow empty for hazards)
                assertThat(spotInfo.type()).isNotEmpty();
                assertThat(spotInfo.bestWind()).isNotEmpty();
                assertThat(spotInfo.waterTemp()).isNotEmpty();
                assertThat(spotInfo.experience()).isNotEmpty();
                assertThat(spotInfo.launch()).isNotEmpty();
                assertThat(spotInfo.season()).isNotEmpty();
                assertThat(spotInfo.description()).isNotEmpty();
            }
        }
    }

    @Test
    void shouldValidateWindguruUrlFormat() throws Exception {
        try (Reader reader = new InputStreamReader(new ClassPathResource("spots.json").getInputStream())) {
            Type listType = new TypeToken<List<Spot>>() {}.getType();
            List<Spot> spots = gson.fromJson(reader, listType);

            for (Spot spot : spots) {
                // windguruUrl can be empty if windguruFallbackUrl is provided
                if (spot.windguruUrl() != null && !spot.windguruUrl().isEmpty()) {
                    // Validate windguruUrl format
                    assertThat(spot.windguruUrl()).startsWith("https://www.windguru.cz/");

                    // Validate wgId can be extracted (i.e., URL ends with a number)
                    String[] parts = spot.windguruUrl().split("/");
                    String lastPart = parts[parts.length - 1];
                    assertThat(lastPart).matches("\\d+");
                } else {
                    // If windguruUrl is empty, windguruFallbackUrl must be valid
                    assertThat(spot.windguruFallbackUrl()).startsWith("https://www.windguru.cz/");
                    String[] parts = spot.windguruFallbackUrl().split("/");
                    String lastPart = parts[parts.length - 1];
                    assertThat(lastPart).matches("\\d+");
                }
            }
        }
    }

    @Test
    void shouldValidateWgIdExtraction() throws Exception {
        try (Reader reader = new InputStreamReader(new ClassPathResource("spots.json").getInputStream())) {
            Type listType = new TypeToken<List<Spot>>() {}.getType();
            List<Spot> spots = gson.fromJson(reader, listType);

            for (int i = 0; i < spots.size(); i++) {
                Spot spot = spots.get(i);

                // Validate wgId can be extracted and is positive
                int wgId = spot.wgId();
                assertThat(wgId).isGreaterThan(0);
            }
        }
    }

    @Test
    void shouldValidateJsonIsArrayAtRoot() throws Exception {
        try (Reader reader = new InputStreamReader(new ClassPathResource("spots.json").getInputStream())) {
            // This test ensures the root element is an array
            Type listType = new TypeToken<List<Spot>>() {}.getType();
            List<Spot> spots = gson.fromJson(reader, listType);

            assertThat(spots).isNotNull();
            assertThat(spots).isInstanceOf(List.class);
        }
    }

    @Test
    void shouldValidateNoDuplicateSpotNames() throws Exception {
        try (Reader reader = new InputStreamReader(new ClassPathResource("spots.json").getInputStream())) {
            Type listType = new TypeToken<List<Spot>>() {}.getType();
            List<Spot> spots = gson.fromJson(reader, listType);

            List<String> names = spots.stream().map(Spot::name).toList();
            List<String> uniqueNames = names.stream().distinct().toList();

            assertThat(names.size()).isEqualTo(uniqueNames.size());
        }
    }

    @Test
    void shouldValidateNoDuplicateWindguruUrls() throws Exception {
        try (Reader reader = new InputStreamReader(new ClassPathResource("spots.json").getInputStream())) {
            Type listType = new TypeToken<List<Spot>>() {}.getType();
            List<Spot> spots = gson.fromJson(reader, listType);

            List<String> urls = spots.stream().map(Spot::windguruUrl).toList();
            List<String> uniqueUrls = urls.stream().distinct().toList();

            assertThat(urls.size()).isEqualTo(uniqueUrls.size());
        }
    }

    @Test
    void shouldValidateAllSpotsHaveSpotInfoPL() throws Exception {
        try (Reader reader = new InputStreamReader(new ClassPathResource("spots.json").getInputStream())) {
            Type listType = new TypeToken<List<Spot>>() {}.getType();
            List<Spot> spots = gson.fromJson(reader, listType);

            for (Spot spot : spots) {
                // Validate spotInfoPL exists
                assertThat(spot.spotInfoPL()).isNotNull();
            }
        }
    }

    @Test
    void shouldValidateAllSpotInfoPLHaveRequiredFields() throws Exception {
        try (Reader reader = new InputStreamReader(new ClassPathResource("spots.json").getInputStream())) {
            Type listType = new TypeToken<List<Spot>>() {}.getType();
            List<Spot> spots = gson.fromJson(reader, listType);

            for (Spot spot : spots) {
                SpotInfo spotInfoPL = spot.spotInfoPL();

                assertThat(spotInfoPL).isNotNull();

                // Validate all SpotInfoPL fields are not null
                assertThat(spotInfoPL.type()).isNotNull();
                assertThat(spotInfoPL.bestWind()).isNotNull();
                assertThat(spotInfoPL.waterTemp()).isNotNull();
                assertThat(spotInfoPL.experience()).isNotNull();
                assertThat(spotInfoPL.launch()).isNotNull();
                assertThat(spotInfoPL.hazards()).isNotNull();
                assertThat(spotInfoPL.season()).isNotNull();
                assertThat(spotInfoPL.description()).isNotNull();
            }
        }
    }

    @Test
    void shouldValidateAllSpotInfoPLHaveNonEmptyRequiredFields() throws Exception {
        try (Reader reader = new InputStreamReader(new ClassPathResource("spots.json").getInputStream())) {
            Type listType = new TypeToken<List<Spot>>() {}.getType();
            List<Spot> spots = gson.fromJson(reader, listType);

            for (Spot spot : spots) {
                SpotInfo spotInfoPL = spot.spotInfoPL();

                // Validate critical SpotInfoPL fields are not empty (allow empty for hazards)
                assertThat(spotInfoPL.type()).isNotEmpty();
                assertThat(spotInfoPL.bestWind()).isNotEmpty();
                assertThat(spotInfoPL.waterTemp()).isNotEmpty();
                assertThat(spotInfoPL.experience()).isNotEmpty();
                assertThat(spotInfoPL.launch()).isNotEmpty();
                assertThat(spotInfoPL.season()).isNotEmpty();
                assertThat(spotInfoPL.description()).isNotEmpty();
            }
        }
    }

    @Test
    void shouldValidateSpotInfoAndSpotInfoPLHaveSameStructure() throws Exception {
        try (Reader reader = new InputStreamReader(new ClassPathResource("spots.json").getInputStream())) {
            Type listType = new TypeToken<List<Spot>>() {}.getType();
            List<Spot> spots = gson.fromJson(reader, listType);

            for (Spot spot : spots) {
                SpotInfo spotInfo = spot.spotInfo();
                SpotInfo spotInfoPL = spot.spotInfoPL();

                // Verify both have the same fields populated (not comparing content, just structure)
                assertThat(spotInfo.type()).isNotNull();
                assertThat(spotInfoPL.type()).isNotNull();

                assertThat(spotInfo.bestWind()).isNotNull();
                assertThat(spotInfoPL.bestWind()).isNotNull();

                assertThat(spotInfo.waterTemp()).isNotNull();
                assertThat(spotInfoPL.waterTemp()).isNotNull();

                assertThat(spotInfo.experience()).isNotNull();
                assertThat(spotInfoPL.experience()).isNotNull();

                assertThat(spotInfo.launch()).isNotNull();
                assertThat(spotInfoPL.launch()).isNotNull();

                assertThat(spotInfo.hazards()).isNotNull();
                assertThat(spotInfoPL.hazards()).isNotNull();

                assertThat(spotInfo.season()).isNotNull();
                assertThat(spotInfoPL.season()).isNotNull();

                assertThat(spotInfo.description()).isNotNull();
                assertThat(spotInfoPL.description()).isNotNull();
            }
        }
    }
}