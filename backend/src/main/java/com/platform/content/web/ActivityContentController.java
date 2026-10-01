package com.platform.content.web;

import com.platform.content.entity.Activity;
import com.platform.content.entity.ActivityItem;
import com.platform.content.entity.ActivityVersion;
import com.platform.content.entity.QuestionOption;
import com.platform.content.entity.QuestionType;
import com.platform.content.entity.QuestionVersion;
import com.platform.content.media.MediaService;
import com.platform.content.repository.ActivityItemRepository;
import com.platform.content.service.ActivityService;
import com.platform.content.service.QuestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.platform.content.web.ActivityContentDtos.ActivityItemResponse;
import static com.platform.content.web.ActivityContentDtos.ActivityVersionDetailResponse;

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
    private final MediaService mediaService;

    public ActivityContentController(ActivityService activityService, QuestionService questionService,
                                      ActivityItemRepository activityItemRepository, MediaService mediaService) {
        this.activityService = activityService;
        this.questionService = questionService;
        this.activityItemRepository = activityItemRepository;
        this.mediaService = mediaService;
    }

    @GetMapping("/api/v1/activity-versions/{activityVersionId}")
    public ResponseEntity<ActivityVersionDetailResponse> getActivityVersion(@PathVariable UUID activityVersionId) {
        // Known Backend Fix C (no draft learner content): getLearnerVisibleVersionById,
        // never the general getVersion - see its javadoc. A caller who knows a draft
        // ActivityVersion id must not be able to retrieve draft learner content here.
        ActivityVersion version = activityService.getLearnerVisibleVersionById(activityVersionId);
        Activity activity = activityService.getActivity(version.getActivityId());

        // Known Backend Fix C, extended (Battle 3): published-only, matching the ActivityVersion check
        // above - see QuestionService#getLearnerVisibleVersionById. Media for every item in one read.
        record Loaded(ActivityItem item, QuestionVersion version, QuestionType type, List<QuestionOption> options) {
        }
        List<Loaded> loaded = activityItemRepository.findByActivityVersionIdOrderBySequenceOrderAsc(activityVersionId).stream()
                .map(item -> {
                    QuestionVersion questionVersion = questionService.getLearnerVisibleVersionById(item.getQuestionVersionId());
                    return new Loaded(item, questionVersion, questionService.getQuestion(questionVersion.getQuestionId()).getQuestionType(),
                            questionService.listOptions(questionVersion.getId()));
                })
                .toList();
        Map<UUID, MediaService.MediaRef> media = mediaService.refs(loaded.stream()
                .flatMap(l -> ActivityContentDtos.mediaOf(l.version(), l.options()).stream()).toList());
        List<ActivityItemResponse> items = loaded.stream()
                .map(l -> ActivityItemResponse.forLearner(l.item().getId(), l.item().getSequenceOrder(), l.type(), l.version(),
                        l.options(), media))
                .toList();

        return ResponseEntity.ok(new ActivityVersionDetailResponse(version.getId(), activity.getId(),
                activity.getTitle(), version.getInstructions(), version.getDifficultyLevel(),
                version.getEstimatedDurationSeconds(), items));
    }

}
