package com.github.pwittchen.varun.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.google.common.truth.Truth.assertThat;

public class SessionTokenServiceTest {

    private static final String SECRET = "test-secret-that-is-long-enough";
    private static final long ONE_DAY = Duration.ofDays(1).toSeconds();

    @Test
    void shouldAcceptTokenItIssued() {
        var service = new SessionTokenService(SECRET, ONE_DAY);

        assertThat(service.isValid(service.issue())).isTrue();
    }

    @Test
    void shouldRejectNullToken() {
        var service = new SessionTokenService(SECRET, ONE_DAY);

        assertThat(service.isValid(null)).isFalse();
    }

    @Test
    void shouldRejectBlankToken() {
        var service = new SessionTokenService(SECRET, ONE_DAY);

        assertThat(service.isValid("  ")).isFalse();
    }

    @Test
    void shouldRejectMalformedToken() {
        var service = new SessionTokenService(SECRET, ONE_DAY);

        assertThat(service.isValid("not-a-token")).isFalse();
    }

    @Test
    void shouldRejectTokenWithoutSignature() {
        var service = new SessionTokenService(SECRET, ONE_DAY);

        assertThat(service.isValid("9999999999.")).isFalse();
    }

    @Test
    void shouldRejectTokenWithTamperedExpiry() {
        var service = new SessionTokenService(SECRET, ONE_DAY);
        String token = service.issue();
        String signature = token.substring(token.indexOf('.') + 1);

        assertThat(service.isValid("9999999999." + signature)).isFalse();
    }

    @Test
    void shouldRejectTokenWithTamperedSignature() {
        var service = new SessionTokenService(SECRET, ONE_DAY);
        String token = service.issue();
        String expiry = token.substring(0, token.indexOf('.'));

        assertThat(service.isValid(expiry + ".AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")).isFalse();
    }

    @Test
    void shouldRejectTokenSignedWithAnotherSecret() {
        var issuer = new SessionTokenService(SECRET, ONE_DAY);
        var verifier = new SessionTokenService("a-completely-different-secret", ONE_DAY);

        assertThat(verifier.isValid(issuer.issue())).isFalse();
    }

    @Test
    void shouldAcceptTokenAcrossInstancesSharingTheSecret() {
        var blue = new SessionTokenService(SECRET, ONE_DAY);
        var green = new SessionTokenService(SECRET, ONE_DAY);

        assertThat(green.isValid(blue.issue())).isTrue();
    }

    @Test
    void shouldRejectExpiredToken() {
        var service = new SessionTokenService(SECRET, 0);

        assertThat(service.isValid(service.issue())).isFalse();
    }

    @Test
    void shouldNotRenewFreshToken() {
        var service = new SessionTokenService(SECRET, ONE_DAY);

        assertThat(service.isDueForRenewal(service.issue())).isFalse();
    }

    @Test
    void shouldRenewTokenPastItsHalfLife() {
        var issuer = new SessionTokenService(SECRET, ONE_DAY);
        // a one-day token read by an instance handing out ten-day ones sits well
        // past the half-life the reader measures it against
        var reader = new SessionTokenService(SECRET, Duration.ofDays(10).toSeconds());

        assertThat(reader.isDueForRenewal(issuer.issue())).isTrue();
    }

    @Test
    void shouldNotRenewInvalidToken() {
        var service = new SessionTokenService(SECRET, ONE_DAY);

        assertThat(service.isDueForRenewal("not-a-token")).isFalse();
    }

    @Test
    void shouldGenerateSecretWhenNoneConfigured() {
        var first = new SessionTokenService("", ONE_DAY);
        var second = new SessionTokenService("", ONE_DAY);

        assertThat(first.isValid(first.issue())).isTrue();
        assertThat(second.isValid(first.issue())).isFalse();
    }

    @Test
    void shouldExposeConfiguredMaxAge() {
        var service = new SessionTokenService(SECRET, ONE_DAY);

        assertThat(service.maxAge()).isEqualTo(Duration.ofDays(1));
    }
}
