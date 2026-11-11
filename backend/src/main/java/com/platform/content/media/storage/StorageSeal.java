package com.platform.content.media.storage;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * M11: a school's own-storage access key, sealed at rest with AES-256-GCM (a random 96-bit nonce per value; the
 * connection's school, address, bucket and key id as associated data, so a sealed value can't be moved to another). The key comes from the
 * environment ({@link StorageProperties#credentialsKey()}); without it nothing can be sealed and own storage isn't
 * offered. Never logged or returned.
 */
@Component
public class StorageSeal {

    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public StorageSeal(StorageProperties properties) {
        this.key = properties.ownStorageAvailable()
                ? new SecretKeySpec(Base64.getDecoder().decode(properties.credentialsKey().strip()), "AES") : null;
    }

    public boolean available() {
        return key != null;
    }

    public String seal(String value, String associatedData) {
        requireKey();
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            byte[] sealed = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + sealed.length).put(nonce).put(sealed).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not seal a storage key", e);
        }
    }

    /** The value, or a refusal if it was altered, moved to another connection or sealed with another key. */
    public String open(String sealed, String associatedData) {
        requireKey();
        try {
            byte[] all = Base64.getDecoder().decode(sealed);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, NONCE_BYTES));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(all, NONCE_BYTES, all.length - NONCE_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("A storage key could not be read: it was changed or sealed with another key");
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new IllegalStateException("Connecting a school's own storage isn't set up on this installation.");
        }
    }
}
