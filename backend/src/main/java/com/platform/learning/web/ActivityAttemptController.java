package com.platform.learning.web;

import com.platform.family.service.FamilyAccessGuard;
import com.platform.learning.entity.ActivityAttempt;
import com.platform.learning.entity.AnswerRecord;
import com.platform.learning.service.ActivityAttemptService;
import com.platform.learning.service.AnswerFeedbackService;
import com.platform.learning.service.PostCompletionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static com.platform.learning.web.ActivityAttemptDtos.AdaptiveRecommendationResponse;
import static com.platform.learning.web.ActivityAttemptDtos.AnswerRecordResponse;
import static com.platform.learning.web.ActivityAttemptDtos.AttemptResponse;
import static com.platform.learning.web.ActivityAttemptDtos.SmartReviewCheckResponse;
import static com.platform.learning.web.ActivityAttemptDtos.StartAttemptRequest;
import static com.platform.learning.web.ActivityAttemptDtos.SubmitAnswerRequest;

/**
 * Implements Section: "Start learning activity -&gt; create attempt -&gt; submit answer
 * -&gt; score answer -&gt; persist evidence -&gt; update mastery -&gt; determine next activity"
 * for the transport layer. The scoring/evidence/mastery part of that chain already
 * happened (ActivityAttemptService -&gt; AnswerSubmissionWriter -&gt; MasteryEngine, fixed
 * earlier this session); this controller is what makes it reachable over HTTP at
 * all. "Determine next activity" (Adaptive Engine, Smart Review) happens inside
 * completion since Battle 5D - see {@link PostCompletionService}; {@link #getAdaptiveNext}
 * and {@link #getSmartReviewCheck} only read what it decided.
 *
 * Every attempt-scoped endpoint loads the attempt first and guards on its
 * {@code childId}, rather than trusting a childId supplied by the client -
 * consistent with {@link EnrollmentController}.
 */
@RestController
public class ActivityAttemptController {

    private final ActivityAttemptService activityAttemptService;
    private final PostCompletionService postCompletionService;
    private final FamilyAccessGuard familyAccessGuard;
    private final AnswerFeedbackService answerFeedbackService;

    public ActivityAttemptController(ActivityAttemptService activityAttemptService,
                                      PostCompletionService postCompletionService, FamilyAccessGuard familyAccessGuard,
                                      AnswerFeedbackService answerFeedbackService) {
        this.activityAttemptService = activityAttemptService;
        this.postCompletionService = postCompletionService;
        this.familyAccessGuard = familyAccessGuard;
        this.answerFeedbackService = answerFeedbackService;
    }

    private AnswerRecordResponse toResponse(AnswerRecord record) {
        return AnswerRecordResponse.from(record, answerFeedbackService.correctAnswerFor(record));
    }

    @PostMapping("/api/v1/children/{childId}/activity-attempts")
    public ResponseEntity<AttemptResponse> startAttempt(@PathVariable UUID childId,
                                                          @Valid @RequestBody StartAttemptRequest request,
                                                          @AuthenticationPrincipal UUID accountId) {
        familyAccessGuard.requireChildAccess(accountId, childId);

        ActivityAttempt attempt = activityAttemptService.startAttempt(
                childId, request.activityVersionId(), request.learningPathItemId(), request.learningSessionId());

        return ResponseEntity.status(HttpStatus.CREATED).body(AttemptResponse.from(attempt));
    }

    @GetMapping("/api/v1/activity-attempts/{attemptId}")
    public ResponseEntity<AttemptResponse> getAttempt(@PathVariable UUID attemptId,
                                                        @AuthenticationPrincipal UUID accountId) {
        ActivityAttempt attempt = activityAttemptService.getAttempt(attemptId);
        familyAccessGuard.requireChildAccess(accountId, attempt.getChildId());

        return ResponseEntity.ok(AttemptResponse.from(attempt));
    }

    @GetMapping("/api/v1/activity-attempts/{attemptId}/answers")
    public ResponseEntity<List<AnswerRecordResponse>> listAnswers(@PathVariable UUID attemptId,
                                                                    @AuthenticationPrincipal UUID accountId) {
        ActivityAttempt attempt = activityAttemptService.getAttempt(attemptId);
        familyAccessGuard.requireChildAccess(accountId, attempt.getChildId());

        List<AnswerRecordResponse> answers = activityAttemptService.listAnswers(attemptId).stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(answers);
    }

