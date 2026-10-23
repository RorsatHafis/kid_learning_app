package com.platform.subscription.service;

import java.util.UUID;

/**
 * A school service was asked of a school without a confirmed, paid subscription in force (Y44). Answered as 402 with
 * the school's id, so the workspace can show the school's billing state instead of a generic refusal.
 */
public class SchoolPaymentRequiredException extends RuntimeException {

    private final UUID schoolId;
    /** M6: the school had a paid period that is over (otherwise it has never paid). */
    private final boolean expired;

    public SchoolPaymentRequiredException(UUID schoolId, boolean expired) {

        super("This school's subscription needs to be paid before its school services can be used");
        this.schoolId = schoolId;
        this.expired = expired;

    }

    public boolean isExpired() {

        return expired;

    }

    public UUID getSchoolId() {

        return schoolId;

    }

}
