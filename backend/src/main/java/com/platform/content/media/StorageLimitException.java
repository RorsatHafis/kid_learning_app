package com.platform.content.media;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** An upload that would take its owner past their storage allowance (409, with the numbers). Nothing was stored. */
public class StorageLimitException extends RuntimeException {

    private final long usedBytes;
    private final long limitBytes;
    private final long neededBytes;

    public StorageLimitException(long usedBytes, long limitBytes, long neededBytes) {
        super("Your storage is full: " + mb(usedBytes) + " MB of " + mb(limitBytes) + " MB is used, and this file needs "
                + mb(neededBytes) + " MB. Remove media you no longer use, or ask for more space.");
        this.usedBytes = usedBytes;
        this.limitBytes = limitBytes;
        this.neededBytes = neededBytes;
    }

    /** MB with one decimal; a small file that would round to 0 shows two, never "0" (at least 0.01). */
    static String mb(long bytes) {
        BigDecimal mb = BigDecimal.valueOf(bytes).divide(BigDecimal.valueOf(1024L * 1024), 1, RoundingMode.HALF_UP);
        if (bytes > 0 && mb.signum() == 0) {
            mb = BigDecimal.valueOf(bytes).divide(BigDecimal.valueOf(1024L * 1024), 2, RoundingMode.UP);
        }
        return mb.stripTrailingZeros().toPlainString();
    }

    public long getUsedBytes() {
        return usedBytes;
    }

    public long getLimitBytes() {
        return limitBytes;
    }

    public long getNeededBytes() {
        return neededBytes;
    }
}
