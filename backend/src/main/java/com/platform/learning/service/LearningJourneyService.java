package com.platform.learning.service;

import com.platform.common.web.ResourceNotFoundException;
import com.platform.content.entity.Activity;
import com.platform.content.entity.ActivitySkill;
import com.platform.content.entity.ActivityVersion;
import com.platform.content.repository.ActivityItemRepository;
import com.platform.content.repository.ActivityRepository;
import com.platform.content.repository.ActivitySkillRepository;
import com.platform.content.repository.ActivityVersionRepository;
import com.platform.curriculum.entity.AgeHub;
import com.platform.curriculum.entity.Curriculum;
import com.platform.curriculum.entity.Subject;
import com.platform.curriculum.repository.AgeHubRepository;
import com.platform.curriculum.repository.SubjectRepository;
import com.platform.curriculum.service.CurriculumService;
import com.platform.knowledge.entity.Skill;
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
    private final ActivitySkillRepository activitySkillRepository;
    private final ActivityItemRepository activityItemRepository;
    private final SkillRepository skillRepository;
    private final AdaptationDecisionRepository adaptationDecisionRepository;
    private final CurriculumService curriculumService;
    private final SubjectRepository subjectRepository;
    private final AgeHubRepository ageHubRepository;
    private final com.platform.review.repository.ReviewItemRepository reviewItemRepository;

    public LearningJourneyService(EnrollmentRepository enrollmentRepository, LearningPathRepository learningPathRepository,
                                  LearningPathItemRepository learningPathItemRepository,
                                  ActivityAttemptRepository activityAttemptRepository,
                                  AnswerRecordRepository answerRecordRepository,
                                  ActivityVersionRepository activityVersionRepository,
                                  ActivityRepository activityRepository, ActivitySkillRepository activitySkillRepository,
                                  ActivityItemRepository activityItemRepository, SkillRepository skillRepository,
                                  AdaptationDecisionRepository adaptationDecisionRepository,
                                  CurriculumService curriculumService, SubjectRepository subjectRepository,
                                  AgeHubRepository ageHubRepository,
                                  com.platform.review.repository.ReviewItemRepository reviewItemRepository) {
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
    public record Explanation(PlacementReason reason, SkillRef skill, SkillRef targetSkill,
                              String afterActivityTitle, BigDecimal afterScore, Instant decidedAt) {
    }

    public record JourneyStep(UUID learningPathItemId, UUID activityVersionId, String title, String activityType,
                              Integer difficultyLevel, Integer estimatedDurationSeconds, int questionCount,
                              int sequenceOrder, LearningPathItemStatus status, LearningPathItemSource source,
                              List<SkillRef> skills, BigDecimal score, Instant completedAt, Explanation explanation) {
    }

    public record Journey(UUID enrollmentId, UUID childId, CurriculumRef curriculum, EnrollmentStatus status,
                          Instant enrolledAt, int completedSteps, int totalSteps, UUID nextStepId,
                          List<JourneyStep> steps) {
    }

    public record EnrollmentOverview(UUID enrollmentId, CurriculumRef curriculum, EnrollmentStatus status,
                                     Instant enrolledAt, int completedSteps, int totalSteps, JourneyStep nextStep) {
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
                    return new EnrollmentOverview(enrollment.getId(), journey.curriculum(), enrollment.getStatus(),
                            enrollment.getEnrolledAt(), journey.completedSteps(), journey.totalSteps(), next);
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
        Lookups lookups = new Lookups(childId);
        return lookups.attempts().stream()
                .filter(attempt -> attempt.getStatus() == ActivityAttemptStatus.COMPLETED)
                .sorted(Comparator.comparing(ActivityAttempt::getCompletedAt).reversed())
                .limit(limit)
                .map(attempt -> completedActivity(attempt, lookups))
                .toList();
    }

    // ------------------------------------------------------------------ assembly

    private Journey journey(Enrollment enrollment, Lookups lookups) {
        CurriculumRef curriculum = curriculumFor(enrollment.getCurriculumVersionId());
        LearningPath path = learningPathRepository.findByEnrollmentId(enrollment.getId())
                .orElseThrow(() -> new ResourceNotFoundException("LearningPath for enrollment", enrollment.getId()));

        List<JourneyStep> steps = learningPathItemRepository.findByLearningPathIdOrderBySequenceOrderAsc(path.getId())
                .stream()
                .filter(item -> item.getStatus() != LearningPathItemStatus.SKIPPED)
                .map(item -> step(item, lookups))
                .toList();

        int completed = (int) steps.stream().filter(step -> step.status() == LearningPathItemStatus.COMPLETED).count();
        // Same rule as LearningPathService#getNextItem: the first PENDING or IN_PROGRESS item.
        UUID nextStepId = steps.stream()
                .filter(step -> step.status() == LearningPathItemStatus.PENDING
                        || step.status() == LearningPathItemStatus.IN_PROGRESS)
                .map(JourneyStep::learningPathItemId)
                .findFirst().orElse(null);

        return new Journey(enrollment.getId(), enrollment.getChildId(), curriculum, enrollment.getStatus(),
                enrollment.getEnrolledAt(), completed, steps.size(), nextStepId, steps);
    }

    private JourneyStep step(LearningPathItem item, Lookups lookups) {
        ActivityVersion version = lookups.version(item.getActivityVersionId());
        Activity activity = lookups.activity(version.getActivityId());
        Optional<ActivityAttempt> completedAttempt = lookups.attempts().stream()
                .filter(attempt -> item.getId().equals(attempt.getLearningPathItemId()))
                .filter(attempt -> attempt.getStatus() == ActivityAttemptStatus.COMPLETED)
                .max(Comparator.comparing(ActivityAttempt::getCompletedAt));

        return new JourneyStep(item.getId(), version.getId(), activity.getTitle(), activity.getActivityType(),
                version.getDifficultyLevel(), version.getEstimatedDurationSeconds(),
                lookups.questionCount(version.getId()), item.getSequenceOrder(), item.getStatus(), item.getSource(),
                lookups.skillsOf(activity.getId()), completedAttempt.map(ActivityAttempt::getScore).orElse(null),
                item.getCompletedAt(), explain(item, activity, lookups));
    }

    private Explanation explain(LearningPathItem item, Activity activity, Lookups lookups) {
        return switch (item.getSource()) {
            case CURRICULUM -> new Explanation(PlacementReason.CURRICULUM, null, null, null, null, item.getAddedAt());
            case TEACHER_ASSIGNED -> new Explanation(PlacementReason.TEACHER_ASSIGNED, null, null, null, null,
                    item.getAddedAt());
            case ADAPTIVE -> lookups.decisionPlacing(item.getId())
                    .map(decision -> adaptiveExplanation(decision, lookups))
                    .orElseGet(() -> unexplained(item));
            case REVIEW -> lookups.skillsOf(activity.getId()).stream()
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

    private CompletedActivity completedActivity(ActivityAttempt attempt, Lookups lookups) {
        ActivityVersion version = lookups.version(attempt.getActivityVersionId());
        Activity activity = lookups.activity(version.getActivityId());
        List<AnswerRecord> answers = answerRecordRepository.findByActivityAttemptId(attempt.getId());
        int correct = (int) answers.stream().filter(AnswerRecord::isCorrect).count();
        LearningPathItemSource source = attempt.getLearningPathItemId() == null ? null
                : learningPathItemRepository.findById(attempt.getLearningPathItemId())
                        .map(LearningPathItem::getSource).orElse(null);

        DecisionSummary decision = adaptationDecisionRepository.findByActivityAttemptId(attempt.getId())
                .map(recorded -> new DecisionSummary(recorded.getDecisionType(),
                        lookups.skill(contextId(recorded, "governingSkillId")),
                        lookups.skill(contextId(recorded, "targetSkillId")),
                        recorded.getSelectedActivityVersionId() == null ? null
                                : lookups.activity(lookups.version(recorded.getSelectedActivityVersionId())
                                        .getActivityId()).getTitle()))
                .orElse(null);

        String subjectName = subjectRepository.findById(activity.getSubjectId()).map(Subject::getName).orElse(null);
        return new CompletedActivity(attempt.getId(), version.getId(), activity.getTitle(), subjectName, source,
                attempt.getScore(), correct, answers.size(), attempt.getStartedAt(), attempt.getCompletedAt(),
                lookups.skillsOf(activity.getId()), decision);
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
        private final Map<UUID, List<SkillRef>> skillsByActivity = new HashMap<>();
        private final Map<UUID, SkillRef> skills = new HashMap<>();
        private final Map<UUID, Integer> questionCounts = new HashMap<>();

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

        /** The earliest decision that placed this item (a later decision may reuse a waiting placement). */
        Optional<AdaptationDecision> decisionPlacing(UUID learningPathItemId) {
            if (decisionsByPlacedItem == null) {
                decisionsByPlacedItem = new LinkedHashMap<>();
                for (AdaptationDecision decision : adaptationDecisionRepository.findByChildIdOrderByOccurredAtAsc(childId)) {
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

        List<SkillRef> skillsOf(UUID activityId) {
            return skillsByActivity.computeIfAbsent(activityId, key -> activitySkillRepository.findByActivityId(key)
                    .stream()
                    .map(ActivitySkill::getSkillId)
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

        int questionCount(UUID activityVersionId) {
            return questionCounts.computeIfAbsent(activityVersionId,
                    key -> activityItemRepository.findByActivityVersionIdOrderBySequenceOrderAsc(key).size());
        }
    }

}
