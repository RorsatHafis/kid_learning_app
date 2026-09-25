package com.platform.common.idempotency.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class RequestFingerprint {

    private RequestFingerprint() {
    }
 
    /** Matches the V2 migration's CHECK constraint: exactly 64 lowercase hex characters. */
    public static String sha256Hex(String canonicalPayload) {
        
        try {
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalPayload.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {

            // SHA-256 is a mandatory JDK algorithm (JLS/JCA baseline) — this can't actually happen.
            throw new IllegalStateException("SHA-256 unavailable", e);

        }

    }
    
}
