package com.platform.family.erasure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * After a database restore, erases again whatever the ledger says was erased, before the server takes its first
 * request. The sweep does the same, but only a minute after startup and then hourly: the Y45 restore drill showed
 * an erased parent signing in, and an erased child's learning back, for that first minute. This runs once every
 * singleton is ready and before the web server starts listening, so a restored database never serves erased data.
 * One erasure that fails is logged and left to the sweep, as there; a ledger that can't be read stops startup.
 */
@Component
public class ErasureStartupCatchUp implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(ErasureStartupCatchUp.class);

    private final ErasureLedger ledger;
    private final FamilyErasureService erasure;

    public ErasureStartupCatchUp(ErasureLedger ledger, FamilyErasureService erasure) {
        this.ledger = ledger;
        this.erasure = erasure;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!ledger.enabled()) {
            return;
        }
        int erased = erasure.eraseRestored();
        if (erased > 0) {
            log.warn("Erased {} child(ren) or account(s) again at startup: the database holds data the erasure ledger "
                    + "says was erased (restored from an earlier backup)", erased);
        }
    }

}
