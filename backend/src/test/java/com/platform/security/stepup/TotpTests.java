package com.platform.security.stepup;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** M9a: the codes are the ones authenticator apps show - RFC 6238's SHA-1 test vectors, cut to 6 digits. */
class TotpTests {

    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void matchesTheRfc6238TestVectors() {
        assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(59)))).isEqualTo("287082");
        assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(1111111109)))).isEqualTo("081804");
        assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(1111111111)))).isEqualTo("050471");
        assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(1234567890)))).isEqualTo("005924");
        assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(2000000000)))).isEqualTo("279037");
    }

    @Test
    void acceptsOneStepOfDriftAndNeverAStepAlreadyUsed() {
        Instant now = Instant.ofEpochSecond(1234567890);
        long step = Totp.step(now);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 1), now, null)).hasValue(step - 1);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step + 1), now, null)).hasValue(step + 1);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 2), now, null)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step), now, step)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, " " + Totp.code(RFC_SECRET, step) + " ", now, step - 1)).hasValue(step);
        assertThat(Totp.verify(RFC_SECRET, "12345", now, null)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, null, now, null)).isEmpty();
    }

    @Test
    void base32RoundTripsAsAuthenticatorAppsExpect() {
        assertThat(Totp.base32(RFC_SECRET)).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(Totp.fromBase32("gezd gnbv gy3t qojq gezd gnbv gy3t qojq")).isEqualTo(RFC_SECRET);
        byte[] secret = Totp.newSecret();
        assertThat(Totp.fromBase32(Totp.base32(secret))).isEqualTo(secret);
    }

}