    @PostMapping("/api/v1/activity-attempts/{attemptId}/answers")
    public ResponseEntity<AnswerRecordResponse> submitAnswer(@PathVariable UUID attemptId,
                                                               @Valid @RequestBody SubmitAnswerRequest request,
                                                               @AuthenticationPrincipal UUID accountId) {
        ActivityAttempt attempt = activityAttemptService.getAttempt(attemptId);
        familyAccessGuard.requireChildAccess(accountId, attempt.getChildId());

        AnswerRecord record = activityAttemptService.submitAnswer(attemptId, request.questionVersionId(),
                request.submittedAnswer(), request.timeSpentSeconds(), request.idempotencyKey());

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(record));
    }

    /**
     * Known Backend Fix #1: takes no score from the request body. Section 15 of
     * the mission is explicit that a client must never be able to submit
     * {@code score = 100} and have the server trust it - so there is nothing here
     * to read from the client at all; {@link ActivityAttemptService#completeAttempt}
     * derives the real score from this attempt's own persisted answers.
     */
    @PostMapping("/api/v1/activity-attempts/{attemptId}/complete")
    public ResponseEntity<AttemptResponse> completeAttempt(@PathVariable UUID attemptId,
                                                             @AuthenticationPrincipal UUID accountId) {
        ActivityAttempt attempt = activityAttemptService.getAttempt(attemptId);
        familyAccessGuard.requireChildAccess(accountId, attempt.getChildId());

        ActivityAttempt completed = activityAttemptService.completeAttempt(attemptId);

        return ResponseEntity.ok(AttemptResponse.from(completed));
    }

    @PostMapping("/api/v1/activity-attempts/{attemptId}/abandon")
    public ResponseEntity<AttemptResponse> abandonAttempt(@PathVariable UUID attemptId,
                                                            @AuthenticationPrincipal UUID accountId) {
        ActivityAttempt attempt = activityAttemptService.getAttempt(attemptId);
        familyAccessGuard.requireChildAccess(accountId, attempt.getChildId());

        ActivityAttempt abandoned = activityAttemptService.abandonAttempt(attemptId);

        return ResponseEntity.ok(AttemptResponse.from(abandoned));
    }

    /**
     * Battle 5D: a compatibility READ. Completing the attempt already made and recorded
     * the adaptive decision (PostCompletionService); this returns what that decision
     * placed on the path and never writes, so neither calling it nor skipping it
     * changes the child's learning state. 204 when nothing was placed: the decision was
     * CONTINUE, no activity was available, the attempt isn't completed, or it was free
     * practice.
     */
    @PostMapping("/api/v1/activity-attempts/{attemptId}/adaptive-next")
    public ResponseEntity<AdaptiveRecommendationResponse> getAdaptiveNext(@PathVariable UUID attemptId,
                                                                            @AuthenticationPrincipal UUID accountId) {
        ActivityAttempt attempt = activityAttemptService.getAttempt(attemptId);
        familyAccessGuard.requireChildAccess(accountId, attempt.getChildId());

        return postCompletionService.findPlacedAdaptiveStep(attemptId)
                .map(step -> ResponseEntity.ok(new AdaptiveRecommendationResponse(
                        step.direction(), step.targetSkillId(), step.learningPathItemId(), step.activityVersionId())))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Battle 5D: a compatibility READ. Completing the attempt already recorded any
     * review outcome, re-evaluated Smart Review for every skill the activity exercises,
     * and placed a due review. This reports the persisted result - the review outcome,
     * whether the governing skill is flagged, and the review waiting on the path -
     * without writing. 204 for an attempt that isn't completed or was free practice.
     */
    @PostMapping("/api/v1/activity-attempts/{attemptId}/smart-review-check")
    public ResponseEntity<SmartReviewCheckResponse> getSmartReviewCheck(@PathVariable UUID attemptId,
                                                                          @AuthenticationPrincipal UUID accountId) {
        ActivityAttempt attempt = activityAttemptService.getAttempt(attemptId);
        familyAccessGuard.requireChildAccess(accountId, attempt.getChildId());

        return postCompletionService.findReviewState(attempt)
                .map(state -> ResponseEntity.ok(new SmartReviewCheckResponse(
                        state.reviewOutcomeRecorded(), state.flaggedForReview(), state.skillId(),
                        state.pendingReviewLearningPathItemId(), state.pendingReviewActivityVersionId())))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

}
