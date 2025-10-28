package com.github.pwittchen.varun.controller;

import com.github.pwittchen.varun.model.sponsor.Sponsor;
import com.github.pwittchen.varun.service.SponsorsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SponsorsControllerTest {

    @Mock
    private SponsorsService sponsorsService;

    private SponsorsController controller;

    @BeforeEach
    void setUp() {
        controller = new SponsorsController(sponsorsService);
    }

    @Test
    void shouldReturnEmptyFluxWhenNoSponsors() {
        when(sponsorsService.getSponsors()).thenReturn(new ArrayList<>());

        Flux<Sponsor> result = controller.sponsors();

        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void shouldReturnSponsorsFromService() {
        List<Sponsor> mockSponsors = createMockSponsors();
        when(sponsorsService.getSponsors()).thenReturn(mockSponsors);

        Flux<Sponsor> result = controller.sponsors();

        StepVerifier.create(result)
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void shouldReturnSponsorsWithCorrectData() {
        List<Sponsor> mockSponsors = createMockSponsors();
        when(sponsorsService.getSponsors()).thenReturn(mockSponsors);

        Flux<Sponsor> result = controller.sponsors();

        StepVerifier.create(result)
                .assertNext(sponsor -> {
                    assertThat(sponsor.name()).isEqualTo("Onet");
                    assertThat(sponsor.link()).isEqualTo("https://onet.pl");
                    assertThat(sponsor.main()).isTrue();
                })
                .assertNext(sponsor -> {
                    assertThat(sponsor.name()).isEqualTo("Sponsor2");
                    assertThat(sponsor.link()).isEqualTo("https://sponsor2.com");
                    assertThat(sponsor.main()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnSponsorByIdWhenSponsorExists() {
        Sponsor mockSponsor = new Sponsor(0, true, "Onet", "https://onet.pl");
        when(sponsorsService.getSponsorById(0)).thenReturn(Optional.of(mockSponsor));

        Mono<ResponseEntity<Sponsor>> result = controller.sponsor(0);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().name()).isEqualTo("Onet");
                    assertThat(response.getBody().link()).isEqualTo("https://onet.pl");
                    assertThat(response.getBody().id()).isEqualTo(0);
                })
                .verifyComplete();
    }

    @Test
    void shouldReturn404WhenSponsorNotFound() {
        when(sponsorsService.getSponsorById(999)).thenReturn(Optional.empty());

        Mono<ResponseEntity<Sponsor>> result = controller.sponsor(999);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(response.getBody()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnSponsorWithAllFields() {
        Sponsor mockSponsor = new Sponsor(1, false, "TestSponsor", "https://test.com");
        when(sponsorsService.getSponsorById(1)).thenReturn(Optional.of(mockSponsor));

        Mono<ResponseEntity<Sponsor>> result = controller.sponsor(1);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody()).isNotNull();
                    Sponsor sponsor = response.getBody();
                    assertThat(sponsor.id()).isEqualTo(1);
                    assertThat(sponsor.main()).isFalse();
                    assertThat(sponsor.name()).isEqualTo("TestSponsor");
                    assertThat(sponsor.link()).isEqualTo("https://test.com");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyFluxWhenNoMainSponsors() {
        when(sponsorsService.getMainSponsors()).thenReturn(new ArrayList<>());

        Flux<Sponsor> result = controller.mainSponsors();

        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void shouldReturnMainSponsorsFromService() {
        List<Sponsor> mockMainSponsors = createMockMainSponsors();
        when(sponsorsService.getMainSponsors()).thenReturn(mockMainSponsors);

        Flux<Sponsor> result = controller.mainSponsors();

        StepVerifier.create(result)
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void shouldReturnOnlyMainSponsors() {
        List<Sponsor> mockMainSponsors = createMockMainSponsors();
        when(sponsorsService.getMainSponsors()).thenReturn(mockMainSponsors);

        Flux<Sponsor> result = controller.mainSponsors();

        StepVerifier.create(result)
                .assertNext(sponsor -> {
                    assertThat(sponsor.main()).isTrue();
                    assertThat(sponsor.name()).isEqualTo("Onet");
                })
                .assertNext(sponsor -> {
                    assertThat(sponsor.main()).isTrue();
                    assertThat(sponsor.name()).isEqualTo("MainSponsor");
                })
                .verifyComplete();
    }

    @Test
    void shouldReturnMainSponsorsWithCorrectData() {
        List<Sponsor> mockMainSponsors = List.of(
                new Sponsor(0, true, "Onet", "https://onet.pl")
        );
        when(sponsorsService.getMainSponsors()).thenReturn(mockMainSponsors);

        Flux<Sponsor> result = controller.mainSponsors();

        StepVerifier.create(result)
                .assertNext(sponsor -> {
                    assertThat(sponsor.id()).isEqualTo(0);
                    assertThat(sponsor.main()).isTrue();
                    assertThat(sponsor.name()).isEqualTo("Onet");
                    assertThat(sponsor.link()).isEqualTo("https://onet.pl");
                })
                .verifyComplete();
    }

    private List<Sponsor> createMockSponsors() {
        return List.of(
                new Sponsor(0, true, "Onet", "https://onet.pl"),
                new Sponsor(1, false, "Sponsor2", "https://sponsor2.com")
        );
    }

    private List<Sponsor> createMockMainSponsors() {
        return List.of(
                new Sponsor(0, true, "Onet", "https://onet.pl"),
                new Sponsor(2, true, "MainSponsor", "https://main.com")
        );
    }
}