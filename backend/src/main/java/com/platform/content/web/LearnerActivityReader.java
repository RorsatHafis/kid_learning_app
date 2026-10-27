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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.platform.content.web.ActivityContentDtos.ActivityItemResponse;
import static com.platform.content.web.ActivityContentDtos.ActivityVersionDetailResponse;

/**
 * A published activity version as a learner sees it: its items in order, each question's published version and its
 * options, and signed links for their media. Never serializes correct answers ({@link ActivityContentDtos}). Shared by
 * the child's activity read and the public demo (M2), so both show exactly the same thing. The caller decides who may
 * read the version; this only builds the view of a version already known to be learner-visible.
 */
@Component
public class LearnerActivityReader {

    private final ActivityService activityService;
    private final QuestionService questionService;
    private final ActivityItemRepository activityItemRepository;
    private final MediaService mediaService;

    public LearnerActivityReader(ActivityService activityService, QuestionService questionService,
                                 ActivityItemRepository activityItemRepository, MediaService mediaService) {
        this.activityService = activityService;
        this.questionService = questionService;
        this.activityItemRepository = activityItemRepository;
        this.mediaService = mediaService;
    }

    @Transactional(readOnly = true)
    public ActivityVersionDetailResponse read(ActivityVersion version) {
        Activity activity = activityService.getActivity(version.getActivityId());

        // Known Backend Fix C, extended (Battle 3): published-only, matching the ActivityVersion check
        // above - see QuestionService#getLearnerVisibleVersionById. Media for every item in one read.
        record Loaded(ActivityItem item, QuestionVersion version, QuestionType type, List<QuestionOption> options) {
        }
        List<Loaded> loaded = activityItemRepository.findByActivityVersionIdOrderBySequenceOrderAsc(version.getId()).stream()
                .map(item -> {
                    QuestionVersion questionVersion = questionService.getLearnerVisibleVersionById(item.getQuestionVersionId());
                    return new Loaded(item, questionVersion, questionService.getQuestion(questionVersion.getQuestionId()).getQuestionType(),
                            questionService.listOptions(questionVersion.getId()));
                })
                .toList();
        List<UUID> mediaIds = new java.util.ArrayList<>(loaded.stream()
                .flatMap(l -> ActivityContentDtos.mediaOf(l.version(), l.options()).stream()).toList());
        java.util.stream.Stream.of(version.getIntroductionImageAssetId(), version.getIntroductionAudioAssetId())
                .filter(java.util.Objects::nonNull).forEach(mediaIds::add);
        Map<UUID, MediaService.MediaRef> media = mediaService.refs(mediaIds);
        List<ActivityItemResponse> items = loaded.stream()
                .map(l -> ActivityItemResponse.forLearner(l.item().getId(), l.item().getSequenceOrder(), l.type(), l.version(),
                        l.options(), media))
                .toList();

        return new ActivityVersionDetailResponse(version.getId(), activity.getId(), activity.getTitle(), version.getInstructions(),
                version.getDifficultyLevel(), version.getEstimatedDurationSeconds(), items,
                ActivityContentDtos.LearnerIntroduction.of(version, media));
    }

}
