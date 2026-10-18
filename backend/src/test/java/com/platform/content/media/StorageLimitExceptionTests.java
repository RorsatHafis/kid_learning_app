package com.platform.content.media;

import com.platform.common.web.ServerText;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Y35: the refusal says how full the storage is in numbers a person reads, in both languages. */
class StorageLimitExceptionTests {

    private static final long MB = 1024L * 1024L;

    @Test
    void sizesAreMegabytesWithOneDecimalAndASmallFileIsNeverZero() {
        assertThat(StorageLimitException.mb(0)).isEqualTo("0");
        assertThat(StorageLimitException.mb(5_000)).isEqualTo("0.01");
        assertThat(StorageLimitException.mb(110_627)).isEqualTo("0.1");
        assertThat(StorageLimitException.mb(MB + MB / 2)).isEqualTo("1.5");
        assertThat(StorageLimitException.mb(1024 * MB)).isEqualTo("1024");
    }

    @Test
    void theMessageCarriesTheNumbersAndHasKhmer() {
        StorageLimitException refused = new StorageLimitException(900 * MB, 1024 * MB, 5_000);
        assertThat(refused.getMessage())
                .isEqualTo("Your storage is full: 900 MB of 1024 MB is used, and this file needs 0.01 MB. "
                        + "Remove media you no longer use, or ask for more space.");
        String khmer = ServerText.translate(refused.getMessage(), "km", 409);
        assertThat(khmer).isNotEqualTo(refused.getMessage()).contains("900 MB", "1024 MB", "0.01 MB");
    }
}
