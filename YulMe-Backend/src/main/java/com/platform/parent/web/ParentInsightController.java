package com.platform.parent.web;

import com.platform.family.service.FamilyAccessGuard;
import com.platform.parent.entity.ParentInsight;
import com.platform.parent.service.ParentInsightService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Section 21 ("Parent Insight... explain data") over HTTP. Same
 * {@link FamilyAccessGuard} pattern as {@link com.platform.learning.web.EnrollmentController} -
 * a parent account can only generate or read insights for children in its own
 * family.
 */
@RestController
public class ParentInsightController {

    private final ParentInsightService parentInsightService;
    private final FamilyAccessGuard familyAccessGuard;

    public ParentInsightController(ParentInsightService parentInsightService, FamilyAccessGuard familyAccessGuard) {
        this.parentInsightService = parentInsightService;
        this.familyAccessGuard = familyAccessGuard;
    }

    /** Recomputes insights from the child's current LearnerObjectiveState/ReviewItem data and persists the new batch. */
    @PostMapping("/api/v1/children/{childId}/parent-insights/generate")
    public ResponseEntity<List<ParentInsightResponse>> generate(@PathVariable UUID childId,
                                                                  @AuthenticationPrincipal UUID accountId) {
        familyAccessGuard.requireChildAccess(accountId, childId);

        List<ParentInsight> insights = parentInsightService.generateInsights(childId);

        return ResponseEntity.ok(insights.stream().map(ParentInsightResponse::from).toList());
    }

    /** Everything generated so far, most recent first - including insights from earlier generate() calls. */
    @GetMapping("/api/v1/children/{childId}/parent-insights")
    public ResponseEntity<List<ParentInsightResponse>> listRecent(@PathVariable UUID childId,
                                                                    @AuthenticationPrincipal UUID accountId) {
        familyAccessGuard.requireChildAccess(accountId, childId);

        List<ParentInsight> insights = parentInsightService.listRecent(childId);

        return ResponseEntity.ok(insights.stream().map(ParentInsightResponse::from).toList());
    }

}
