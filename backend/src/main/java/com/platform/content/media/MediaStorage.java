package com.platform.content.media;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Uploaded files on disk, named by the SHA-256 of their bytes ("ab/cd/abcd…"): the same bytes
 * are stored once, a stored file never changes, and a name can't point outside the root.
 * Checked at startup: an unwritable root stops the application. Callers that store or delete
 * a file hold the content's lock ({@code ContentAssetRepository.lockContent}).
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

    /**
     * Makes sure the bytes are stored under their key, and returns the key. A file already there is kept only
     * if it really holds these bytes (its size and SHA-256); a missing, truncated or damaged one is written
     * again (TD-26). The file is written beside its name and moved into place, so a reader never sees half of
     * it, and it is read back and checked before this returns: a failure throws, never a false success.
     */
    public String store(String sha256, byte[] bytes) {
        String key = keyFor(sha256);
        Path target = resolve(key);
        if (intact(target, sha256, bytes.length)) {
            return key;
        }
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), ".upload", ".tmp");
            try {
                Files.write(temp, bytes);
                try {
                    Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            // Two uploads of the same bytes may race to the same name: what matters is what is there now (below).
            if (!intact(target, sha256, bytes.length)) {
                throw new UncheckedIOException("Could not store media " + key, e);
            }
        }
        if (!intact(target, sha256, bytes.length)) {
            throw new UncheckedIOException(new IOException("Stored media " + key + " did not read back as written"));
        }
        return key;
    }

    /** Whether the file under this key holds exactly the bytes its name promises. */
    public boolean intact(String key, String sha256, long size) {
        return intact(resolve(key), sha256, size);
    }

    private static boolean intact(Path file, String sha256, long size) {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) != size) {
                return false;
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new DigestInputStream(Files.newInputStream(file), digest)) {
                in.transferTo(OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest()).equals(sha256);
        } catch (IOException e) {
            return false; // unreadable counts as damaged
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Removes a stored file no asset refers to any more (X13: deleting unused media). */
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete media " + key, e);
        }
    }

    /** Free space where media is kept (System status, X15). */
    public long usableSpace() {
        return root.toFile().getUsableSpace();
    }

    public Path resolve(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Not a media key");
        }
        return root.resolve(key);
    }

}
