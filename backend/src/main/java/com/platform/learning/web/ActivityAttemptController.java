package com.platform.learning.web;

import com.platform.content.entity.ActivitySkill;
import com.platform.content.entity.ActivityVersion;
import com.platform.content.repository.ActivitySkillRepository;
import com.platform.content.service.ActivityService;
import com.platform.family.service.FamilyAccessGuard;
import com.platform.learner.service.AdaptiveEngine;
import com.platform.learning.entity.ActivityAttempt;
import com.platform.learning.entity.ActivityAttemptStatus;
import com.platform.learning.entity.AnswerRecord;
import com.platform.learning.entity.LearningPathItem;
import com.platform.learning.entity.LearningPathItemSource;
import com.platform.learning.service.ActivityAttemptService;
import com.platform.learning.service.LearningPathService;
import com.platform.review.entity.ReviewItem;
import com.platform.review.service.SmartReviewService;
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
import java.util.Optional;
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
 * all. "determine next activity" is {@link #getAdaptiveNext}; Smart Review is
 * {@link #getSmartReviewCheck} - see their javadocs.
 *
 * Every attempt-scoped endpoint loads the attempt first and guards on its
 * {@code childId}, rather than trusting a childId supplied by the client -
 * consistent with {@link EnrollmentController}.
 */
@RestController
public class ActivityAttemptController {

    private final ActivityAttemptService activityAttemptService;
    private final ActivityService activityService;
    private final ActivitySkillRepository activitySkillRepository;
    private final LearningPathService learningPathService;
    private final AdaptiveEngine adaptiveEngine;
    private final SmartReviewService smartReviewService;
    private final FamilyAccessGuard familyAccessGuard;

    public ActivityAttemptController(ActivityAttemptService activityAttemptService, ActivityService activityService,
                                      ActivitySkillRepository activitySkillRepository,
                                      LearningPathService learningPathService, AdaptiveEngine adaptiveEngine,
                                      SmartReviewService smartReviewService, FamilyAccessGuard familyAccessGuard) {
        this.activityAttemptService = activityAttemptService;
        this.activityService = activityService;
        this.activitySkillRepository = activitySkillRepository;
        this.learningPathService = learningPathService;
        this.adaptiveEngine = adaptiveEngine;
        this.smartReviewService = smartReviewService;
        this.familyAccessGuard = familyAccessGuard;
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
                .map(AnswerRecordResponse::from)
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

        return ResponseEntity.status(HttpStatus.CREATED).body(AnswerRecordResponse.from(record));
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
     * The Adaptive Engine entry point: derives the (first) skill the just-completed
     * activity addresses, hands it to {@link AdaptiveEngine#recommendNext}, and - if
     * a recommendation was made - the new ADAPTIVE LearningPathItem is already
     * appended to the path by the time this returns; {@code insertedLearningPathItemId}
     * in the response is what the frontend should present as "what's next."
     * Returns 204 (no body) when there's nothing to recommend yet: the attempt isn't
     * COMPLETED, its activity is untagged with any skill, it isn't on a
     * LearningPath at all (free practice), or there's no published activity
     * reachable for the chosen target skill - all legitimate non-error outcomes, not
     * failures.
     */
    @PostMapping("/api/v1/activity-attempts/{attemptId}/adaptive-next")
    public ResponseEntity<AdaptiveRecommendationResponse> getAdaptiveNext(@PathVariable UUID attemptId,
                                                                            @AuthenticationPrincipal UUID accountId) {
        ActivityAttempt attempt = activityAttemptService.getAttempt(attemptId);
        familyAccessGuard.requireChildAccess(accountId, attempt.getChildId());

        Optional<SkillContext> context = deriveSkillContext(attempt);
        if (context.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        Optional<AdaptiveEngine.Recommendation> recommendation = adaptiveEngine.recommendNext(
                attempt.getChildId(), context.get().skillId(), context.get().currentItem().getLearningPathId(),
                attempt.getActivityVersionId());

        return recommendation
                .map(rec -> ResponseEntity.ok(new AdaptiveRecommendationResponse(
                        rec.direction().name(), rec.targetSkillId(), rec.insertedItem().getId(),
                        rec.insertedItem().getActivityVersionId())))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * The Smart Review entry point, run alongside (not instead of) the Adaptive
     * Engine on the same just-completed attempt. Three things happen, in order:
     * <ol>
     *   <li>if the just-completed attempt's LearningPathItem was itself
     *       {@code source == REVIEW}, the outcome is recorded against that skill's
     *       ReviewItem and its schedule is rescheduled (SM-2-style) -
     *       {@code reviewOutcomeRecorded} in the response;</li>
     *   <li>the skill is re-evaluated against current mastery - flagged for review,
     *       unflagged, or left alone - {@code flaggedForReview} in the response;</li>
     *   <li>if anything for this child is now due (this skill or any other
     *       previously-flagged one), one REVIEW LearningPathItem is appended to the
     *       path - {@code insertedLearningPathItemId} in the response, or absent if
     *       nothing is due yet.</li>
     * </ol>
     * "Successful" for step 1 is majority-correct across the attempt's own answers
     * (at least half correct) - a documented, simple choice, not the same number as
     * the skill's overall mastery probability, which reflects the whole history,
     * not just this one attempt.
     */
    @PostMapping("/api/v1/activity-attempts/{attemptId}/smart-review-check")
    public ResponseEntity<SmartReviewCheckResponse> getSmartReviewCheck(@PathVariable UUID attemptId,
                                                                          @AuthenticationPrincipal UUID accountId) {
        ActivityAttempt attempt = activityAttemptService.getAttempt(attemptId);
        familyAccessGuard.requireChildAccess(accountId, attempt.getChildId());

        Optional<SkillContext> context = deriveSkillContext(attempt);
        if (context.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        UUID childId = attempt.getChildId();
        UUID skillId = context.get().skillId();
        UUID learningPathId = context.get().currentItem().getLearningPathId();

        boolean outcomeRecorded = false;
        if (context.get().currentItem().getSource() == LearningPathItemSource.REVIEW) {
            List<AnswerRecord> answers = activityAttemptService.listAnswers(attemptId);
            boolean successful = !answers.isEmpty()
                    && answers.stream().filter(AnswerRecord::isCorrect).count() * 2 >= answers.size();
            smartReviewService.recordOutcomeForSkill(childId, skillId, attemptId, successful);
            outcomeRecorded = true;
        }

        Optional<ReviewItem> flagged = smartReviewService.evaluateAndSchedule(childId, skillId);
        Optional<LearningPathItem> inserted = smartReviewService.insertDueReviewOnPath(childId, learningPathId);

        return ResponseEntity.ok(new SmartReviewCheckResponse(
                outcomeRecorded, flagged.isPresent(), skillId,
                inserted.map(LearningPathItem::getId).orElse(null),
                inserted.map(LearningPathItem::getActivityVersionId).orElse(null)));
    }

    /** Shared by {@link #getAdaptiveNext} and {@link #getSmartReviewCheck}: both need "which skill, on which path" from a completed attempt. */
    private Optional<SkillContext> deriveSkillContext(ActivityAttempt attempt) {
        if (attempt.getStatus() != ActivityAttemptStatus.COMPLETED || attempt.getLearningPathItemId() == null) {
            return Optional.empty();
        }

        ActivityVersion activityVersion = activityService.getVersion(attempt.getActivityVersionId());
        List<ActivitySkill> taggedSkills = activitySkillRepository.findByActivityId(activityVersion.getActivityId());
        if (taggedSkills.isEmpty()) {
            return Optional.empty();
        }

        LearningPathItem currentItem = learningPathService.getItem(attempt.getLearningPathItemId());
        return Optional.of(new SkillContext(taggedSkills.get(0).getSkillId(), currentItem));
    }

    private record SkillContext(UUID skillId, LearningPathItem currentItem) {
    }

}
