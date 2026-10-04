package com.platform.learning.service;

import com.platform.curriculum.entity.CurriculumObjective;
import com.platform.curriculum.entity.LearningObjective;
import com.platform.curriculum.service.CurriculumService;
import com.platform.curriculum.service.LearningObjectiveService;
import com.platform.knowledge.entity.LearningObjectiveSkill;
import com.platform.knowledge.entity.Skill;
import com.platform.knowledge.repository.LearningObjectiveSkillRepository;
import com.platform.knowledge.repository.SkillRepository;
import com.platform.learner.entity.LearnerObjectiveState;
import com.platform.learner.entity.LearnerObjectiveStatus;
import com.platform.learner.entity.LearnerSkillState;
import com.platform.learner.repository.LearnerObjectiveStateRepository;
import com.platform.learner.repository.LearnerSkillStateRepository;
import com.platform.learner.service.EvidenceStandard;
import com.platform.learner.service.EvidenceStandard.EvidenceLevel;
import com.platform.learner.service.EvidenceStandard.SkillStanding;
import com.platform.learning.entity.Enrollment;
import com.platform.learning.repository.EnrollmentRepository;
import com.platform.review.entity.ReviewItem;
import com.platform.review.entity.ReviewItemStatus;
import com.platform.review.entity.ReviewSchedule;
import com.platform.review.repository.ReviewItemRepository;
import com.platform.review.repository.ReviewScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What a child is learning and what the evidence says about it, per curriculum the
 * child is enrolled in: objectives in curriculum order, each with its skills.
 *
 * <p>Deliberately carries no raw mastery number (Section 21: explain the data, don't
 * expose mastery percentages). Each skill reports its evidence level and the
 * standing that evidence supports ({@link EvidenceStandard}), plus the plain facts
 * behind it - how many scored answers, how many correct, when last practised. An
 * objective is only SECURE when its rollup is MASTERED <em>and</em> every skill in it
 * has sufficient evidence - the same bar Parent Insight applies before saying
 * "mastered" (Battle 2C).
 */
@Service
public class SkillProgressService {

    private final EnrollmentRepository enrollmentRepository;
    private final CurriculumService curriculumService;
    private final LearningObjectiveService learningObjectiveService;
    private final LearningObjectiveSkillRepository learningObjectiveSkillRepository;
    private final SkillRepository skillRepository;
    private final LearnerSkillStateRepository skillStateRepository;
    private final LearnerObjectiveStateRepository objectiveStateRepository;
    private final ReviewItemRepository reviewItemRepository;
    private final ReviewScheduleRepository reviewScheduleRepository;
    private final LearningJourneyService learningJourneyService;
    private final EvidenceStandard evidenceStandard;

    public SkillProgressService(EnrollmentRepository enrollmentRepository, CurriculumService curriculumService,
                                LearningObjectiveService learningObjectiveService,
                                LearningObjectiveSkillRepository learningObjectiveSkillRepository,
                                SkillRepository skillRepository, LearnerSkillStateRepository skillStateRepository,
                                LearnerObjectiveStateRepository objectiveStateRepository,
                                ReviewItemRepository reviewItemRepository,
                                ReviewScheduleRepository reviewScheduleRepository,
                                LearningJourneyService learningJourneyService, EvidenceStandard evidenceStandard) {
        this.enrollmentRepository = enrollmentRepository;
        this.curriculumService = curriculumService;
        this.learningObjectiveService = learningObjectiveService;
        this.learningObjectiveSkillRepository = learningObjectiveSkillRepository;
        this.skillRepository = skillRepository;
        this.skillStateRepository = skillStateRepository;
        this.objectiveStateRepository = objectiveStateRepository;
        this.reviewItemRepository = reviewItemRepository;
        this.reviewScheduleRepository = reviewScheduleRepository;
        this.learningJourneyService = learningJourneyService;
        this.evidenceStandard = evidenceStandard;
    }

