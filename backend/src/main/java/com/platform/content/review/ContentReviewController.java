package com.platform.content.review;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.common.web.PageResponse;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.content.service.ContentAccessGuard;
import com.platform.content.studio.StudioService;
import com.platform.content.validation.ContentValidationException;
import com.platform.content.validation.ValidationReport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Reviews before publishing (Y14, see {@link ContentReviewService}). An author asks for a review
 * of a draft that passes its checks; another administrator approves it - which publishes it - or
 * asks for changes. Every step is audited.
 */
@RestController
@RequestMapping("/api/v1/studio")
public class ContentReviewController {

    private final ContentReviewService reviews;
    private final StudioService studio;
    private final ContentAccessGuard guard;
    private final AuditService audit;

    public ContentReviewController(ContentReviewService reviews, StudioService studio, ContentAccessGuard guard,
                                   AuditService audit) {
        this.reviews = reviews;
        this.studio = studio;
        this.guard = guard;
        this.audit = audit;
    }

    public record Settings(boolean required) {
    }

    public record ChangesRequest(@Size(max = 1000) String note) {
    }

    /** Whether review is switched on (the Studio shows the review queue only then). */
    @GetMapping("/review-settings")
    public Settings settings(@AuthenticationPrincipal UUID accountId) {
        guard.requireAuthor(accountId);
        return new Settings(reviews.reviewSwitchedOn());
    }

    @GetMapping("/{kind:question-versions|activity-versions|curriculum-versions}/{versionId}/review")
    public ContentReviewService.ReviewState state(@AuthenticationPrincipal UUID accountId, @PathVariable String kind,
                                                  @PathVariable UUID versionId) {
        checks(accountId, kind(kind), versionId);
        return reviews.state(accountId, kind(kind), versionId);
    }

    /** Asks for a review: the draft must pass its checks first (422 like a refused publish). */
    @PostMapping("/{kind:question-versions|activity-versions|curriculum-versions}/{versionId}/review")
    public ContentReviewService.ReviewState request(@AuthenticationPrincipal UUID accountId, @PathVariable String kind,
                                                    @PathVariable UUID versionId) {
        ContentReview.Kind k = kind(kind);
        ValidationReport report = checks(accountId, k, versionId);
        if (!report.errors().isEmpty()) {
            throw new ContentValidationException("This draft", report);
        }
        ContentReviewService.ReviewState state = reviews.request(accountId, k, versionId);
        record(accountId, "CONTENT_REVIEW_REQUESTED", state.review().id(), k, versionId, null);
        return state;
    }

    @DeleteMapping("/{kind:question-versions|activity-versions|curriculum-versions}/{versionId}/review")
    public ContentReviewService.ReviewState withdraw(@AuthenticationPrincipal UUID accountId, @PathVariable String kind,
                                                     @PathVariable UUID versionId) {
        checks(accountId, kind(kind), versionId);
        return reviews.withdraw(accountId, kind(kind), versionId);
    }

    /** Open reviews, oldest first. Administrators only. */
    @GetMapping("/reviews")
    public PageResponse<ContentReviewService.QueueRow> queue(@AuthenticationPrincipal UUID accountId,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "25") int size) {
        guard.requireCatalogAdmin(accountId);
        return reviews.openQueue(new PageResponse.Request(page, size));
    }

    /** Approves and publishes, as the reviewer, in one transaction: a publish refused by its checks approves nothing. */
    @PostMapping("/reviews/{reviewId}/approve")
    @Transactional
    public ContentReviewService.ReviewState approve(@AuthenticationPrincipal UUID accountId, @PathVariable UUID reviewId) {
        guard.requireCatalogAdmin(accountId);
        ContentReview review = reviews.get(reviewId);
        ContentReviewService.Approval approval = reviews.approve(accountId, reviewId);
        switch (review.getKind()) {
            case QUESTION -> studio.publishQuestionVersion(accountId, review.getVersionId(), approval);
            case ACTIVITY -> studio.publishActivityVersion(accountId, review.getVersionId(), approval);
            case CURRICULUM -> studio.publishCurriculumVersion(accountId, review.getVersionId(), approval);
        }
        record(accountId, "CONTENT_REVIEW_APPROVED", reviewId, review.getKind(), review.getVersionId(), null);
        return reviews.state(accountId, review.getKind(), review.getVersionId());
    }

    @PostMapping("/reviews/{reviewId}/request-changes")
    public ContentReviewService.ReviewState requestChanges(@AuthenticationPrincipal UUID accountId, @PathVariable UUID reviewId,
                                                           @Valid @RequestBody ChangesRequest body) {
        guard.requireCatalogAdmin(accountId);
        ContentReview review = reviews.requestChanges(accountId, reviewId, body.note());
        record(accountId, "CONTENT_REVIEW_CHANGES_REQUESTED", reviewId, review.getKind(), review.getVersionId(), body.note());
        return reviews.state(accountId, review.getKind(), review.getVersionId());
    }

    // ------------------------------------------------------------------

    /** The caller's write access to the content, and its checks (the same as publishing's). */
    private ValidationReport checks(UUID accountId, ContentReview.Kind kind, UUID versionId) {
        return switch (kind) {
            case QUESTION -> studio.validateQuestionVersion(accountId, versionId);
            case ACTIVITY -> studio.validateActivityVersion(accountId, versionId);
            case CURRICULUM -> studio.validateCurriculumVersion(accountId, versionId);
        };
    }

    private static ContentReview.Kind kind(String path) {
        return switch (path) {
            case "question-versions" -> ContentReview.Kind.QUESTION;
            case "activity-versions" -> ContentReview.Kind.ACTIVITY;
            case "curriculum-versions" -> ContentReview.Kind.CURRICULUM;
            default -> throw new ResourceNotFoundException("Content", path);
        };
    }

    private void record(UUID actorId, String action, UUID reviewId, ContentReview.Kind kind, UUID versionId, String note) {
        Map<String, Object> metadata = note == null || note.isBlank()
                ? Map.of("kind", kind.name(), "versionId", versionId.toString())
                : Map.of("kind", kind.name(), "versionId", versionId.toString(), "note", note.strip());
        audit.record(new RecordAuditEvent(null, actorId, AuditActorType.ADMIN, action, "CONTENT_REVIEW", reviewId, metadata));
    }

}
