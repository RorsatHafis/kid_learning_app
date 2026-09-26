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
import com.platform.content.entity.Question;
import com.platform.content.entity.QuestionVersion;
import com.platform.content.repository.QuestionRepository;
import com.platform.content.repository.QuestionVersionRepository;
import com.platform.content.validation.ContentValidationException;
import com.platform.content.validation.ContentValidator;
import com.platform.content.validation.ValidationReport;
import com.platform.knowledge.repository.SkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final ActivityVersionRepository activityVersionRepository;
    private final ActivitySkillRepository activitySkillRepository;
    private final SkillRepository skillRepository;
    private final ActivityItemRepository activityItemRepository;
    private final QuestionVersionRepository questionVersionRepository;
    private final ContentValidator contentValidator;
    private final QuestionRepository questionRepository;
    private final ContentOwnership contentOwnership;

    public ActivityService(ActivityRepository activityRepository, ActivityVersionRepository activityVersionRepository,
                            ActivitySkillRepository activitySkillRepository, SkillRepository skillRepository, ActivityItemRepository activityItemRepository, QuestionVersionRepository questionVersionRepository,
                            ContentValidator contentValidator, QuestionRepository questionRepository,
                            ContentOwnership contentOwnership) {
        this.contentValidator = contentValidator;
        this.questionRepository = questionRepository;
        this.contentOwnership = contentOwnership;
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
     *
     * <p>Battle 3R: a RETIRED version is readable. It was superseded by a newer
     * version, not withdrawn, and existing learning path items and attempts keep
     * pointing at it; hiding it stranded those children (L5) and made completed
     * attempts unreconstructable. Only DRAFT stays hidden - see {@link
     * com.platform.common.entity.PublishableVersion#wasPublished()}. Reading is not
     * starting: new work only ever uses the current PUBLISHED version.
     */
    @Transactional(readOnly = true)
    public ActivityVersion getLearnerVisibleVersionById(UUID activityVersionId) {
        ActivityVersion version = getVersion(activityVersionId);
        if (!version.wasPublished()) {
            throw new ResourceNotFoundException("Published ActivityVersion", activityVersionId);
        }
        return version;
    }

    /**
     * See CurriculumService.publish() for the retire-before-publish ordering rationale (V20 partial unique index).
     *
     * <p>Battle 3 (content read-back integrity): also requires every linked {@link
     * ActivityItem}'s {@link com.platform.content.entity.QuestionVersion} to
     * already be {@code PUBLISHED}. Neither this method nor {@link #addItem}
     * previously checked that - {@code addItem} only required the question version
     * to exist, not to be published, and this method only required at least one
     * item to exist - so a DRAFT question version could be linked into an activity
     * version and then published along with it. The learner-facing read path
     * ({@code ActivityContentController}) gates the ActivityVersion itself on
     * PUBLISHED (Known Backend Fix C) but reads each question via {@code
     * QuestionService.getVersion}, which does not check status - so that draft
     * question's prompt/options would have been served to a child inside an
     * otherwise-legitimately-published activity. Closing it here, at the one place
     * an ActivityVersion is allowed to become learner-visible, protects every
     * current and future reader of published activity content, not just this one
     * controller.
     *
     * <p>W3.1: that rule and every other pre-publication check now live in {@link
     * ContentValidator#activityVersion}, the same report the Content Studio shows.
     */
    @Transactional
    public ActivityVersion publish(UUID activityVersionId) {
        ActivityVersion version = getVersion(activityVersionId);
        // Includes the Battle 3 rule above (QUESTION_NOT_PUBLISHED) and "at least one question" (NO_QUESTIONS).
        ValidationReport report = contentValidator.activityVersion(activityVersionId);
        if (!report.publishable()) {
            throw new ContentValidationException("Activity version " + activityVersionId, report);
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
        ActivityVersion version = activityVersionRepository.findById(activityVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("ActivityVersion", activityVersionId));
        requireUsableQuestion(version.getActivityId(), questionVersionId);
        if (activityItemRepository.findByActivityVersionIdAndQuestionVersionId(activityVersionId, questionVersionId).isPresent()) throw new IllegalArgumentException("Question is already in this activity version");
        return activityItemRepository.save(ActivityItem.create(activityVersionId, questionVersionId, sequenceOrder, points));
    }

    public record ItemDraft(UUID questionVersionId, BigDecimal points) {
    }

    /**
     * Replaces a draft's questions, in the given order. Each must be a question's
     * current PUBLISHED version from content this activity may use (N7: platform
     * content, or the activity owner's own).
     */
    @Transactional
    public List<ActivityItem> replaceItems(UUID activityVersionId, List<ItemDraft> items) {
        ActivityVersion version = getVersion(activityVersionId);
        requireDraft(version);
        if (items.stream().map(ItemDraft::questionVersionId).distinct().count() != items.size()) {
            throw new IllegalArgumentException("A question can appear only once in an activity");
        }
        for (ItemDraft item : items) {
            QuestionVersion questionVersion = requireUsableQuestion(version.getActivityId(), item.questionVersionId());
            if (questionVersion.getStatus() != PublicationStatus.PUBLISHED) {
                throw new IllegalStateException("Only a question's current published version can be added (question version %s is %s)"
                        .formatted(questionVersion.getId(), questionVersion.getStatus()));
            }
        }
        activityItemRepository.deleteByActivityVersionId(activityVersionId);
        activityItemRepository.flush();
        for (int i = 0; i < items.size(); i++) {
            BigDecimal points = items.get(i).points() == null ? BigDecimal.ONE : items.get(i).points();
            activityItemRepository.save(ActivityItem.create(activityVersionId, items.get(i).questionVersionId(), i, points));
        }
        return activityItemRepository.findByActivityVersionIdOrderBySequenceOrderAsc(activityVersionId);
    }

    @Transactional
    public ActivityVersion reviseDraft(UUID activityVersionId, String instructions, Integer difficultyLevel,
                                       Integer estimatedDurationSeconds) {
        ActivityVersion version = getVersion(activityVersionId);
        version.reviseDraft(instructions, difficultyLevel, estimatedDurationSeconds);
        return activityVersionRepository.saveAndFlush(version);
    }

    /**
     * The draft to edit next: the open draft if there is one, otherwise a new draft
     * copied from the latest version, each question re-pinned to that question's
     * current published version (a new activity version must not pin superseded
     * questions - see {@link #publish}).
     */
    @Transactional
    public ActivityVersion openDraft(UUID activityId) {
        Optional<ActivityVersion> latest = activityVersionRepository.findTopByActivityIdOrderByVersionNumberDesc(activityId);
        if (latest.isPresent() && latest.get().getStatus() == PublicationStatus.DRAFT) {
            return latest.get();
        }
        if (latest.isEmpty()) {
            return createDraftVersion(activityId, null, null, null);
        }
        ActivityVersion source = latest.get();
        ActivityVersion draft = createDraftVersion(activityId, source.getInstructions(), source.getDifficultyLevel(),
                source.getEstimatedDurationSeconds());
        int order = 0;
        for (ActivityItem item : activityItemRepository.findByActivityVersionIdOrderBySequenceOrderAsc(source.getId())) {
            UUID questionId = questionVersionRepository.findById(item.getQuestionVersionId()).orElseThrow().getQuestionId();
            Optional<QuestionVersion> current = questionVersionRepository
                    .findFirstByQuestionIdAndStatusOrderByVersionNumberDesc(questionId, PublicationStatus.PUBLISHED);
            if (current.isPresent()) {
                activityItemRepository.save(ActivityItem.create(draft.getId(), current.get().getId(), order++, item.getPoints()));
            }
        }
        return draft;
    }

    @Transactional(readOnly = true)
    public List<ActivityVersion> listVersions(UUID activityId) {
        return activityVersionRepository.findByActivityIdOrderByVersionNumberAsc(activityId);
    }

    /** Idempotent: tagging a skill the activity already has returns the existing tag. */
    @Transactional
    public ActivitySkill tagSkill(UUID activityId, UUID skillId) {
        if (!activityRepository.existsById(activityId)) {
            throw new ResourceNotFoundException("Activity", activityId);
        }
        if (!skillRepository.existsById(skillId)) {
            throw new ResourceNotFoundException("Skill", skillId);
        }
        return activitySkillRepository.findByActivityIdAndSkillId(activityId, skillId)
                .orElseGet(() -> activitySkillRepository.save(ActivitySkill.create(activityId, skillId)));
    }

    /** Skill tags belong to the activity, not a version (PD-08): this changes placement for its published version too. */
    @Transactional
    public void untagSkill(UUID activityId, UUID skillId) {
        activitySkillRepository.findByActivityIdAndSkillId(activityId, skillId).ifPresent(activitySkillRepository::delete);
    }

    /** N7: an activity may contain platform questions or its own owner's, nothing else - answered as not found. */
    private QuestionVersion requireUsableQuestion(UUID activityId, UUID questionVersionId) {
        QuestionVersion questionVersion = questionVersionRepository.findById(questionVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("QuestionVersion", questionVersionId));
        Question question = questionRepository.findById(questionVersion.getQuestionId())
                .orElseThrow(() -> new ResourceNotFoundException("QuestionVersion", questionVersionId));
        if (!ContentOwnership.usableBy(question.getOwnerAccountId(), contentOwnership.activityOwner(activityId).orElse(null))) {
            throw new ResourceNotFoundException("QuestionVersion", questionVersionId);
        }
        return questionVersion;
    }

    private static void requireDraft(ActivityVersion version) {
        if (version.getStatus() != PublicationStatus.DRAFT) {
            throw new IllegalStateException("Only a DRAFT activity version can be edited, %s is %s"
                    .formatted(version.getId(), version.getStatus()));
        }
    }

    @Transactional(readOnly = true)
    public List<ActivitySkill> listSkills(UUID activityId) {
        return activitySkillRepository.findByActivityId(activityId);
    }

}