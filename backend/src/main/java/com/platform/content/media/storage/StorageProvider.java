package com.platform.content.media.storage;

/**
 * M11: where media bytes are kept - Bookeyond-hosted disk ({@code MediaStorage}) or a school's own S3-compatible
 * bucket ({@link S3StorageProvider}). Files are named by the SHA-256 of their bytes ({@code MediaStorage.keyFor}), never
 * change once stored, and every write is read back and checked before it counts.
 */
public interface StorageProvider {

    /** Stores the bytes under {@code key} and checks them; a failure throws, never a false success. */
    void put(String key, String sha256, byte[] bytes);

    /** Whether {@code key} holds exactly these bytes (its size and SHA-256). */
    boolean intact(String key, String sha256, long size);

    /** The stored bytes (media is small: at most a few MB). */
    byte[] read(String key);

    void delete(String key);
}
