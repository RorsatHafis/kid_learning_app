package com.platform.learning.service;

import com.platform.common.web.ResourceNotFoundException;
import com.platform.content.entity.Activity;
import com.platform.content.entity.ActivityVersionSkill;
import com.platform.content.entity.ActivityVersion;
import com.platform.content.repository.ActivityItemRepository;
import com.platform.content.repository.ActivityRepository;
import com.platform.content.repository.ActivityVersionSkillRepository;
import com.platform.content.repository.ActivityVersionRepository;
import com.platform.curriculum.entity.AgeHub;
import com.platform.curriculum.entity.Curriculum;
import com.platform.curriculum.entity.CurriculumObjective;
import com.platform.curriculum.entity.LearningObjective;
import com.platform.curriculum.entity.Subject;
import com.platform.curriculum.repository.AgeHubRepository;
import com.platform.curriculum.repository.LearningObjectiveRepository;
import com.platform.curriculum.repository.SubjectRepository;
import com.platform.curriculum.service.CurriculumService;
import com.platform.knowledge.entity.LearningObjectiveSkill;
import com.platform.knowledge.entity.Skill;
import com.platform.knowledge.service.LearningObjectiveSkillService;
import com.platform.knowledge.repository.SkillRepository;
import com.platform.learner.entity.AdaptationDecision;
import com.platform.learner.entity.AdaptationDecisionType;
import com.platform.learner.repository.AdaptationDecisionRepository;
import com.platform.learning.entity.ActivityAttempt;
import com.platform.learning.entity.AnswerRecord;
import com.platform.learning.entity.Enrollment;
import com.platform.learning.entity.EnrollmentStatus;
import com.platform.learning.entity.LearningPath;
import com.platform.learning.entity.LearningPathItem;
import com.platform.learning.entity.LearningPathItemSource;
import com.platform.learning.entity.LearningPathItemStatus;
import com.platform.learning.repository.ActivityAttemptRepository;
import com.platform.learning.repository.AnswerRecordRepository;
import com.platform.learning.repository.EnrollmentRepository;
import com.platform.learning.repository.LearningPathItemRepository;
import com.platform.learning.repository.LearningPathRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read model of a child's learning: their enrollments, each enrollment's journey
 * (the learning path as a sequence of steps, with why each step is there), and the
 * activities they have completed. Read-only - it presents persisted state and decides
 * nothing; the decisions it explains were made and recorded when an activity was
 * completed ({@link PostCompletionService}).
 *
 * <p>Explanations are derived from records, never guessed: an ADAPTIVE step is
 * explained by the {@link AdaptationDecision} that placed it (which records the
 * governing and target skills and the attempt that triggered it); a REVIEW step by
 * the child's Smart Review item for one of the step's skills; a CURRICULUM step by
 * its curriculum.
 */
@Service
public class LearningJourneyService {

    private final EnrollmentRepository enrollmentRepository;
    private final LearningPathRepository learningPathRepository;
    private final LearningPathItemRepository learningPathItemRepository;
    private final ActivityAttemptRepository activityAttemptRepository;
    private final AnswerRecordRepository answerRecordRepository;
    private final ActivityVersionRepository activityVersionRepository;
    private final ActivityRepository activityRepository;
    private final ActivityVersionSkillRepository activitySkillRepository;
    private final ActivityItemRepository activityItemRepository;
    private final SkillRepository skillRepository;
    private final AdaptationDecisionRepository adaptationDecisionRepository;
    private final CurriculumService curriculumService;
    private final SubjectRepository subjectRepository;
    private final AgeHubRepository ageHubRepository;
    private final com.platform.review.repository.ReviewItemRepository reviewItemRepository;
    private final LearningObjectiveRepository learningObjectiveRepository;
    private final LearningObjectiveSkillService learningObjectiveSkillService;
    private final PlacementNames placementNames;
    private final com.platform.child.repository.ChildRepository childRepository;

    /**
     * An activity's name as the reader gets it: in Khmer for a Khmer reader when the version's Khmer is complete
     * (ActivityTranslations - the same rule as the activity screen), otherwise as written.
     */
    private String titleFor(Activity activity, ActivityVersion version) {
        return activityTranslations.forReader(activity, version).map(com.platform.content.service.ActivityTranslations.Khmer::title).orElse(activity.getTitle());
    }

    private final com.platform.content.service.ActivityTranslations activityTranslations;

    private final com.platform.content.service.CatalogNames catalogNames;

