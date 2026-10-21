package com.platform.content.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TD-26: a stored file is kept only if it really holds the bytes its name promises; a missing, truncated or
 * damaged one is written again and checked, and a file that can't be written is a failure, never a success.
 */
class MediaStorageTests {

    @TempDir Path root;

    @Test
    void anIntactFileIsKeptAsItIs() throws Exception {
        MediaStorage storage = new MediaStorage(new MediaProperties(root.toString()));
        byte[] bytes = "a picture".getBytes(StandardCharsets.UTF_8);
        String sha = sha256(bytes);
        String key = storage.store(sha, bytes);
        Path file = storage.resolve(key);
        FileTime written = FileTime.from(Instant.parse("2020-01-01T00:00:00Z"));
        Files.setLastModifiedTime(file, written);

        assertThat(storage.store(sha, bytes)).isEqualTo(key);
        assertThat(Files.getLastModifiedTime(file)).isEqualTo(written); // not rewritten
        assertThat(storage.intact(key, sha, bytes.length)).isTrue();
    }

    @Test
    void aMissingTruncatedOrDamagedFileIsWrittenAgainAndChecked() throws Exception {
        MediaStorage storage = new MediaStorage(new MediaProperties(root.toString()));
        byte[] bytes = "a recording, quite a bit longer".getBytes(StandardCharsets.UTF_8);
        String sha = sha256(bytes);
        String key = storage.store(sha, bytes);
        Path file = storage.resolve(key);

        Files.delete(file);
        assertThat(storage.intact(key, sha, bytes.length)).isFalse();
        storage.store(sha, bytes);
        assertThat(Files.readAllBytes(file)).isEqualTo(bytes);

        Files.write(file, "a recording".getBytes(StandardCharsets.UTF_8)); // truncated
        assertThat(storage.intact(key, sha, bytes.length)).isFalse();
        storage.store(sha, bytes);
        assertThat(Files.readAllBytes(file)).isEqualTo(bytes);

        byte[] damaged = bytes.clone();
        damaged[3] ^= 0x20; // same size, different content
        Files.write(file, damaged);
        assertThat(storage.intact(key, sha, bytes.length)).isFalse();
        storage.store(sha, bytes);
        assertThat(storage.intact(key, sha, bytes.length)).isTrue();
        try (var leftovers = Files.list(file.getParent())) {
            assertThat(leftovers).containsExactly(file); // no temporary files left beside it
        }
    }

    @Test
    void aFileThatCantBeWrittenIsAFailure() throws Exception {
        MediaStorage storage = new MediaStorage(new MediaProperties(root.toString()));
        byte[] bytes = "unwritable".getBytes(StandardCharsets.UTF_8);
        String sha = sha256(bytes);
        // Where its folder should be there is a file, so nothing can be written under it.
        Path folder = storage.resolve(MediaStorage.keyFor(sha)).getParent();
        Files.createDirectories(folder.getParent());
        Files.writeString(folder, "in the way");

        assertThatThrownBy(() -> storage.store(sha, bytes)).isInstanceOf(UncheckedIOException.class);
        assertThat(storage.intact(MediaStorage.keyFor(sha), sha, bytes.length)).isFalse();
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

}
