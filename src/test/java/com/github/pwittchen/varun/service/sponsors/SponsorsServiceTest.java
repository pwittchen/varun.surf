package com.github.pwittchen.varun.service.sponsors;

import com.github.pwittchen.varun.model.sponsor.Sponsor;
import com.github.pwittchen.varun.data.sponsors.SponsorsDataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SponsorsServiceTest {

    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(10);

    @Mock
    private SponsorsDataProvider sponsorsDataProvider;

    private SponsorsService sponsorsService;

    @BeforeEach
    void setUp() {
        sponsorsService = new SponsorsService(sponsorsDataProvider);
    }

    /**
     * {@link SponsorsService#init()} loads sponsors on a boundedElastic thread, so tests have to wait
     * for them to actually arrive rather than for a fixed amount of time, which is not long enough on
     * a loaded CI runner.
     */
    private void initAndAwaitSponsors(int expectedSponsorCount) {
        sponsorsService.init();
        awaitUntil(
                expectedSponsorCount + " sponsor(s) to be loaded",
                () -> sponsorsService.getSponsors().size() == expectedSponsorCount
        );
    }

    private static void awaitUntil(String description, BooleanSupplier condition) {
        var deadline = System.nanoTime() + AWAIT_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted while waiting for " + description, e);
            }
        }
        throw new AssertionError("timed out after " + AWAIT_TIMEOUT + " waiting for " + description);
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
    void shouldInitializeWithSponsors() {
        // given
        var sponsor1 = new Sponsor(0, true, "Onet", "https://onet.pl");
        var sponsor2 = new Sponsor(1, false, "Sponsor2", "https://sponsor2.com");
        when(sponsorsDataProvider.getSponsors()).thenReturn(Flux.just(sponsor1, sponsor2));

        // when
        initAndAwaitSponsors(2);

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
    void shouldReturnEmptyListWhenNoSponsors() {
        // given
        when(sponsorsDataProvider.getSponsors()).thenReturn(Flux.empty());
        initAndAwaitSponsors(0);

        // when
        List<Sponsor> result = sponsorsService.getSponsors();

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnAllSponsors() {
        // given
        var sponsor1 = new Sponsor(0, true, "Onet", "https://onet.pl");
        var sponsor2 = new Sponsor(1, false, "Sponsor2", "https://sponsor2.com");
        when(sponsorsDataProvider.getSponsors()).thenReturn(Flux.just(sponsor1, sponsor2));
        initAndAwaitSponsors(2);

        // when
        List<Sponsor> result = sponsorsService.getSponsors();

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Onet");
        assertThat(result.get(1).name()).isEqualTo("Sponsor2");
    }

    @Test
    void shouldReturnSponsorById() {
        // given
        var sponsor1 = new Sponsor(0, true, "Onet", "https://onet.pl");
        var sponsor2 = new Sponsor(1, false, "Sponsor2", "https://sponsor2.com");
        when(sponsorsDataProvider.getSponsors()).thenReturn(Flux.just(sponsor1, sponsor2));
        initAndAwaitSponsors(2);

        // when
        Optional<Sponsor> result = sponsorsService.getSponsorById(0);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("Onet");
        assertThat(result.get().id()).isEqualTo(0);
    }

    @Test
    void shouldReturnEmptyOptionalWhenSponsorNotFound() {
        // given
        var sponsor1 = new Sponsor(0, true, "Onet", "https://onet.pl");
        when(sponsorsDataProvider.getSponsors()).thenReturn(Flux.just(sponsor1));
        initAndAwaitSponsors(1);

        // when
        Optional<Sponsor> result = sponsorsService.getSponsorById(999);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnOnlyMainSponsors() {
        // given
        var sponsor1 = new Sponsor(0, true, "Onet", "https://onet.pl");
        var sponsor2 = new Sponsor(1, false, "Sponsor2", "https://sponsor2.com");
        var sponsor3 = new Sponsor(2, true, "MainSponsor", "https://main.com");
        when(sponsorsDataProvider.getSponsors()).thenReturn(Flux.just(sponsor1, sponsor2, sponsor3));
        initAndAwaitSponsors(3);

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
    void shouldReturnEmptyListWhenNoMainSponsors() {
        // given
        var sponsor1 = new Sponsor(0, false, "Sponsor1", "https://sponsor1.com");
        var sponsor2 = new Sponsor(1, false, "Sponsor2", "https://sponsor2.com");
        when(sponsorsDataProvider.getSponsors()).thenReturn(Flux.just(sponsor1, sponsor2));
        initAndAwaitSponsors(2);

        // when
        List<Sponsor> result = sponsorsService.getMainSponsors();

        // then
        assertThat(result).isEmpty();
    }
}