    public enum ObjectiveStanding {
        NOT_STARTED,
        IN_PROGRESS,
        SECURE
    }

    /** {@code reviewDueAt} is set while Smart Review has the skill flagged. */
    public record SkillProgress(UUID skillId, String name, String description, EvidenceLevel evidenceLevel,
                                SkillStanding standing, int scoredAnswers, int correctAnswers,
                                Instant lastPracticedAt, boolean inReview, Instant reviewDueAt) {
    }

    public record ObjectiveProgress(UUID objectiveId, String title, String description, ObjectiveStanding standing,
                                    int skillsMapped, int skillsPractised, int skillsWithSufficientEvidence,
                                    List<SkillProgress> skills) {
    }

    public record CurriculumProgress(UUID enrollmentId, LearningJourneyService.CurriculumRef curriculum,
                                     List<ObjectiveProgress> objectives) {
    }

    @Transactional(readOnly = true)
    public List<CurriculumProgress> progressFor(UUID childId) {
        return progressForAll(List.of(childId)).getOrDefault(childId, List.of());
    }

    /**
     * Several children at once - a class (X20). The same reading as {@link #progressFor}, with every
     * child's states read together and each curriculum version's chapters and skills read once, so
     * the statements depend on the curricula, not on how many children there are.
     */
    @Transactional(readOnly = true)
    public Map<UUID, List<CurriculumProgress>> progressForAll(Collection<UUID> childIds) {
        Map<UUID, List<CurriculumProgress>> result = new LinkedHashMap<>();
        if (childIds.isEmpty()) {
            return result;
        }
        Map<UUID, Map<UUID, LearnerSkillState>> skillStates = new HashMap<>();
        skillStateRepository.findByChildIdIn(childIds).forEach(state ->
                skillStates.computeIfAbsent(state.getChildId(), id -> new HashMap<>()).put(state.getSkillId(), state));
        Map<UUID, Map<UUID, LearnerObjectiveState>> objectiveStates = new HashMap<>();
        objectiveStateRepository.findByChildIdIn(childIds).forEach(state ->
                objectiveStates.computeIfAbsent(state.getChildId(), id -> new HashMap<>()).put(state.getLearningObjectiveId(), state));
        List<ReviewItem> reviews = reviewItemRepository.findByChildIdInAndStatus(childIds, ReviewItemStatus.ACTIVE);
        Map<UUID, Map<UUID, ReviewItem>> activeReviews = new HashMap<>();
        reviews.forEach(item -> activeReviews.computeIfAbsent(item.getChildId(), id -> new HashMap<>()).put(item.getSkillId(), item));
        Map<UUID, Instant> dueByReview = new HashMap<>();
        if (!reviews.isEmpty()) {
            reviewScheduleRepository.findByReviewItemIdIn(reviews.stream().map(ReviewItem::getId).toList())
                    .forEach(schedule -> dueByReview.put(schedule.getReviewItemId(), schedule.getDueAt()));
        }
        Map<UUID, List<Enrollment>> enrollments = enrollmentRepository.findByChildIdIn(childIds).stream()
                .collect(Collectors.groupingBy(Enrollment::getChildId));

        Map<UUID, CurriculumStructure> structures = new HashMap<>();
        Map<UUID, ObjectiveStructure> objectives = new HashMap<>();
        for (UUID childId : childIds) {
            Map<UUID, LearnerSkillState> childSkills = skillStates.getOrDefault(childId, Map.of());
            Map<UUID, LearnerObjectiveState> childObjectives = objectiveStates.getOrDefault(childId, Map.of());
            Map<UUID, ReviewItem> childReviews = activeReviews.getOrDefault(childId, Map.of());
            result.put(childId, enrollments.getOrDefault(childId, List.of()).stream()
                    .sorted(Comparator.comparing(Enrollment::getEnrolledAt))
                    .map(enrollment -> {
                        CurriculumStructure structure = structures.computeIfAbsent(enrollment.getCurriculumVersionId(),
                                versionId -> structure(versionId, objectives));
                        return new CurriculumProgress(enrollment.getId(), structure.curriculum(), structure.objectives().stream()
                                .map(objective -> objectiveProgress(objective, childSkills, childObjectives, childReviews, dueByReview))
                                .toList());
                    })
                    .toList());
        }
        return result;
    }

