package com.platform.content.service;

import com.platform.common.entity.PublicationStatus;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.content.entity.Activity;
import com.platform.content.entity.ActivitySkill;
import com.platform.content.entity.ActivityItem;
import com.platform.content.repository.ActivityItemRepository;
import java.math.BigDecimal;
import com.platform.content.entity.ActivityVersion;
import com.platform.content.repository.ActivityRepository;
import com.platform.content.repository.ActivitySkillRepository;
import com.platform.content.repository.ActivityVersionRepository;
import com.platform.content.repository.QuestionVersionRepository;
import com.platform.knowledge.repository.SkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final ActivityVersionRepository activityVersionRepository;
    private final ActivitySkillRepository activitySkillRepository;
    private final SkillRepository skillRepository;
    private final ActivityItemRepository activityItemRepository;
    private final QuestionVersionRepository questionVersionRepository;

    public ActivityService(ActivityRepository activityRepository, ActivityVersionRepository activityVersionRepository,
                            ActivitySkillRepository activitySkillRepository, SkillRepository skillRepository, ActivityItemRepository activityItemRepository, QuestionVersionRepository questionVersionRepository) {
        this.activityRepository = activityRepository;
        this.activityVersionRepository = activityVersionRepository;
        this.activitySkillRepository = activitySkillRepository;
        this.skillRepository = skillRepository;
        this.activityItemRepository = activityItemRepository;
        this.questionVersionRepository = questionVersionRepository;
    }

    @Transactional
    public Activity createActivity(UUID subjectId, UUID lessonId, String activityType, String title) {
        return activityRepository.save(Activity.create(subjectId, lessonId, activityType, title));
    }

    @Transactional(readOnly = true)
    public Activity getActivity(UUID id) {
        return activityRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Activity", id));
    }

    @Transactional(readOnly = true)
    public List<Activity> listByLesson(UUID lessonId) {
        return activityRepository.findByLessonId(lessonId);
    }

    @Transactional
    public ActivityVersion createDraftVersion(UUID activityId, String instructions, Integer difficultyLevel,
                                               Integer estimatedDurationSeconds) {
        if (!activityRepository.existsById(activityId)) {
            throw new ResourceNotFoundException("Activity", activityId);
        }
        int nextVersionNumber = activityVersionRepository.findTopByActivityIdOrderByVersionNumberDesc(activityId)
                .map(previous -> previous.getVersionNumber() + 1)
                .orElse(1);
        return activityVersionRepository.save(
                ActivityVersion.draft(activityId, nextVersionNumber, instructions, difficultyLevel, estimatedDurationSeconds));
    }

    @Transactional(readOnly = true)
    public ActivityVersion getVersion(UUID activityVersionId) {
        return activityVersionRepository.findById(activityVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("ActivityVersion", activityVersionId));
    }

    @Transactional(readOnly = true)
    public ActivityVersion getPublishedVersion(UUID activityId) {
        return activityVersionRepository
                .findFirstByActivityIdAndStatusOrderByVersionNumberDesc(activityId, PublicationStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Published ActivityVersion for activity", activityId));
    }

    /**
     * Known Backend Fix C (published-only learner content): the narrow accessor
     * {@code ActivityContentController} (the learner-facing content endpoint) must
     * use instead of {@link #getVersion}. A caller who knows or guesses a DRAFT (or
     * RETIRED) ActivityVersion id must not be able to retrieve its content early -
     * {@link #getVersion} alone doesn't check status at all, so it was possible to
     * pull draft learner content by id. Deliberately indistinguishable from "this id
     * doesn't exist" (same exception, same message shape): a learner-facing endpoint
     * has no legitimate reason to confirm that an id it was given is a real draft
     * version that simply isn't visible yet.
     */
    @Transactional(readOnly = true)
    public ActivityVersion getPublishedVersionById(UUID activityVersionId) {
        ActivityVersion version = getVersion(activityVersionId);
        if (version.getStatus() != PublicationStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Published ActivityVersion", activityVersionId);
        }
        return version;
    }

    /** See CurriculumService.publish() for the retire-before-publish ordering rationale (V20 partial unique index). */
    @Transactional
    public ActivityVersion publish(UUID activityVersionId) {
        ActivityVersion version = getVersion(activityVersionId);
        if (activityItemRepository.findByActivityVersionIdOrderBySequenceOrderAsc(activityVersionId).isEmpty()) {
            throw new IllegalStateException("Activity version must contain at least one question before publishing");
        }

        activityVersionRepository
                .findFirstByActivityIdAndStatusOrderByVersionNumberDesc(version.getActivityId(), PublicationStatus.PUBLISHED)
                .ifPresent(previouslyPublished -> {
                    previouslyPublished.retire();
                    activityVersionRepository.saveAndFlush(previouslyPublished);
                });

        version.publish(Instant.now());
        return activityVersionRepository.saveAndFlush(version);
    }

    @Transactional
    public ActivityItem addItem(UUID activityVersionId, UUID questionVersionId, int sequenceOrder, BigDecimal points) {
        if (!activityVersionRepository.existsById(activityVersionId)) throw new ResourceNotFoundException("ActivityVersion", activityVersionId);
        if (!questionVersionRepository.existsById(questionVersionId)) throw new ResourceNotFoundException("QuestionVersion", questionVersionId);
        if (activityItemRepository.findByActivityVersionIdAndQuestionVersionId(activityVersionId, questionVersionId).isPresent()) throw new IllegalArgumentException("Question is already in this activity version");
        return activityItemRepository.save(ActivityItem.create(activityVersionId, questionVersionId, sequenceOrder, points));
    }

    @Transactional
    public ActivitySkill tagSkill(UUID activityId, UUID skillId) {
        if (!activityRepository.existsById(activityId)) {
            throw new ResourceNotFoundException("Activity", activityId);
        }
        if (!skillRepository.existsById(skillId)) {
            throw new ResourceNotFoundException("Skill", skillId);
        }
        return activitySkillRepository.save(ActivitySkill.create(activityId, skillId));
    }

    @Transactional(readOnly = true)
    public List<ActivitySkill> listSkills(UUID activityId) {
        return activitySkillRepository.findByActivityId(activityId);
    }

}