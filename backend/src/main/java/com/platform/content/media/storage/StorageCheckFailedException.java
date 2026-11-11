package com.platform.content.media.storage;

/**
 * M11: a school's own storage didn't pass its check (write, read back, delete a marker). Nothing was kept; the message
 * says what failed in words a principal can act on (422).
 */
public class StorageCheckFailedException extends RuntimeException {

    public StorageCheckFailedException(String message) {
        super(message);
    }
}
