package com.platform.parent.web;

import com.platform.identity.UiLanguage;
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
    private final com.platform.family.service.ChildAccess childAccess;

    public ParentInsightController(ParentInsightService parentInsightService, FamilyAccessGuard familyAccessGuard, com.platform.family.service.ChildAccess childAccess) {
        this.parentInsightService = parentInsightService;
        this.familyAccessGuard = familyAccessGuard;
        this.childAccess = childAccess;
    }

    /** Recomputes insights from the child's current LearnerObjectiveState/ReviewItem data and persists the new batch. */
    @PostMapping("/api/v1/children/{childId}/parent-insights/generate")
    public ResponseEntity<List<ParentInsightResponse>> generate(@PathVariable UUID childId,
                                                                  @AuthenticationPrincipal UUID accountId) {
        familyAccessGuard.requireChildAccess(accountId, childId);

        List<ParentInsight> insights = parentInsightService.generateInsights(childId);

        return ResponseEntity.ok(insights.stream().map(insight -> ParentInsightResponse.from(insight, UiLanguage.ofCurrentRequest())).toList());
    }

    /**
     * Battle 5C: the child's CURRENT interpretation, derived from current learner
     * state (nothing persisted, so {@code id}/{@code occurredAt} are null). Previously
     * this returned every snapshot ever generated, so a parent saw superseded and
     * contradictory claims - including ones the child's corrected learning state no
     * longer supports - as if they described the child now.
     */
    @GetMapping("/api/v1/children/{childId}/parent-insights")
    public ResponseEntity<List<ParentInsightResponse>> current(@PathVariable UUID childId,
                                                                 @AuthenticationPrincipal UUID accountId) {
        String name = readerName(childAccess.requireViewer(accountId, childId));

        List<ParentInsight> insights = parentInsightService.currentInsights(childId);

        return ResponseEntity.ok(insights.stream().map(insight -> ParentInsightResponse.from(insight, UiLanguage.ofCurrentRequest(), name)).toList());
    }

    /** Every recorded snapshot, most recent first - historical events, not the child's current state. */
    @GetMapping("/api/v1/children/{childId}/parent-insights/history")
    public ResponseEntity<List<ParentInsightResponse>> history(@PathVariable UUID childId,
                                                                 @AuthenticationPrincipal UUID accountId) {
        String name = readerName(childAccess.requireViewer(accountId, childId));

        List<ParentInsight> insights = parentInsightService.listRecent(childId);

        return ResponseEntity.ok(insights.stream().map(insight -> ParentInsightResponse.from(insight, UiLanguage.ofCurrentRequest(), name)).toList());
    }

    /** The child's name for a caregiver, so insights don't call the child theirs; null for the family. */
    private static String readerName(com.platform.family.service.ChildAccess.Access access) {
        return access.family() ? null : access.child().getDisplayName();
    }

}
