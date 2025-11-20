package com.github.pwittchen.varun.controller;

import com.github.pwittchen.varun.model.sponsor.Sponsor;
import com.github.pwittchen.varun.service.sponsors.SponsorsService;
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