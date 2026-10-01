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
import com.platform.learning.entity.ActivityAttemptStatus;
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
                                  LearningObjectiveSkillService learningObjectiveSkillService, PlacementNames placementNames) {
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
    public record EnrollmentOverview(UUID enrollmentId, CurriculumRef curriculum, EnrollmentStatus status,
                                     Instant enrolledAt, int completedSteps, int totalSteps, JourneyStep nextStep,
                                     Chapter currentChapter, int chapterCount, List<Chapter> chapters) {
    }

    /** What the platform decided after a completed activity, as recorded. */
    public record DecisionSummary(AdaptationDecisionType type, SkillRef governingSkill, SkillRef targetSkill,
                                  String placedActivityTitle) {
    }

    public record CompletedActivity(UUID attemptId, UUID activityVersionId, String title, String subjectName,
                                    LearningPathItemSource source, BigDecimal score, int correctAnswers,
                                    int totalAnswers, Instant startedAt, Instant completedAt, List<SkillRef> skills,
                                    DecisionSummary decision) {
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public List<EnrollmentOverview> enrollmentsFor(UUID childId) {
        Lookups lookups = new Lookups(childId);
        return enrollmentRepository.findByChildId(childId).stream()
                .sorted(Comparator.comparing(Enrollment::getEnrolledAt))
                .map(enrollment -> {
                    Journey journey = journey(enrollment, lookups);
                    JourneyStep next = journey.steps().stream()
                            .filter(step -> step.learningPathItemId().equals(journey.nextStepId()))
                            .findFirst().orElse(null);
                    Chapter current = journey.chapters().stream()
                            .filter(chapter -> chapter.state() == ChapterState.CURRENT).findFirst().orElse(null);
                    return new EnrollmentOverview(enrollment.getId(), journey.curriculum(), enrollment.getStatus(),
                            enrollment.getEnrolledAt(), journey.completedSteps(), journey.totalSteps(), next,
                            current, journey.chapters().size(), journey.chapters());
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
                chapters(chapterSources, steps, nextStepId), steps);
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
            sources.add(new ChapterSource(objective.getId(), position++, objective.getTitle(), objective.getDescription(), skills));
        }
        return sources;
    }

    /** The first chapter, in curriculum order, that covers one of the step's skills. */
    private static UUID chapterOf(List<SkillRef> skills, List<ChapterSource> chapters) {
        for (ChapterSource chapter : chapters) {
            for (SkillRef skill : skills) {
                if (chapter.skillIds().contains(skill.id())) {
                    return chapter.id();
                }
            }
        }
        return null;
    }

    private static List<Chapter> chapters(List<ChapterSource> sources, List<JourneyStep> steps, UUID nextStepId) {
        UUID currentChapter = steps.stream().filter(step -> step.learningPathItemId().equals(nextStepId))
                .findFirst().map(JourneyStep::chapterId).orElse(null);
        return sources.stream().map(source -> {
            List<JourneyStep> own = steps.stream().filter(step -> source.id().equals(step.chapterId())).toList();
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
        Optional<ActivityAttempt> completedAttempt = lookups.attempts().stream()
                .filter(attempt -> item.getId().equals(attempt.getLearningPathItemId()))
                .filter(attempt -> attempt.getStatus() == ActivityAttemptStatus.COMPLETED)
                .max(Comparator.comparing(ActivityAttempt::getCompletedAt));

        List<SkillRef> skills = lookups.skillsOf(version.getId());
        return new JourneyStep(item.getId(), version.getId(), activity.getTitle(), activity.getActivityType(),
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
        return new CompletedActivity(attempt.getId(), version.getId(), activity.getTitle(), subjectName, source,
                attempt.getScore(), correct, answers.size(), attempt.getStartedAt(), attempt.getCompletedAt(),
                lookups.skillsOf(version.getId()), decision);
    }

    @Transactional(readOnly = true)
    public CurriculumRef curriculumFor(UUID curriculumVersionId) {
        Curriculum curriculum = curriculumService.getCurriculum(
                curriculumService.getVersion(curriculumVersionId).getCurriculumId());
        Subject subject = subjectRepository.findById(curriculum.getSubjectId()).orElse(null);
        AgeHub ageHub = ageHubRepository.findById(curriculum.getAgeHubId()).orElse(null);
        return new CurriculumRef(curriculum.getId(), curriculum.getName(),
                subject == null ? null : subject.getCode(), subject == null ? null : subject.getName(),
                ageHub == null ? null : ageHub.getName());
    }

    private static UUID contextId(AdaptationDecision decision, String key) {
        Object value = decision.getContext().get(key);
        return value == null ? null : UUID.fromString(value.toString());
    }

    /** Per-request caches, so a journey of N steps doesn't re-read the same rows N times. */
    private final class Lookups {
        private final UUID childId;
        private List<ActivityAttempt> attempts;
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

        List<ActivityAttempt> attempts() {
            if (attempts == null) {
                attempts = activityAttemptRepository.findByChildIdOrderByStartedAtDesc(childId);
            }
            return attempts;
        }

        ActivityAttempt attempt(UUID attemptId) {
            return attempts().stream().filter(attempt -> attempt.getId().equals(attemptId)).findFirst().orElse(null);
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
            skillIds.removeAll(skills.keySet());
            if (!skillIds.isEmpty()) {
                skillRepository.findAllById(skillIds).forEach(skill -> skills.put(skill.getId(), new SkillRef(skill.getId(), skill.getName())));
            }
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
            return skillsByVersion.computeIfAbsent(activityVersionId, key -> activitySkillRepository
                    .findByActivityVersionIdOrderByCreatedAtAscIdAsc(key).stream()
                    .map(ActivityVersionSkill::getSkillId)
                    .map(this::skill)
                    .filter(java.util.Objects::nonNull)
                    .sorted(Comparator.comparing(SkillRef::name))
                    .toList());
        }

        SkillRef skill(UUID skillId) {
            if (skillId == null) {
                return null;
            }
            return skills.computeIfAbsent(skillId, key -> skillRepository.findById(key)
                    .map((Skill skill) -> new SkillRef(skill.getId(), skill.getName()))
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