    /** One objective as a curriculum version lists it: its skills in their linked order. */
    private record ObjectiveStructure(LearningObjective objective, List<Skill> skills) {
    }

    private record CurriculumStructure(LearningJourneyService.CurriculumRef curriculum, List<ObjectiveStructure> objectives) {
    }

    private CurriculumStructure structure(UUID curriculumVersionId, Map<UUID, ObjectiveStructure> objectives) {
        List<ObjectiveStructure> ordered = curriculumService.listObjectives(curriculumVersionId).stream()
                .sorted(Comparator.comparingInt(CurriculumObjective::getDisplayOrder))
                .map(link -> objectives.computeIfAbsent(link.getLearningObjectiveId(), this::objectiveStructure))
                .toList();
        return new CurriculumStructure(learningJourneyService.curriculumFor(curriculumVersionId), ordered);
    }

    private ObjectiveStructure objectiveStructure(UUID objectiveId) {
        LearningObjective objective = learningObjectiveService.getById(objectiveId);
        List<UUID> skillIds = learningObjectiveSkillRepository.findByLearningObjectiveId(objectiveId).stream()
                .map(LearningObjectiveSkill::getSkillId)
                .toList();
        Map<UUID, Skill> byId = skillRepository.findAllById(skillIds).stream().collect(Collectors.toMap(Skill::getId, Function.identity()));
        return new ObjectiveStructure(objective, skillIds.stream().map(byId::get).filter(java.util.Objects::nonNull).toList());
    }

    private ObjectiveProgress objectiveProgress(ObjectiveStructure structure, Map<UUID, LearnerSkillState> skillStates,
                                                Map<UUID, LearnerObjectiveState> objectiveStates,
                                                Map<UUID, ReviewItem> activeReviews, Map<UUID, Instant> dueByReview) {
        LearningObjective objective = structure.objective();
        List<SkillProgress> skills = structure.skills().stream()
                .map(skill -> skillProgress(skill, skillStates.get(skill.getId()), activeReviews.get(skill.getId()), dueByReview))
                .toList();

        int practised = (int) skills.stream().filter(skill -> skill.evidenceLevel() != EvidenceLevel.NONE).count();
        int sufficient = (int) skills.stream()
                .filter(skill -> skill.evidenceLevel() == EvidenceLevel.SUFFICIENT || skill.evidenceLevel() == EvidenceLevel.STRONG)
                .count();
        LearnerObjectiveState state = objectiveStates.get(objective.getId());
        ObjectiveStanding standing = practised == 0 ? ObjectiveStanding.NOT_STARTED
                : state != null && state.getStatus() == LearnerObjectiveStatus.MASTERED && sufficient == skills.size()
                        ? ObjectiveStanding.SECURE
                        : ObjectiveStanding.IN_PROGRESS;

        return new ObjectiveProgress(objective.getId(), objective.getTitle(), objective.getDescription(), standing,
                skills.size(), practised, sufficient, skills);
    }

    private SkillProgress skillProgress(Skill skill, LearnerSkillState state, ReviewItem review, Map<UUID, Instant> dueByReview) {
        int evidence = state == null ? 0 : state.getEvidenceCount();
        Instant reviewDueAt = review == null ? null : dueByReview.get(review.getId());
        return new SkillProgress(skill.getId(), skill.getName(), skill.getDescription(),
                evidenceStandard.levelFor(evidence), evidenceStandard.standingFor(state), evidence,
                state == null ? 0 : state.getCorrectCount(), state == null ? null : state.getLastEvidenceAt(),
                review != null, reviewDueAt);
    }

}
