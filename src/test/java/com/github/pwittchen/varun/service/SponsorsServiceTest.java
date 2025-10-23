package com.github.pwittchen.varun.service;

import com.github.pwittchen.varun.model.Sponsor;
import com.github.pwittchen.varun.provider.SponsorsDataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SponsorsServiceTest {

    @Mock
    private SponsorsDataProvider sponsorsDataProvider;

    private SponsorsService sponsorsService;

    @BeforeEach
    void setUp() {
        sponsorsService = new SponsorsService(sponsorsDataProvider);
    }

    @Test
    void shouldInitializeWithEmptySponsors() {
        // given
        when(sponsorsDataProvider.getSponsors()).thenReturn(Flux.empty());

        // when
        sponsorsService.init();

        // then
        verify(sponsorsDataProvider).getSponsors();
    }

    @Test
    void shouldInitializeWithSponsors() throws InterruptedException {
        // given
        var sponsor1 = new Sponsor(0, true, "Onet", "https://onet.pl", "onet.png");
        var sponsor2 = new Sponsor(1, false, "Sponsor2", "https://sponsor2.com", "sponsor2.png");
        when(sponsorsDataProvider.getSponsors()).thenReturn(Flux.just(sponsor1, sponsor2));

        // when
        sponsorsService.init();

        // give reactor time to process async
        Thread.sleep(100);

        // then
        verify(sponsorsDataProvider).getSponsors();
        assertThat(sponsorsService.getSponsors()).hasSize(2);
    }

    @Test
    void shouldHandleErrorDuringInitialization() {
        // given
        when(sponsorsDataProvider.getSponsors()).thenReturn(Flux.error(new RuntimeException("Failed to load")));

        // when
        sponsorsService.init();

        // then - should not throw, error is logged
        verify(sponsorsDataProvider).getSponsors();
    }

    @Test
    void shouldReturnEmptyListWhenNoSponsors() throws InterruptedException {
        // given
        when(sponsorsDataProvider.getSponsors()).thenReturn(Flux.empty());
        sponsorsService.init();
        Thread.sleep(100);

        // when
        List<Sponsor> result = sponsorsService.getSponsors();

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnAllSponsors() throws InterruptedException {
        // given
        var sponsor1 = new Sponsor(0, true, "Onet", "https://onet.pl", "onet.png");
        var sponsor2 = new Sponsor(1, false, "Sponsor2", "https://sponsor2.com", "sponsor2.png");
        when(sponsorsDataProvider.getSponsors()).thenReturn(Flux.just(sponsor1, sponsor2));
        sponsorsService.init();
        Thread.sleep(100);

        // when
        List<Sponsor> result = sponsorsService.getSponsors();

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Onet");
        assertThat(result.get(1).name()).isEqualTo("Sponsor2");
    }

    @Test
    void shouldReturnSponsorById() throws InterruptedException {
        // given
        var sponsor1 = new Sponsor(0, true, "Onet", "https://onet.pl", "onet.png");
        var sponsor2 = new Sponsor(1, false, "Sponsor2", "https://sponsor2.com", "sponsor2.png");
        when(sponsorsDataProvider.getSponsors()).thenReturn(Flux.just(sponsor1, sponsor2));
        sponsorsService.init();
        Thread.sleep(100);

        // when
        Optional<Sponsor> result = sponsorsService.getSponsorById(0);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("Onet");
        assertThat(result.get().id()).isEqualTo(0);
    }

    @Test
    void shouldReturnEmptyOptionalWhenSponsorNotFound() throws InterruptedException {
        // given
        var sponsor1 = new Sponsor(0, true, "Onet", "https://onet.pl", "onet.png");
        when(sponsorsDataProvider.getSponsors()).thenReturn(Flux.just(sponsor1));
        sponsorsService.init();
        Thread.sleep(100);

        // when
        Optional<Sponsor> result = sponsorsService.getSponsorById(999);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnOnlyMainSponsors() throws InterruptedException {
        // given
        var sponsor1 = new Sponsor(0, true, "Onet", "https://onet.pl", "onet.png");
        var sponsor2 = new Sponsor(1, false, "Sponsor2", "https://sponsor2.com", "sponsor2.png");
        var sponsor3 = new Sponsor(2, true, "MainSponsor", "https://main.com", "main.png");
        when(sponsorsDataProvider.getSponsors()).thenReturn(Flux.just(sponsor1, sponsor2, sponsor3));
        sponsorsService.init();
        Thread.sleep(100);

        // when
        List<Sponsor> result = sponsorsService.getMainSponsors();

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).main()).isTrue();
        assertThat(result.get(1).main()).isTrue();
        assertThat(result.get(0).name()).isEqualTo("Onet");
        assertThat(result.get(1).name()).isEqualTo("MainSponsor");
    }

    @Test
    void shouldReturnEmptyListWhenNoMainSponsors() throws InterruptedException {
        // given
        var sponsor1 = new Sponsor(0, false, "Sponsor1", "https://sponsor1.com", "sponsor1.png");
        var sponsor2 = new Sponsor(1, false, "Sponsor2", "https://sponsor2.com", "sponsor2.png");
        when(sponsorsDataProvider.getSponsors()).thenReturn(Flux.just(sponsor1, sponsor2));
        sponsorsService.init();
        Thread.sleep(100);

        // when
        List<Sponsor> result = sponsorsService.getMainSponsors();

        // then
        assertThat(result).isEmpty();
    }
}