package com.platform.family.erasure;

import com.platform.common.config.ConfigurationContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What has been erased, kept outside the database: one line per erasure - when, what kind, the
 * id - and nothing else (no names or addresses). Restoring the database from a backup taken before
 * an erasure brings the erased rows back; the sweep reads this file and erases again anything
 * listed here that has reappeared. Off when {@code ERASURE_LEDGER_FILE} is not set (required in
 * production). The file must live on storage that is not restored together with the database.
 */
@Component
public class ErasureLedger {

    public enum Kind { CHILD, ACCOUNT }

    public record Entry(Kind kind, UUID id) {
    }

    private static final Logger log = LoggerFactory.getLogger(ErasureLedger.class);

    private final Path file;

    public ErasureLedger(ErasureProperties properties) {
        String configured = properties.ledgerFile();
        this.file = configured == null || configured.isBlank() ? null : Path.of(configured.trim());
        if (file != null) {
            try {
                Path parent = file.toAbsolutePath().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                if (!Files.exists(file)) {
                    Files.createFile(file);
                }
            } catch (IOException e) {
                throw new IllegalStateException("ERASURE_LEDGER_FILE " + file + " can't be created: " + e.getMessage(), e);
            }
            ConfigurationContract.require(Files.isWritable(file), "ERASURE_LEDGER_FILE " + file + " is not writable");
        }
    }

    public boolean enabled() {
        return file != null;
    }

    /** Appends one erasure, flushed to disk before returning. */
    public synchronized void record(Kind kind, UUID id) {
        if (file == null) {
            return;
        }
        try {
            Files.writeString(file, Instant.now() + " " + kind + " " + id + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.SYNC);
        } catch (IOException e) {
            throw new UncheckedIOException("The erasure of " + kind + " " + id + " could not be written to the ledger", e);
        }
    }

    /** Every erasure recorded so far; a line that can't be read is skipped and reported. */
    public List<Entry> entries() {
        if (file == null) {
            return List.of();
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("The erasure ledger " + file + " can't be read", e);
        }
        List<Entry> entries = new ArrayList<>(lines.size());
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.trim().split(" ");
            try {
                entries.add(new Entry(Kind.valueOf(parts[1]), UUID.fromString(parts[2])));
            } catch (RuntimeException e) {
                log.error("Unreadable line in the erasure ledger {}: '{}'", file, line);
            }
        }
        return entries;
    }

}
