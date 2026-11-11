package com.platform.content.media.storage;

import com.platform.common.config.ConfigurationContract;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * M11 (D-06, owner decision 2026-11-11: hybrid storage). {@code credentialsKey} (STORAGE_CREDENTIALS_KEY): the AES-256
 * key, base64, that encrypts schools' own-storage credentials at rest - optional: without it a school can't connect its
 * own storage on this installation (Bookeyond-hosted storage works as before). {@code allowPrivateEndpoints}: local
 * and test only (an S3-compatible server on this machine); never in production ({@link StorageSafetyGuard}).
 */
@ConfigurationProperties(prefix = "platform.storage")
public record StorageProperties(String credentialsKey, boolean allowPrivateEndpoints) {

    public StorageProperties {
        if (credentialsKey != null && credentialsKey.isBlank()) {
            credentialsKey = null;
        }
        if (credentialsKey != null) {
            byte[] key;
            try {
                key = Base64.getDecoder().decode(credentialsKey.strip());
            } catch (IllegalArgumentException e) {
                key = new byte[0];
            }
            ConfigurationContract.require(key.length == 32, "STORAGE_CREDENTIALS_KEY must be 32 bytes, base64-encoded");
        }
    }

    public boolean ownStorageAvailable() {
        return credentialsKey != null;
    }
}
