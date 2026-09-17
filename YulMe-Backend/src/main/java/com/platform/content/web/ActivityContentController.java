package com.platform.content.web;

import com.platform.content.entity.Activity;
import com.platform.content.entity.ActivityItem;
import com.platform.content.entity.ActivityVersion;
import com.platform.content.entity.Question;
import com.platform.content.entity.QuestionVersion;
import com.platform.content.repository.ActivityItemRepository;
import com.platform.content.service.ActivityService;
import com.platform.content.service.QuestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static com.platform.content.web.ActivityContentDtos.ActivityItemResponse;
import static com.platform.content.web.ActivityContentDtos.ActivityVersionDetailResponse;
import static com.platform.content.web.ActivityContentDtos.QuestionOptionResponse;

/**
 * The missing "load the real questions" step of the golden journey: every other
 * piece of the activity-attempt flow (start/submit/complete/adaptive-next/smart-review)
 * already existed, but nothing served the actual prompt/options a child needs to
 * see to answer a question - the frontend had no choice but to hardcode/mock
 * question content. Read-only and not family/child-scoped: activity/question
 * content is shared published catalog data, not per-child data (contrast
 * {@link com.platform.family.service.FamilyAccessGuard}-guarded endpoints, which
 * gate access to a specific child's own records). Never serializes
 * {@code QuestionVersion.correctAnswer} or {@code QuestionOption.correct} - see
 * {@link ActivityContentDtos}.
 */
@RestController
public class ActivityContentController {

    private final ActivityService activityService;
    private final QuestionService questionService;
    private final ActivityItemRepository activityItemRepository;

    public ActivityContentController(ActivityService activityService, QuestionService questionService,
                                      ActivityItemRepository activityItemRepository) {
        this.activityService = activityService;
        this.questionService = questionService;
        this.activityItemRepository = activityItemRepository;
    }

    @GetMapping("/api/v1/activity-versions/{activityVersionId}")
    public ResponseEntity<ActivityVersionDetailResponse> getActivityVersion(@PathVariable UUID activityVersionId) {
        // Known Backend Fix C (published-only learner content): getPublishedVersionById,
        // never the general getVersion - see its javadoc. A caller who knows a draft
        // ActivityVersion id must not be able to retrieve draft learner content here.
        ActivityVersion version = activityService.getPublishedVersionById(activityVersionId);
        Activity activity = activityService.getActivity(version.getActivityId());

        List<ActivityItemResponse> items = activityItemRepository
                .findByActivityVersionIdOrderBySequenceOrderAsc(activityVersionId).stream()
                .map(this::toItemResponse)
                .toList();

        return ResponseEntity.ok(new ActivityVersionDetailResponse(version.getId(), activity.getId(),
                activity.getTitle(), version.getInstructions(), version.getDifficultyLevel(),
                version.getEstimatedDurationSeconds(), items));
    }

    private ActivityItemResponse toItemResponse(ActivityItem item) {
        QuestionVersion questionVersion = questionService.getVersion(item.getQuestionVersionId());
        Question question = questionService.getQuestion(questionVersion.getQuestionId());

        List<QuestionOptionResponse> options = questionService.listOptions(questionVersion.getId()).stream()
                .map(QuestionOptionResponse::from)
                .toList();

        return new ActivityItemResponse(item.getId(), item.getSequenceOrder(), questionVersion.getId(),
                question.getQuestionType(), questionVersion.getPrompt(), options);
    }

}