    public LearningJourneyService(EnrollmentRepository enrollmentRepository, LearningPathRepository learningPathRepository,
                                  LearningPathItemRepository learningPathItemRepository,
                                  ActivityAttemptRepository activityAttemptRepository,
                                  AnswerRecordRepository answerRecordRepository,
                                  ActivityVersionRepository activityVersionRepository,
                                  ActivityRepository activityRepository, ActivityVersionSkillRepository activitySkillRepository,
                                  ActivityItemRepository activityItemRepository, SkillRepository skillRepository,
                                  AdaptationDecisionRepository adaptationDecisionRepository,
                                  CurriculumService curriculumService, SubjectRepository subjectRepository,
                                  AgeHubRepository ageHubRepository,
                                  com.platform.review.repository.ReviewItemRepository reviewItemRepository,
                                  LearningObjectiveRepository learningObjectiveRepository,
                                  LearningObjectiveSkillService learningObjectiveSkillService, PlacementNames placementNames,
                                  com.platform.child.repository.ChildRepository childRepository,
                                  com.platform.content.service.ActivityTranslations activityTranslations,
            com.platform.content.service.CatalogNames catalogNames) {
        this.catalogNames = catalogNames;
        this.activityTranslations = activityTranslations;
        this.childRepository = childRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.learningPathRepository = learningPathRepository;
        this.learningPathItemRepository = learningPathItemRepository;
        this.activityAttemptRepository = activityAttemptRepository;
        this.answerRecordRepository = answerRecordRepository;
        this.activityVersionRepository = activityVersionRepository;
        this.activityRepository = activityRepository;
        this.activitySkillRepository = activitySkillRepository;
        this.activityItemRepository = activityItemRepository;
        this.skillRepository = skillRepository;
        this.adaptationDecisionRepository = adaptationDecisionRepository;
        this.curriculumService = curriculumService;
        this.subjectRepository = subjectRepository;
        this.ageHubRepository = ageHubRepository;
        this.reviewItemRepository = reviewItemRepository;
        this.learningObjectiveRepository = learningObjectiveRepository;
        this.learningObjectiveSkillService = learningObjectiveSkillService;
        this.placementNames = placementNames;
    }

    // ------------------------------------------------------------------ view types

    public record SkillRef(UUID id, String name) {
    }

    public record CurriculumRef(UUID id, String name, String subjectCode, String subjectName, String ageHubName) {
    }

    /** Why a step is on the path. Each value corresponds to a recorded cause. */
    public enum PlacementReason {
        /** Part of the curriculum the child is enrolled in. */
        CURRICULUM,
        /** The Adaptive Engine placed practice on a skill's prerequisite after low mastery. */
        REINFORCE_PREREQUISITE,
        /** The Adaptive Engine placed more practice on the same skill after low mastery. */
        REINFORCE_SKILL,
        /** The Adaptive Engine placed a harder activity after strong, well-evidenced mastery. */
        CHALLENGE,
        /** Smart Review scheduled a review of a skill that needs reinforcing. */
        REVIEW,
        /** Placed by a teacher. */
        TEACHER_ASSIGNED,
        /** The step's source is known but its recorded cause was not found. */
        UNEXPLAINED
    }

    /**
     * @param skill           the skill the cause is about (the governing skill for an
     *                        adaptive step, the reviewed skill for a review step)
     * @param targetSkill     for REINFORCE_PREREQUISITE, the prerequisite practiced
     * @param afterActivityTitle / afterScore the completed activity that triggered the decision
     */
    /** @param assignedBy for practice a class's teacher placed: the class and school ("Grade 1 A · Wat Phnom Primary") */
    public record Explanation(PlacementReason reason, SkillRef skill, SkillRef targetSkill,
                              String afterActivityTitle, BigDecimal afterScore, Instant decidedAt, String assignedBy) {

        public Explanation(PlacementReason reason, SkillRef skill, SkillRef targetSkill, String afterActivityTitle,
                           BigDecimal afterScore, Instant decidedAt) {
            this(reason, skill, targetSkill, afterActivityTitle, afterScore, decidedAt, null);
        }
    }

    /**
     * @param chapterId the learning objective this step belongs to (see {@link Chapter}); null when
     *                  none of the curriculum's objectives covers the activity's skills
     */
    public record JourneyStep(UUID learningPathItemId, UUID activityVersionId, String title, String activityType,
                              Integer difficultyLevel, Integer estimatedDurationSeconds, int questionCount,
                              int sequenceOrder, LearningPathItemStatus status, LearningPathItemSource source,
                              List<SkillRef> skills, BigDecimal score, Instant completedAt, Explanation explanation,
                              UUID chapterId) {
    }

    /** Where a child is in a chapter, from its steps. Says nothing about mastery. */
    public enum ChapterState {
        /** Every step in it is done. */
        COMPLETE,
        /** It holds the next step. */
        CURRENT,
        /** Some steps done, the next step is elsewhere. */
        STARTED,
        /** Not started yet. */
        UPCOMING,
        /** No published activity on the path for it yet. */
        NOT_READY
    }

    /**
     * A chapter of a journey: one learning objective of the enrolled curriculum version, in the
     * order its educators set (PLATFORM-EXPANSION.md §1). Its steps are the path items whose
     * activity practises one of the objective's skills, adaptive and review steps included.
     */
    public record Chapter(UUID id, int position, String title, String description, int totalSteps,
                          int completedSteps, ChapterState state) {
    }

    /** Steps are in play order: done (as completed), then waiting in the order they will be served. */
    public record Journey(UUID enrollmentId, UUID childId, CurriculumRef curriculum, EnrollmentStatus status,
                          Instant enrolledAt, int completedSteps, int totalSteps, UUID nextStepId,
                          List<Chapter> chapters, List<JourneyStep> steps) {
    }

