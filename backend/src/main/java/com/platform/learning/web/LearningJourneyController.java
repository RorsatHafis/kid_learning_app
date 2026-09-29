package com.platform.learning.web;

import com.platform.family.service.FamilyAccessGuard;
import com.platform.learning.service.EnrollmentService;
import com.platform.learning.service.LearningJourneyService;
import com.platform.learning.service.LearningJourneyService.CompletedActivity;
import com.platform.learning.service.LearningJourneyService.EnrollmentOverview;
import com.platform.learning.service.LearningJourneyService.Journey;
import com.platform.learning.service.SkillProgressService;
import com.platform.learning.service.SkillProgressService.CurriculumProgress;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read views of a child's learning for the child and parent experiences: what the
 * child is enrolled in, their journey through each path (and why each step is there),
 * what the evidence says per skill, and what they have completed. Every endpoint is
 * guarded by {@link FamilyAccessGuard} on the child the resource belongs to, resolved
 * from persisted state - never a child id the client merely claims.
 *
 * <p>These replace the frontend's client-side enrollment pointer: before them nothing
 * could tell a browser which enrollments a child had, so progress "disappeared" on a
 * new device and the enrollment created automatically with the child was never
 * reachable.
 */
@RestController
public class LearningJourneyController {

    private static final int MAX_HISTORY = 100;

    private final LearningJourneyService learningJourneyService;
    private final SkillProgressService skillProgressService;
    private final EnrollmentService enrollmentService;
    private final FamilyAccessGuard familyAccessGuard;

    public LearningJourneyController(LearningJourneyService learningJourneyService,
                                     SkillProgressService skillProgressService, EnrollmentService enrollmentService,
                                     FamilyAccessGuard familyAccessGuard) {
        this.learningJourneyService = learningJourneyService;
        this.skillProgressService = skillProgressService;
        this.enrollmentService = enrollmentService;
        this.familyAccessGuard = familyAccessGuard;
    }

    @GetMapping("/api/v1/children/{childId}/enrollments")
    public List<EnrollmentOverview> enrollments(@PathVariable UUID childId, @AuthenticationPrincipal UUID accountId) {
        familyAccessGuard.requireChildAccess(accountId, childId);
        return learningJourneyService.enrollmentsFor(childId);
    }

    @GetMapping("/api/v1/enrollments/{enrollmentId}/journey")
    public Journey journey(@PathVariable UUID enrollmentId, @AuthenticationPrincipal UUID accountId) {
        familyAccessGuard.requireChildAccess(accountId, enrollmentService.getEnrollment(enrollmentId).getChildId());
        return learningJourneyService.journeyFor(enrollmentId);
    }

    @GetMapping("/api/v1/children/{childId}/skills")
    public List<CurriculumProgress> skills(@PathVariable UUID childId, @AuthenticationPrincipal UUID accountId) {
        familyAccessGuard.requireChildAccess(accountId, childId);
        return skillProgressService.progressFor(childId);
    }

    @GetMapping("/api/v1/children/{childId}/activity-history")
    public List<CompletedActivity> history(@PathVariable UUID childId,
                                           @RequestParam(defaultValue = "20") int limit,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant before,
                                           @RequestParam(required = false) UUID beforeId,
                                           @AuthenticationPrincipal UUID accountId) {
        familyAccessGuard.requireChildAccess(accountId, childId);
        if (limit < 1 || limit > MAX_HISTORY) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_HISTORY);
        }
        return learningJourneyService.completedActivities(childId, limit, before, beforeId);
    }

}
