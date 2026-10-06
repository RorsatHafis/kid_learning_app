package com.platform.family.erasure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Carries out due deletions every {@code platform.erasure.sweep-interval}. Safe with several
 * instances: each deletion locks its row and skips one another instance holds.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "platform.erasure", name = "sweep-enabled", havingValue = "true")
public class ErasureSweep {

    private static final Logger log = LoggerFactory.getLogger(ErasureSweep.class);

    private final FamilyErasureService erasure;

    public ErasureSweep(FamilyErasureService erasure) {
        this.erasure = erasure;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "${platform.erasure.sweep-interval}")
    public void sweep() {
        int done = erasure.eraseDue();
        if (done > 0) {
            log.info("Erased {} scheduled deletion(s)", done);
        }
    }

}
