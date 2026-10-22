package com.platform.content.web;

import com.platform.common.web.ResourceNotFoundException;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.platform.learning.service.LearnerContentReadGuard;
import com.platform.content.entity.ActivityVersion;
import com.platform.content.service.ActivityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static com.platform.content.web.ActivityContentDtos.ActivityVersionDetailResponse;

/**
 * The missing "load the real questions" step of the golden journey: every other
 * piece of the activity-attempt flow (start/submit/complete/adaptive-next/smart-review)
 * already existed, but nothing served the actual prompt/options a child needs to
 * see to answer a question - the frontend had no choice but to hardcode/mock
 * question content. Read-only; since TD-13 scoped by
 * {@link LearnerContentReadGuard}: a parent reads it for a child who may play it. Never serializes
 * {@code QuestionVersion.correctAnswer} or {@code QuestionOption.correct} - see
 * {@link ActivityContentDtos}.
 */
@RestController
public class ActivityContentController {

    private final ActivityService activityService;
    private final LearnerActivityReader reader;
    private final LearnerContentReadGuard readGuard;

    private final com.platform.content.withdrawal.WithdrawnContent withdrawn;

    public ActivityContentController(ActivityService activityService, LearnerActivityReader reader,
                                      LearnerContentReadGuard readGuard,
                                      com.platform.content.withdrawal.WithdrawnContent withdrawn) {
        this.withdrawn = withdrawn;
        this.readGuard = readGuard;
        this.activityService = activityService;
        this.reader = reader;
    }

    @GetMapping("/api/v1/activity-versions/{activityVersionId}")
    public ResponseEntity<ActivityVersionDetailResponse> getActivityVersion(@PathVariable UUID activityVersionId,
                                                                            @RequestParam(required = false) UUID childId,
                                                                            @AuthenticationPrincipal UUID accountId) {
        // Known Backend Fix C (no draft learner content): getLearnerVisibleVersionById,
        // never the general getVersion - see its javadoc. A caller who knows a draft
        // ActivityVersion id must not be able to retrieve draft learner content here.
        ActivityVersion version = activityService.getLearnerVisibleVersionById(activityVersionId);
        // TD-13: a parent reads it for a child who may play it; staff, what they may see.
        readGuard.requireReadable(accountId, childId, version);
        // Y16: content taken down is no longer served to anyone.
        if (withdrawn.activityVersion(activityVersionId)) {
            throw new ResourceNotFoundException("ActivityVersion", activityVersionId);
        }
        return ResponseEntity.ok(reader.read(version));
    }

}
