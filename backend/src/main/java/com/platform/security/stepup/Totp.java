package com.platform.security.stepup;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * M9a: time-based one-time codes (RFC 6238 over RFC 4226): HMAC-SHA1, 6 digits, 30-second steps - what every
 * authenticator app speaks. Secrets are 20 random bytes, shown to the person once as base32 (RFC 4648, no padding).
 */
public final class Totp {

    public static final int DIGITS = 6;
    public static final long STEP_SECONDS = 30;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    public static byte[] newSecret() {
        byte[] secret = new byte[20];
        RANDOM.nextBytes(secret);
        return secret;
    }

    public static long step(Instant at) {
        return Math.floorDiv(at.getEpochSecond(), STEP_SECONDS);
    }

    /** The code for one time step. */
    public static String code(byte[] secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16) | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return String.format(Locale.ROOT, "%0" + DIGITS + "d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA1 is not available", e);
        }
    }

    /**
     * The step {@code code} belongs to, within one step either side of {@code now} (clock drift), and only after
     * {@code afterStep} - so a code accepted once is never accepted again.
     */
    public static OptionalLong verify(byte[] secret, String code, Instant now, Long afterStep) {
        if (code == null || !code.strip().matches("\\d{" + DIGITS + "}")) {
            return OptionalLong.empty();
        }
        String given = code.strip();
        long current = step(now);
        for (long candidate = current - 1; candidate <= current + 1; candidate++) {
            if ((afterStep == null || candidate > afterStep)
                    && java.security.MessageDigest.isEqual(code(secret, candidate).getBytes(), given.getBytes())) {
                return OptionalLong.of(candidate);
            }
        }
        return OptionalLong.empty();
    }

    public static String base32(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return out.toString();
    }

    public static byte[] fromBase32(String text) {
        String clean = text.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        ByteBuffer out = ByteBuffer.allocate(clean.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : clean.toCharArray()) {
            int value = BASE32.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("not base32");
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) ((buffer >> (bits - 8)) & 0xff));
                bits -= 8;
            }
        }
        return java.util.Arrays.copyOf(out.array(), out.position());
    }

}