    /**
     * @param currentChapter the chapter holding the next step (null when nothing is waiting)
     * @param chapterCount   how many chapters the journey has
     * @param chapters       every chapter with its state, in curriculum order
     */
    /** {@code ageFit}: for a family (platform) path, whether it is for the child's age now; null for a class's path. */
    public record EnrollmentOverview(UUID enrollmentId, CurriculumRef curriculum, EnrollmentStatus status,
                                     Instant enrolledAt, int completedSteps, int totalSteps, JourneyStep nextStep,
                                     Chapter currentChapter, int chapterCount, List<Chapter> chapters, AgeFit ageFit) {
    }

    /**
     * Whether a family path's age group is the child's now (the rule automatic enrolment and the family catalog use:
     * the age group the child's age falls in). When it isn't, {@code suggested} names a published platform path in
     * the same subject for the child's age that the child isn't on, if there is one. The family decides; nothing
     * changes on its own.
     */
    public record AgeFit(boolean forChildAge, int childAge, int minAge, Integer maxAge, UUID suggestedCurriculumId,
                         String suggestedCurriculumName) {
    }

    /** What the platform decided after a completed activity, as recorded. */
    public record DecisionSummary(AdaptationDecisionType type, SkillRef governingSkill, SkillRef targetSkill,
                                  String placedActivityTitle) {
    }

