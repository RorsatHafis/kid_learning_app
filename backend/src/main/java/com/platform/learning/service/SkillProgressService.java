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
import java.util.Comparator;
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
        Map<UUID, LearnerSkillState> skillStates = skillStateRepository.findByChildId(childId).stream()
                .collect(Collectors.toMap(LearnerSkillState::getSkillId, Function.identity()));
        Map<UUID, LearnerObjectiveState> objectiveStates = objectiveStateRepository.findByChildId(childId).stream()
                .collect(Collectors.toMap(LearnerObjectiveState::getLearningObjectiveId, Function.identity()));
        Map<UUID, ReviewItem> activeReviews = reviewItemRepository.findByChildIdAndStatus(childId, ReviewItemStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(ReviewItem::getSkillId, Function.identity()));
        return enrollmentRepository.findByChildId(childId).stream()
                .sorted(Comparator.comparing(Enrollment::getEnrolledAt))
                .map(enrollment -> new CurriculumProgress(enrollment.getId(),
                        learningJourneyService.curriculumFor(enrollment.getCurriculumVersionId()),
                        curriculumService.listObjectives(enrollment.getCurriculumVersionId()).stream()
                                .sorted(Comparator.comparingInt(CurriculumObjective::getDisplayOrder))
                                .map(objective -> objectiveProgress(objective.getLearningObjectiveId(), skillStates,
                                        objectiveStates, activeReviews))
                                .toList()))
                .toList();
    }

    private ObjectiveProgress objectiveProgress(UUID objectiveId, Map<UUID, LearnerSkillState> skillStates,
                                                Map<UUID, LearnerObjectiveState> objectiveStates,
                                                Map<UUID, ReviewItem> activeReviews) {
        LearningObjective objective = learningObjectiveService.getById(objectiveId);
        List<SkillProgress> skills = learningObjectiveSkillRepository.findByLearningObjectiveId(objectiveId).stream()
                .map(LearningObjectiveSkill::getSkillId)
                .map(skillRepository::findById)
                .flatMap(Optional::stream)
                .map(skill -> skillProgress(skill, skillStates.get(skill.getId()), activeReviews.get(skill.getId())))
                .toList();

        int practised = (int) skills.stream().filter(skill -> skill.evidenceLevel() != EvidenceLevel.NONE).count();
        int sufficient = (int) skills.stream()
                .filter(skill -> skill.evidenceLevel() == EvidenceLevel.SUFFICIENT || skill.evidenceLevel() == EvidenceLevel.STRONG)
                .count();
        LearnerObjectiveState state = objectiveStates.get(objectiveId);
        ObjectiveStanding standing = practised == 0 ? ObjectiveStanding.NOT_STARTED
                : state != null && state.getStatus() == LearnerObjectiveStatus.MASTERED && sufficient == skills.size()
                        ? ObjectiveStanding.SECURE
                        : ObjectiveStanding.IN_PROGRESS;

        return new ObjectiveProgress(objectiveId, objective.getTitle(), objective.getDescription(), standing,
                skills.size(), practised, sufficient, skills);
    }

    private SkillProgress skillProgress(Skill skill, LearnerSkillState state, ReviewItem review) {
        int evidence = state == null ? 0 : state.getEvidenceCount();
        Instant reviewDueAt = review == null ? null
                : reviewScheduleRepository.findByReviewItemId(review.getId()).map(ReviewSchedule::getDueAt).orElse(null);
        return new SkillProgress(skill.getId(), skill.getName(), skill.getDescription(),
                evidenceStandard.levelFor(evidence), evidenceStandard.standingFor(state), evidence,
                state == null ? 0 : state.getCorrectCount(), state == null ? null : state.getLastEvidenceAt(),
                review != null, reviewDueAt);
    }

}
