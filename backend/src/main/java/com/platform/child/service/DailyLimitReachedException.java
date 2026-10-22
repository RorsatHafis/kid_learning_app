package com.platform.child.service;

import java.util.UUID;

/**
 * M3: today's learning time for this child is used, so a new activity doesn't start (409 with {@code dailyLimitReached}).
 * Never thrown while an activity is open or for an answer.
 */
public class DailyLimitReachedException extends RuntimeException {

    private final UUID childId;
    private final DailyLearningTimeService.Today today;

    public DailyLimitReachedException(UUID childId, DailyLearningTimeService.Today today) {
        super("Today's learning time is used. See you tomorrow!");
        this.childId = childId;
        this.today = today;
    }

    public UUID getChildId() {
        return childId;
    }

    public DailyLearningTimeService.Today getToday() {
        return today;
    }

}
