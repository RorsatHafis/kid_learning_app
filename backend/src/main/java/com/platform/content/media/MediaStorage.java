package com.platform.content.media;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

/**
 * Uploaded files on disk, named by the SHA-256 of their bytes ("ab/cd/abcd…"): the same bytes
 * are stored once, a stored file never changes, and a name can't point outside the root.
 * Checked at startup: an unwritable root stops the application.
 */
@Component
public class MediaStorage {

    private static final Pattern KEY = Pattern.compile("[0-9a-f]{2}/[0-9a-f]{2}/[0-9a-f]{64}");

    private final Path root;

    public MediaStorage(MediaProperties properties) {
        this.root = properties.path().toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            Path probe = Files.createTempFile(root, ".probe", ".tmp");
            Files.delete(probe);
        } catch (IOException e) {
            throw new IllegalStateException("MEDIA_ROOT " + root + " is not a writable directory", e);
        }
    }

    public static String keyFor(String sha256) {
        return sha256.substring(0, 2) + "/" + sha256.substring(2, 4) + "/" + sha256;
    }

    /** Writes the bytes under their key unless they are already there; returns the key. */
    public String store(String sha256, byte[] bytes) {
        String key = keyFor(sha256);
        Path target = resolve(key);
        if (Files.exists(target)) {
            return key;
        }
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), ".upload", ".tmp");
            try {
                Files.write(temp, bytes);
                try {
                    Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            // Two uploads of the same bytes may race to the same name: the content is identical either way.
            if (!Files.exists(target)) {
                throw new UncheckedIOException("Could not store media " + key, e);
            }
        }
        return key;
    }

    public Path resolve(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Not a media key");
        }
        return root.resolve(key);
    }

}
