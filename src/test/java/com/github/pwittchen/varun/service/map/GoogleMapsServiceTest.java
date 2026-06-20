package com.github.pwittchen.varun.service.map;

import com.github.pwittchen.varun.model.map.Coordinates;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;

class GoogleMapsServiceTest {

    @Test
    void shouldParseCoordinatesFromAtFormat() {
        Coordinates coordinates = GoogleMapsService.parseCoordinates(
                "https://www.google.com/maps/@54.82750,18.08778,14z");

        assertThat(coordinates).isNotNull();
        assertThat(coordinates.lat()).isWithin(1e-9).of(54.82750);
        assertThat(coordinates.lon()).isWithin(1e-9).of(18.08778);
    }

    @Test
    void shouldParseCoordinatesFromAtFormatWithTrailingData() {
        Coordinates coordinates = GoogleMapsService.parseCoordinates(
                "https://www.google.com/maps/place/Spot/@36.012,-5.605,17z/data=!3m1");

        assertThat(coordinates).isNotNull();
        assertThat(coordinates.lat()).isWithin(1e-9).of(36.012);
        assertThat(coordinates.lon()).isWithin(1e-9).of(-5.605);
    }

    @Test
    void shouldParseCoordinatesFromQueryParam() {
        Coordinates coordinates = GoogleMapsService.parseCoordinates(
                "https://www.google.com/maps?q=-33.093,18.028");

        assertThat(coordinates).isNotNull();
        assertThat(coordinates.lat()).isWithin(1e-9).of(-33.093);
        assertThat(coordinates.lon()).isWithin(1e-9).of(18.028);
    }

    @Test
    void shouldParseCoordinatesFromSearchQueryParam() {
        Coordinates coordinates = GoogleMapsService.parseCoordinates(
                "https://www.google.com/maps/search/?api=1&query=37.0839,-8.3213");

        assertThat(coordinates).isNotNull();
        assertThat(coordinates.lat()).isWithin(1e-9).of(37.0839);
        assertThat(coordinates.lon()).isWithin(1e-9).of(-8.3213);
    }

    @Test
    void shouldParseCoordinatesFromLlQueryParam() {
        Coordinates coordinates = GoogleMapsService.parseCoordinates(
                "https://maps.google.com/?ll=51.760158,3.847107&z=12");

        assertThat(coordinates).isNotNull();
        assertThat(coordinates.lat()).isWithin(1e-9).of(51.760158);
        assertThat(coordinates.lon()).isWithin(1e-9).of(3.847107);
    }

    @Test
    void shouldParseCoordinatesFromUrlEncodedQueryParam() {
        Coordinates coordinates = GoogleMapsService.parseCoordinates(
                "https://www.google.com/maps?q=43.062471%2C6.131498");

        assertThat(coordinates).isNotNull();
        assertThat(coordinates.lat()).isWithin(1e-9).of(43.062471);
        assertThat(coordinates.lon()).isWithin(1e-9).of(6.131498);
    }

    @Test
    void shouldReturnNullWhenNoCoordinatesPresent() {
        assertThat(GoogleMapsService.parseCoordinates(
                "https://www.google.com/maps/place/Some+Beach")).isNull();
    }

    @Test
    void shouldReturnNullForNullOrEmptyUrl() {
        assertThat(GoogleMapsService.parseCoordinates(null)).isNull();
        assertThat(GoogleMapsService.parseCoordinates("")).isNull();
    }
}
