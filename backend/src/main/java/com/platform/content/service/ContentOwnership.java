package com.platform.content.service;

import com.platform.common.web.ResourceNotFoundException;
import com.platform.content.entity.Activity;
import com.platform.content.entity.Lesson;
import com.platform.content.repository.ActivityRepository;
import com.platform.content.repository.LessonRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * N7: who owns a piece of content. Empty means platform content. Shared by learner
 * selection ({@code PathContentScope}) and authoring validation so both apply the
 * same rule.
 */
@Component
public class ContentOwnership {

    private final ActivityRepository activityRepository;
    private final LessonRepository lessonRepository;

    public ContentOwnership(ActivityRepository activityRepository, LessonRepository lessonRepository) {
        this.activityRepository = activityRepository;
        this.lessonRepository = lessonRepository;
    }

    /**
     * The account that owns an activity's content: its lesson's owner. A standalone
     * activity (no lesson) is not teacher-manageable ({@code ContentAccessGuard}), so it
     * is platform content.
     */
    @Transactional(readOnly = true)
    public Optional<UUID> activityOwner(UUID activityId) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new ResourceNotFoundException("Activity", activityId));
        if (activity.getLessonId() == null) {
            return Optional.empty();
        }
        return lessonRepository.findById(activity.getLessonId()).map(Lesson::getOwnerAccountId);
    }

    /** Whether content owned by {@code contentOwner} may be used by content owned by {@code consumerOwner}. */
    public static boolean usableBy(UUID contentOwner, UUID consumerOwner) {
        return contentOwner == null || contentOwner.equals(consumerOwner);
    }

}
