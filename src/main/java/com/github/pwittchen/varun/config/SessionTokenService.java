package com.github.pwittchen.varun.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Mints and verifies the token carried by the SESSION cookie.
 * <p>
 * The token is {@code <expiry epoch second>.<base64url HMAC-SHA256 of that number>},
 * so the server keeps nothing. That matters twice over: there is no session map to
 * fill - the in-memory store this replaced accepted 10 000 sessions and then failed
 * every further page visit, which any bot could reach in a minute - and there is no
 * state a restart could lose. The second part is why the secret belongs in the
 * environment: two containers sharing {@code APP_SESSION_SECRET} accept each other's
 * cookies, so a blue-green swap stops logging every visitor out mid-session.
 * <p>
 * This is a gate, not a login. The token says "this client asked for a page first"
 * and nothing else, which is all a cookie handed to every visitor can ever be worth;
 * it costs a scraper one extra request. The real defences against abuse are the rate
 * limits in front of the application.
 */
@Component
public class SessionTokenService {

    private static final Logger log = LoggerFactory.getLogger(SessionTokenService.class);

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SEPARATOR = ".";
    private static final int GENERATED_SECRET_BYTES = 32;

    private static final long INVALID = -1L;

    private final SecretKeySpec key;
    private final Duration maxAge;

    public SessionTokenService(
            @Value("${app.session.secret:}") String secret,
            @Value("${app.session.max-age-seconds:86400}") long maxAgeSeconds
    ) {
        this.key = new SecretKeySpec(resolveSecret(secret), HMAC_ALGORITHM);
        this.maxAge = Duration.ofSeconds(maxAgeSeconds);
    }

    /**
     * @return a freshly signed token, valid for {@link #maxAge()} from now
     */
    public String issue() {
        long expiresAt = Instant.now().plus(maxAge).getEpochSecond();
        return expiresAt + SEPARATOR + sign(Long.toString(expiresAt));
    }

    /**
     * @param token cookie value to check, possibly null
     * @return true when the token carries our signature and has not expired
     */
    public boolean isValid(String token) {
        return expiryOf(token) > Instant.now().getEpochSecond();
    }

    /**
     * True for a valid token past its half-life, so an active visitor gets a fresh
     * cookie on the next page load instead of being cut off mid-visit. An invalid
     * or expired token answers false here - it is not renewed, it is replaced.
     *
     * @param token cookie value to check, possibly null
     */
    public boolean isDueForRenewal(String token) {
        long expiresAt = expiryOf(token);
        long now = Instant.now().getEpochSecond();
        return expiresAt > now && expiresAt - now < maxAge.toSeconds() / 2;
    }

    public Duration maxAge() {
        return maxAge;
    }

    private long expiryOf(String token) {
        if (token == null || token.isBlank()) {
            return INVALID;
        }

        int separator = token.indexOf(SEPARATOR);
        if (separator < 1 || separator == token.length() - 1) {
            return INVALID;
        }

        String expiry = token.substring(0, separator);
        String signature = token.substring(separator + 1);

        if (!MessageDigest.isEqual(
                signature.getBytes(StandardCharsets.UTF_8),
                sign(expiry).getBytes(StandardCharsets.UTF_8))) {
            return INVALID;
        }

        try {
            return Long.parseLong(expiry);
        } catch (NumberFormatException e) {
            return INVALID;
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            return Base64
                    .getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("cannot sign the session token", e);
        }
    }

    private byte[] resolveSecret(String secret) {
        if (secret != null && !secret.isBlank()) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }

        byte[] generated = new byte[GENERATED_SECRET_BYTES];
        new SecureRandom().nextBytes(generated);
        log.warn("app.session.secret is not set, generating a random one: cookies issued now "
                + "stop working after a restart and are not shared between instances. "
                + "Set APP_SESSION_SECRET to keep visitors signed in across deployments.");
        return generated;
    }
}