    /** {@code hintedAnswers} (D-16): answers given after opening the hint - practice, not shown on their own. */
    public record CompletedActivity(UUID attemptId, UUID activityVersionId, String title, String subjectName,
                                    LearningPathItemSource source, BigDecimal score, int correctAnswers,
                                    int totalAnswers, Instant startedAt, Instant completedAt, List<SkillRef> skills,
                                    DecisionSummary decision, int hintedAnswers) {
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public List<EnrollmentOverview> enrollmentsFor(UUID childId) {
        Lookups lookups = new Lookups(childId);
        List<Enrollment> active = enrollmentRepository.findByChildId(childId).stream()
                // Y19: a stopped journey is not one the child is on; choosing it again brings it back.
                .filter(enrollment -> enrollment.getStatus() != EnrollmentStatus.WITHDRAWN)
                .sorted(Comparator.comparing(Enrollment::getEnrolledAt))
                .toList();
        java.util.Set<UUID> onCurricula = active.stream()
                .map(enrollment -> curriculumService.getVersion(enrollment.getCurriculumVersionId()).getCurriculumId())
                .collect(java.util.stream.Collectors.toSet());
        Integer childAge = childRepository.findById(childId).map(child -> child.ageInYears(java.time.LocalDate.now())).orElse(null);
        return active.stream()
                .map(enrollment -> {
                    Journey journey = journey(enrollment, lookups);
                    JourneyStep next = journey.steps().stream()
                            .filter(step -> step.learningPathItemId().equals(journey.nextStepId()))
                            .findFirst().orElse(null);
                    Chapter current = journey.chapters().stream()
                            .filter(chapter -> chapter.state() == ChapterState.CURRENT).findFirst().orElse(null);
                    return new EnrollmentOverview(enrollment.getId(), journey.curriculum(), enrollment.getStatus(),
                            enrollment.getEnrolledAt(), journey.completedSteps(), journey.totalSteps(), next,
                            current, journey.chapters().size(), journey.chapters(),
                            ageFit(enrollment.getCurriculumVersionId(), childAge, onCurricula));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public Journey journeyFor(UUID enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment", enrollmentId));
        return journey(enrollment, new Lookups(enrollment.getChildId()));
    }

    @Transactional(readOnly = true)
    public List<CompletedActivity> completedActivities(UUID childId, int limit) {
        return completedActivities(childId, limit, null, null);
    }

    /**
     * A child's completed activities, newest first. With a cursor (the completion time and id of
     * the last one already shown), the ones completed before it; the page is read in the database,
     * not cut from the whole history.
     */
    @Transactional(readOnly = true)
    public List<CompletedActivity> completedActivities(UUID childId, int limit, Instant before, UUID beforeId) {
        if ((before == null) != (beforeId == null)) {
            throw new IllegalArgumentException("before and beforeId go together");
        }
        Lookups lookups = new Lookups(childId);
        List<ActivityAttempt> page = before == null
                ? activityAttemptRepository.findCompletedNewestFirst(childId, org.springframework.data.domain.Limit.of(limit))
                : activityAttemptRepository.findCompletedBefore(childId, before, beforeId, org.springframework.data.domain.Limit.of(limit));
        // One query each for the whole page, not one per attempt (ReadQueryScalingIntegrationTests).
        Map<UUID, List<AnswerRecord>> answers = page.isEmpty() ? Map.of()
                : answerRecordRepository.findByActivityAttemptIdIn(page.stream().map(ActivityAttempt::getId).toList()).stream()
                        .collect(java.util.stream.Collectors.groupingBy(AnswerRecord::getActivityAttemptId));
        List<UUID> itemIds = page.stream().map(ActivityAttempt::getLearningPathItemId).filter(java.util.Objects::nonNull).toList();
        Map<UUID, LearningPathItemSource> sources = new HashMap<>();
        if (!itemIds.isEmpty()) {
            learningPathItemRepository.findAllById(itemIds).forEach(item -> sources.put(item.getId(), item.getSource()));
        }
        lookups.preload(page.stream().map(ActivityAttempt::getActivityVersionId).toList());
        return page.stream()
                .map(attempt -> completedActivity(attempt, lookups, answers.getOrDefault(attempt.getId(), List.of()),
                        attempt.getLearningPathItemId() == null ? null : sources.get(attempt.getLearningPathItemId())))
                .toList();
    }

    // ------------------------------------------------------------------ assembly

    private Journey journey(Enrollment enrollment, Lookups lookups) {
        CurriculumRef curriculum = curriculumFor(enrollment.getCurriculumVersionId());
        LearningPath path = learningPathRepository.findByEnrollmentId(enrollment.getId())
                .orElseThrow(() -> new ResourceNotFoundException("LearningPath for enrollment", enrollment.getId()));

        List<LearningPathItem> items = learningPathItemRepository.findByLearningPathIdOrderBySequenceOrderAsc(path.getId());
        List<ChapterSource> chapterSources = chapterSources(enrollment.getCurriculumVersionId());

        List<JourneyStep> steps = LearningPathService.playOrder(items).stream()
                .map(item -> step(item, lookups, chapterSources))
                .toList();

        int completed = (int) steps.stream().filter(step -> step.status() == LearningPathItemStatus.COMPLETED).count();
        // The same rule that serves the next activity (LearningPathService#nextOf).
        UUID nextStepId = LearningPathService.nextOf(items).map(LearningPathItem::getId).orElse(null);

        return new Journey(enrollment.getId(), enrollment.getChildId(), curriculum, enrollment.getStatus(),
                enrollment.getEnrolledAt(), completed, steps.size(), nextStepId,
                chapters(chapterSources, steps.stream().map(step -> new StepPlace(step.learningPathItemId(), step.chapterId(),
                        step.status())).toList(), nextStepId), steps);
    }

    /**
     * Where each of a child's paths stands - counts and chapters, no step detail - for many children
     * at once (a class, X20). The same rules as {@link #enrollmentsFor}: steps in play order, each in
     * the first chapter covering one of its version's skills, the next step as the path serves it,
     * chapter states as the journey has them. The statements depend on the curricula, not on how
     * many children there are.
     */
    public record EnrollmentSummary(UUID enrollmentId, CurriculumRef curriculum, Instant enrolledAt, int completedSteps,
                                    int totalSteps, Chapter currentChapter, List<Chapter> chapters) {
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<EnrollmentSummary>> summariesFor(java.util.Collection<UUID> childIds) {
        Map<UUID, List<EnrollmentSummary>> result = new LinkedHashMap<>();
        childIds.forEach(id -> result.put(id, new java.util.ArrayList<>()));
        if (childIds.isEmpty()) {
            return result;
        }
        List<Enrollment> enrollments = enrollmentRepository.findByChildIdIn(childIds).stream()
                .filter(enrollment -> enrollment.getStatus() != EnrollmentStatus.WITHDRAWN)
                .sorted(Comparator.comparing(Enrollment::getEnrolledAt))
                .toList();
        if (enrollments.isEmpty()) {
            return result;
        }
        Map<UUID, LearningPath> pathByEnrollment = new HashMap<>();
        learningPathRepository.findByEnrollmentIdIn(enrollments.stream().map(Enrollment::getId).toList())
                .forEach(path -> pathByEnrollment.put(path.getEnrollmentId(), path));
        Map<UUID, List<LearningPathItem>> itemsByPath = new HashMap<>();
        List<LearningPathItem> allItems = pathByEnrollment.isEmpty() ? List.of()
                : learningPathItemRepository.findByLearningPathIdInOrderBySequenceOrderAsc(
                        pathByEnrollment.values().stream().map(LearningPath::getId).toList());
        allItems.forEach(item -> itemsByPath.computeIfAbsent(item.getLearningPathId(), id -> new java.util.ArrayList<>()).add(item));
        Map<UUID, java.util.Set<UUID>> skillsByVersion = new HashMap<>();
        java.util.Set<UUID> versionIds = new java.util.HashSet<>();
        allItems.forEach(item -> versionIds.add(item.getActivityVersionId()));
        if (!versionIds.isEmpty()) {
            for (ActivityVersionSkill link : activitySkillRepository.findByActivityVersionIdIn(versionIds)) {
                skillsByVersion.computeIfAbsent(link.getActivityVersionId(), id -> new java.util.HashSet<>()).add(link.getSkillId());
            }
        }
        Map<UUID, List<ChapterSource>> sourcesByVersion = new HashMap<>();
        Map<UUID, CurriculumRef> curriculumByVersion = new HashMap<>();
        for (Enrollment enrollment : enrollments) {
            LearningPath path = pathByEnrollment.get(enrollment.getId());
            if (path == null) {
                throw new ResourceNotFoundException("LearningPath for enrollment", enrollment.getId());
            }
            UUID versionId = enrollment.getCurriculumVersionId();
            List<ChapterSource> sources = sourcesByVersion.computeIfAbsent(versionId, this::chapterSources);
            CurriculumRef curriculum = curriculumByVersion.computeIfAbsent(versionId, this::curriculumFor);
            List<LearningPathItem> items = itemsByPath.getOrDefault(path.getId(), List.of());
            List<StepPlace> places = LearningPathService.playOrder(items).stream()
                    .map(item -> new StepPlace(item.getId(), chapterOfSkills(skillsByVersion.getOrDefault(item.getActivityVersionId(),
                            java.util.Set.of()), sources), item.getStatus()))
                    .toList();
            UUID nextStepId = LearningPathService.nextOf(items).map(LearningPathItem::getId).orElse(null);
            List<Chapter> chapters = chapters(sources, places, nextStepId);
            int completed = (int) places.stream().filter(place -> place.status() == LearningPathItemStatus.COMPLETED).count();
            Chapter current = chapters.stream().filter(chapter -> chapter.state() == ChapterState.CURRENT).findFirst().orElse(null);
            result.get(enrollment.getChildId()).add(new EnrollmentSummary(enrollment.getId(), curriculum, enrollment.getEnrolledAt(),
                    completed, places.size(), current, chapters));
        }
        return result;
    }

    /** Where a step sits in its journey: what the chapter states are counted from. */
    private record StepPlace(UUID itemId, UUID chapterId, LearningPathItemStatus status) {
    }

    /** One objective of the curriculum version with the skills it covers, in curriculum order. */
    private record ChapterSource(UUID id, int position, String title, String description, java.util.Set<UUID> skillIds) {
    }

    private List<ChapterSource> chapterSources(UUID curriculumVersionId) {
        List<CurriculumObjective> objectives = curriculumService.listObjectives(curriculumVersionId).stream()
                .sorted(Comparator.comparingInt(CurriculumObjective::getDisplayOrder))
                .toList();
        Map<UUID, LearningObjective> byId = new HashMap<>();
        learningObjectiveRepository.findAllById(objectives.stream().map(CurriculumObjective::getLearningObjectiveId).toList())
                .forEach(objective -> byId.put(objective.getId(), objective));
        catalogNames.prefetch(com.platform.content.entity.LocalizableContentType.LEARNING_OBJECTIVE, byId.keySet());
        List<ChapterSource> sources = new java.util.ArrayList<>();
        int position = 1;
        for (CurriculumObjective link : objectives) {
            LearningObjective objective = byId.get(link.getLearningObjectiveId());
            if (objective == null) {
                continue;
            }
            java.util.Set<UUID> skills = learningObjectiveSkillService.listSkillsForObjective(objective.getId()).stream()
                    .map(LearningObjectiveSkill::getSkillId)
                    .collect(java.util.stream.Collectors.toSet());
            sources.add(new ChapterSource(objective.getId(), position++,
                    catalogNames.name(com.platform.content.entity.LocalizableContentType.LEARNING_OBJECTIVE, objective.getId(), objective.getTitle()),
                    catalogNames.description(com.platform.content.entity.LocalizableContentType.LEARNING_OBJECTIVE, objective.getId(), objective.getDescription()), skills));
        }
        return sources;
    }

    /** The first chapter, in curriculum order, that covers one of the step's skills. */
    private static UUID chapterOf(List<SkillRef> skills, List<ChapterSource> chapters) {
        return chapterOfSkills(skills.stream().map(SkillRef::id).toList(), chapters);
    }

    private static UUID chapterOfSkills(java.util.Collection<UUID> skillIds, List<ChapterSource> chapters) {
        for (ChapterSource chapter : chapters) {
            for (UUID skillId : skillIds) {
                if (chapter.skillIds().contains(skillId)) {
                    return chapter.id();
                }
            }
        }
        return null;
    }

    private static List<Chapter> chapters(List<ChapterSource> sources, List<StepPlace> steps, UUID nextStepId) {
        UUID currentChapter = steps.stream().filter(step -> step.itemId().equals(nextStepId))
                .findFirst().map(StepPlace::chapterId).orElse(null);
        return sources.stream().map(source -> {
            List<StepPlace> own = steps.stream().filter(step -> source.id().equals(step.chapterId())).toList();
            int done = (int) own.stream().filter(step -> step.status() == LearningPathItemStatus.COMPLETED).count();
            ChapterState state;
            if (own.isEmpty()) {
                state = ChapterState.NOT_READY;
            } else if (source.id().equals(currentChapter)) {
                state = ChapterState.CURRENT;
            } else if (done == own.size()) {
                state = ChapterState.COMPLETE;
            } else if (done > 0) {
                state = ChapterState.STARTED;
            } else {
                state = ChapterState.UPCOMING;
            }
            return new Chapter(source.id(), source.position(), source.title(), source.description(), own.size(), done, state);
        }).toList();
    }

    private JourneyStep step(LearningPathItem item, Lookups lookups, List<ChapterSource> chapters) {
        ActivityVersion version = lookups.version(item.getActivityVersionId());
        Activity activity = lookups.activity(version.getActivityId());
        Optional<ActivityAttempt> completedAttempt = lookups.completedOnPath(item.getLearningPathId()).stream()
                .filter(attempt -> item.getId().equals(attempt.getLearningPathItemId()))
                .max(Comparator.comparing(ActivityAttempt::getCompletedAt));

        List<SkillRef> skills = lookups.skillsOf(version.getId());
        return new JourneyStep(item.getId(), version.getId(), titleFor(activity, version), activity.getActivityType(),
                version.getDifficultyLevel(), version.getEstimatedDurationSeconds(),
                lookups.questionCount(version.getId()), item.getSequenceOrder(), item.getStatus(), item.getSource(),
                skills, completedAttempt.map(ActivityAttempt::getScore).orElse(null),
                item.getCompletedAt(), explain(item, activity, lookups), chapterOf(skills, chapters));
    }

    private Explanation explain(LearningPathItem item, Activity activity, Lookups lookups) {
        return switch (item.getSource()) {
            case CURRICULUM -> new Explanation(PlacementReason.CURRICULUM, null, null, null, null, item.getAddedAt());
            // Which class asked for it, and for which skill (X10); older rows without them read as before.
            case TEACHER_ASSIGNED -> new Explanation(PlacementReason.TEACHER_ASSIGNED, lookups.skill(item.getAssignedSkillId()), null,
                    null, null, item.getAddedAt(), lookups.classLabel(item.getAssignedClassId()));
            case ADAPTIVE -> lookups.decisionPlacing(item.getId())
                    .map(decision -> adaptiveExplanation(decision, lookups))
                    .orElseGet(() -> unexplained(item));
            case REVIEW -> lookups.skillsOf(item.getActivityVersionId()).stream()
                    .filter(skill -> reviewItemRepository.findByChildIdAndSkillId(lookups.childId(), skill.id()).isPresent())
                    .findFirst()
                    .map(skill -> new Explanation(PlacementReason.REVIEW, skill, null, null, null, item.getAddedAt()))
                    .orElseGet(() -> unexplained(item));
        };
    }

    private static Explanation unexplained(LearningPathItem item) {
        return new Explanation(PlacementReason.UNEXPLAINED, null, null, null, null, item.getAddedAt());
    }

    private Explanation adaptiveExplanation(AdaptationDecision decision, Lookups lookups) {
        SkillRef governing = lookups.skill(contextId(decision, "governingSkillId"));
        SkillRef target = lookups.skill(contextId(decision, "targetSkillId"));
        PlacementReason reason = switch (decision.getDecisionType()) {
            case REMEDIATE -> governing != null && target != null && !governing.id().equals(target.id())
                    ? PlacementReason.REINFORCE_PREREQUISITE
                    : PlacementReason.REINFORCE_SKILL;
            case INCREASE_DIFFICULTY -> PlacementReason.CHALLENGE;
            default -> PlacementReason.UNEXPLAINED;
        };
        ActivityAttempt trigger = lookups.attempt(decision.getActivityAttemptId());
        String afterTitle = trigger == null ? null
                : lookups.activity(lookups.version(trigger.getActivityVersionId()).getActivityId()).getTitle();
        return new Explanation(reason, governing, reason == PlacementReason.REINFORCE_PREREQUISITE ? target : null,
                afterTitle, trigger == null ? null : trigger.getScore(), decision.getOccurredAt());
    }

    private CompletedActivity completedActivity(ActivityAttempt attempt, Lookups lookups, List<AnswerRecord> answers,
                                                LearningPathItemSource source) {
        ActivityVersion version = lookups.version(attempt.getActivityVersionId());
        Activity activity = lookups.activity(version.getActivityId());
        int correct = (int) answers.stream().filter(AnswerRecord::isCorrect).count();

        DecisionSummary decision = lookups.decisionOf(attempt.getId())
                .map(recorded -> new DecisionSummary(recorded.getDecisionType(),
                        lookups.skill(contextId(recorded, "governingSkillId")),
                        lookups.skill(contextId(recorded, "targetSkillId")),
                        recorded.getSelectedActivityVersionId() == null ? null
                                : lookups.activity(lookups.version(recorded.getSelectedActivityVersionId())
                                        .getActivityId()).getTitle()))
                .orElse(null);

        String subjectName = lookups.subjectName(activity.getSubjectId());
        return new CompletedActivity(attempt.getId(), version.getId(), titleFor(activity, version), subjectName, source,
                attempt.getScore(), correct, answers.size(), attempt.getStartedAt(), attempt.getCompletedAt(),
                lookups.skillsOf(version.getId()), decision, (int) answers.stream().filter(AnswerRecord::isHintUsed).count());
    }

    /** Null for a class's (teacher-owned) path, whose fit is the school's to judge, or when the child's age is unknown. */
    private AgeFit ageFit(UUID curriculumVersionId, Integer childAge, java.util.Set<UUID> onCurricula) {
        Curriculum curriculum = curriculumService.getCurriculum(curriculumService.getVersion(curriculumVersionId).getCurriculumId());
        if (curriculum.getOwnerAccountId() != null || childAge == null) {
            return null;
        }
        AgeHub hub = ageHubRepository.findById(curriculum.getAgeHubId()).orElse(null);
        if (hub == null) {
            return null;
        }
        java.util.Optional<AgeHub> childHub = ageHubRepository.findForAge(childAge);
        boolean fits = childHub.map(h -> h.getId().equals(hub.getId())).orElse(false);
        Curriculum suggested = fits || childHub.isEmpty() ? null : curriculumService.listActiveForAgeHub(childHub.get().getId()).stream()
                .filter(other -> other.getOwnerAccountId() == null && other.getSubjectId().equals(curriculum.getSubjectId()))
                .filter(other -> !onCurricula.contains(other.getId()) && curriculumService.hasPublishedVersion(other.getId()))
                .min(Comparator.comparing(Curriculum::getName, String.CASE_INSENSITIVE_ORDER))
                .orElse(null);
        return new AgeFit(fits, childAge, hub.getMinAge(), hub.getMaxAge(), suggested == null ? null : suggested.getId(),
                suggested == null ? null : suggested.getName());
    }

    @Transactional(readOnly = true)
    public CurriculumRef curriculumFor(UUID curriculumVersionId) {
        Curriculum curriculum = curriculumService.getCurriculum(
                curriculumService.getVersion(curriculumVersionId).getCurriculumId());
        Subject subject = subjectRepository.findById(curriculum.getSubjectId()).orElse(null);
        AgeHub ageHub = ageHubRepository.findById(curriculum.getAgeHubId()).orElse(null);
        return new CurriculumRef(curriculum.getId(), catalogNames.name(com.platform.content.entity.LocalizableContentType.CURRICULUM, curriculum.getId(), curriculum.getName()),
                subject == null ? null : subject.getCode(),
                subject == null ? null : catalogNames.name(com.platform.content.entity.LocalizableContentType.SUBJECT, subject.getId(), subject.getName()),
                ageHub == null ? null : ageHub.getName());
    }

    private static UUID contextId(AdaptationDecision decision, String key) {
        Object value = decision.getContext().get(key);
        return value == null ? null : UUID.fromString(value.toString());
    }

    /** Per-request caches, so a journey of N steps doesn't re-read the same rows N times. */
    private final class Lookups {
        private final UUID childId;
        /** Completed attempts per path, loaded once per path (not the child's whole history). */
        private final Map<UUID, List<ActivityAttempt>> completedByPath = new HashMap<>();
        private final Map<UUID, Optional<ActivityAttempt>> attemptsById = new HashMap<>();
        private Map<UUID, AdaptationDecision> decisionsByPlacedItem;
        private final Map<UUID, ActivityVersion> versions = new HashMap<>();
        private final Map<UUID, Activity> activities = new HashMap<>();
        /** What each activity version practises: its own tags (PD-08), not whatever its activity has now. */
        private final Map<UUID, List<SkillRef>> skillsByVersion = new HashMap<>();
        private final Map<UUID, SkillRef> skills = new HashMap<>();
        private final Map<UUID, Integer> questionCounts = new HashMap<>();
        private final Map<UUID, Optional<String>> classLabels = new HashMap<>();

        Lookups(UUID childId) {
            this.childId = childId;
        }

        UUID childId() {
            return childId;
        }

        List<ActivityAttempt> completedOnPath(UUID learningPathId) {
            return completedByPath.computeIfAbsent(learningPathId, activityAttemptRepository::findCompletedOnPath);
        }

        /** An attempt of this child (a decision's trigger); another child's id reads as none. */
        ActivityAttempt attempt(UUID attemptId) {
            if (attemptId == null) {
                return null;
            }
            return attemptsById.computeIfAbsent(attemptId, id -> activityAttemptRepository.findById(id)
                    .filter(attempt -> childId.equals(attempt.getChildId()))).orElse(null);
        }

        /**
         * Loads the versions, activities, their skills and the skills themselves for a whole
         * page at once (plus the activities the page's recorded decisions selected), so a long
         * history costs a fixed number of queries.
         */
        void preload(List<UUID> activityVersionIds) {
            java.util.Set<UUID> versionIds = new java.util.HashSet<>(activityVersionIds);
            for (AdaptationDecision decision : decisions()) {
                if (decision.getSelectedActivityVersionId() != null) {
                    versionIds.add(decision.getSelectedActivityVersionId());
                }
            }
            versionIds.removeAll(versions.keySet());
            if (!versionIds.isEmpty()) {
                activityVersionRepository.findAllById(versionIds).forEach(v -> versions.put(v.getId(), v));
            }
            java.util.Set<UUID> activityIds = new java.util.HashSet<>();
            versions.values().forEach(v -> activityIds.add(v.getActivityId()));
            activityIds.removeAll(activities.keySet());
            if (!activityIds.isEmpty()) {
                activityRepository.findAllById(activityIds).forEach(a -> activities.put(a.getId(), a));
            }
            java.util.Set<UUID> untagged = new java.util.HashSet<>(versions.keySet());
            untagged.removeAll(skillsByVersion.keySet());
            if (untagged.isEmpty()) {
                return;
            }
            Map<UUID, List<UUID>> skillIdsByVersion = new HashMap<>();
            for (ActivityVersionSkill link : activitySkillRepository.findByActivityVersionIdIn(untagged)) {
                skillIdsByVersion.computeIfAbsent(link.getActivityVersionId(), key -> new java.util.ArrayList<>()).add(link.getSkillId());
            }
            java.util.Set<UUID> skillIds = new java.util.HashSet<>();
            skillIdsByVersion.values().forEach(skillIds::addAll);
            for (AdaptationDecision decision : decisions()) {
                for (String key : List.of("governingSkillId", "targetSkillId")) {
                    UUID id = contextId(decision, key);
                    if (id != null) {
                        skillIds.add(id);
                    }
                }
            }
            loadSkills(skillIds);
            for (UUID versionId : untagged) {
                skillsByVersion.put(versionId, skillIdsByVersion.getOrDefault(versionId, List.of()).stream()
                        .map(this::skill).filter(java.util.Objects::nonNull)
                        .sorted(Comparator.comparing(SkillRef::name)).toList());
            }
        }

        private Map<UUID, AdaptationDecision> decisionsByAttempt;
        private final Map<UUID, String> subjectNames = new HashMap<>();

        /** The decision recorded when this attempt was completed (at most one per attempt, V36). */
        Optional<AdaptationDecision> decisionOf(UUID attemptId) {
            if (decisionsByAttempt == null) {
                decisionsByAttempt = new HashMap<>();
                for (AdaptationDecision decision : decisions()) {
                    if (decision.getActivityAttemptId() != null) {
                        decisionsByAttempt.putIfAbsent(decision.getActivityAttemptId(), decision);
                    }
                }
            }
            return Optional.ofNullable(decisionsByAttempt.get(attemptId));
        }

        String subjectName(UUID subjectId) {
            return subjectNames.computeIfAbsent(subjectId,
                    key -> subjectRepository.findById(key).map(Subject::getName).orElse(null));
        }

        private List<AdaptationDecision> decisions;

        private List<AdaptationDecision> decisions() {
            if (decisions == null) {
                decisions = adaptationDecisionRepository.findByChildIdOrderByOccurredAtAsc(childId);
            }
            return decisions;
        }

        /** The earliest decision that placed this item (a later decision may reuse a waiting placement). */
        Optional<AdaptationDecision> decisionPlacing(UUID learningPathItemId) {
            if (decisionsByPlacedItem == null) {
                decisionsByPlacedItem = new LinkedHashMap<>();
                for (AdaptationDecision decision : decisions()) {
                    if (decision.getLearningPathItemId() != null) {
                        decisionsByPlacedItem.putIfAbsent(decision.getLearningPathItemId(), decision);
                    }
                }
            }
            return Optional.ofNullable(decisionsByPlacedItem.get(learningPathItemId));
        }

        ActivityVersion version(UUID id) {
            return versions.computeIfAbsent(id, key -> activityVersionRepository.findById(key)
                    .orElseThrow(() -> new ResourceNotFoundException("ActivityVersion", key)));
        }

        Activity activity(UUID id) {
            return activities.computeIfAbsent(id, key -> activityRepository.findById(key)
                    .orElseThrow(() -> new ResourceNotFoundException("Activity", key)));
        }

        List<SkillRef> skillsOf(UUID activityVersionId) {
            return skillsByVersion.computeIfAbsent(activityVersionId, key -> {
                List<UUID> skillIds = activitySkillRepository.findByActivityVersionIdOrderByCreatedAtAscIdAsc(key).stream()
                        .map(ActivityVersionSkill::getSkillId)
                        .toList();
                loadSkills(skillIds);
                return skillIds.stream()
                        .map(this::skill)
                        .filter(java.util.Objects::nonNull)
                        .sorted(Comparator.comparing(SkillRef::name))
                        .toList();
            });
        }

        /** The skills not read yet, and their names in the reader's language, in one query each - not one per skill. */
        private void loadSkills(java.util.Collection<UUID> skillIds) {
            List<UUID> missing = skillIds.stream().filter(id -> !skills.containsKey(id)).distinct().toList();
            if (missing.isEmpty()) {
                return;
            }
            catalogNames.prefetch(com.platform.content.entity.LocalizableContentType.SKILL, missing);
            skillRepository.findAllById(missing).forEach(skill -> skills.put(skill.getId(), new SkillRef(skill.getId(),
                    catalogNames.name(com.platform.content.entity.LocalizableContentType.SKILL, skill.getId(), skill.getName()))));
        }

        SkillRef skill(UUID skillId) {
            if (skillId == null) {
                return null;
            }
            return skills.computeIfAbsent(skillId, key -> skillRepository.findById(key)
                    .map((Skill skill) -> new SkillRef(skill.getId(), catalogNames.name(com.platform.content.entity.LocalizableContentType.SKILL, skill.getId(), skill.getName())))
                    .orElse(null));
        }

        /** Once per class that placed practice on this path, however many steps it placed. */
        String classLabel(UUID classId) {
            return classId == null ? null : classLabels.computeIfAbsent(classId, placementNames::classLabel).orElse(null);
        }

        int questionCount(UUID activityVersionId) {
            return questionCounts.computeIfAbsent(activityVersionId,
                    key -> activityItemRepository.findByActivityVersionIdOrderBySequenceOrderAsc(key).size());
        }
    }

}
