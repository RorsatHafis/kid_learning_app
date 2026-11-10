package com.platform.learning.drawing;

import com.fasterxml.jackson.annotation.JsonRawValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Owner-approved 2026-11-10 (V91): reviewing a child's drawings - by the family, or by the class that assigned them. */
@RestController
public class DrawingReviewController {

    private final DrawingReviewService drawings;

    public DrawingReviewController(DrawingReviewService drawings) {
        this.drawings = drawings;
    }

    /** A drawing as a reviewer sees it; {@code guide} and {@code strokes} are the drawing's own JSON. */
    public record DrawingResponse(UUID answerId, UUID childId, String childName, String activityTitle, String prompt,
                                  @JsonRawValue String guide, @JsonRawValue String strokes, Instant drawnAt,
                                  List<DrawingReviewService.Feedback> feedback) {
        static DrawingResponse of(DrawingReviewService.Drawing d) {
            return new DrawingResponse(d.answerId(), d.childId(), d.childName(), d.activityTitle(), d.prompt(), d.guide(), d.strokes(),
                    d.drawnAt(), d.feedback());
        }
    }

    public record FeedbackRequest(@NotBlank @Size(max = 500) String body) {
    }

    @GetMapping("/api/v1/children/{childId}/drawings")
    public List<DrawingResponse> forChild(@AuthenticationPrincipal UUID accountId, @PathVariable UUID childId) {
        return drawings.forChild(accountId, childId).stream().map(DrawingResponse::of).toList();
    }

    @PostMapping("/api/v1/children/{childId}/drawings/{answerId}/feedback")
    public DrawingResponse familyFeedback(@AuthenticationPrincipal UUID accountId, @PathVariable UUID childId,
                                          @PathVariable UUID answerId, @Valid @RequestBody FeedbackRequest request) {
        return DrawingResponse.of(drawings.familyFeedback(accountId, childId, answerId, request.body()));
    }

    @GetMapping("/api/v1/schools/classes/{classId}/drawings")
    public List<DrawingResponse> forClass(@AuthenticationPrincipal UUID accountId, @PathVariable UUID classId) {
        return drawings.forClass(accountId, classId).stream().map(DrawingResponse::of).toList();
    }

    @PostMapping("/api/v1/schools/classes/{classId}/drawings/{answerId}/feedback")
    public DrawingResponse classFeedback(@AuthenticationPrincipal UUID accountId, @PathVariable UUID classId,
                                         @PathVariable UUID answerId, @Valid @RequestBody FeedbackRequest request) {
        return DrawingResponse.of(drawings.classFeedback(accountId, classId, answerId, request.body()));
    }
}
