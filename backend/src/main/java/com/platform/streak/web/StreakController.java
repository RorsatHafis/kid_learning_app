package com.platform.streak.web;

import com.platform.family.service.ChildAccess;
import com.platform.streak.service.LearnerStreakService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * STREAK-1: a child's learning streak, for whoever may open the child's space (the family, a caregiver who may open
 * it, the child's own device) - the child's space shows it. A caregiver who only views progress is refused (404).
 */
@RestController
@RequestMapping("/api/v1/children/{childId}/streak")
public class StreakController {

    private final LearnerStreakService service;
    private final ChildAccess childAccess;

    public StreakController(LearnerStreakService service, ChildAccess childAccess) {
        this.service = service;
        this.childAccess = childAccess;
    }

    @GetMapping
    public Response get(@PathVariable UUID childId, @AuthenticationPrincipal UUID accountId) {
        childAccess.requireLearner(accountId, childId);
        LearnerStreakService.Standing standing = service.standing(childId);
        return new Response(childId, standing.current(), standing.longest(), standing.lastLearningDay(), standing.learnedToday());
    }

    /** {@code currentStreak}: as of today (0 once a day has been missed); {@code learnedToday}: today already counts. */
    public record Response(UUID childId, int currentStreak, int longestStreak, LocalDate lastActivityDate, boolean learnedToday) {
    }
}
