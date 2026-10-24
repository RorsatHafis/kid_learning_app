package com.platform.content.studio;

import com.platform.content.entity.LessonVisibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** M5b over HTTP: sharing a lesson with your school, colleagues' shared lessons, and copying one into your own. */
@RestController
@RequestMapping("/api/v1/studio")
public class LessonSharingController {

    public record VisibilityRequest(@NotNull LessonVisibility visibility) {
    }

    private final LessonSharingService sharing;

    public LessonSharingController(LessonSharingService sharing) {
        this.sharing = sharing;
    }

    @PutMapping("/lessons/{lessonId}/visibility")
    public ResponseEntity<Void> setVisibility(@AuthenticationPrincipal UUID accountId, @PathVariable UUID lessonId,
                                              @Valid @RequestBody VisibilityRequest request) {
        sharing.setVisibility(accountId, lessonId, request.visibility());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/shared-lessons")
    public List<LessonSharingService.SharedLesson> shared(@AuthenticationPrincipal UUID accountId, @RequestParam UUID subjectId) {
        return sharing.shared(accountId, subjectId);
    }

    @PostMapping("/shared-lessons/{lessonId}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    public StudioDtos.LessonSummary copy(@AuthenticationPrincipal UUID accountId, @PathVariable UUID lessonId) {
        return sharing.copy(accountId, lessonId);
    }

}
