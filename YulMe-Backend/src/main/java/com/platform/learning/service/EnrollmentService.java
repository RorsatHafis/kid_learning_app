package com.platform.learning.service;

import com.platform.child.repository.ChildRepository;
import com.platform.common.entity.PublicationStatus;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.content.entity.ActivitySkill;
import com.platform.content.entity.ActivityVersion;
import com.platform.content.repository.ActivitySkillRepository;
import com.platform.content.repository.ActivityVersionRepository;
import com.platform.curriculum.entity.CurriculumObjective;
import com.platform.curriculum.entity.CurriculumVersion;
import com.platform.curriculum.service.CurriculumService;
import com.platform.knowledge.entity.LearningObjectiveSkill;
import com.platform.knowledge.service.LearningObjectiveSkillService;
import com.platform.learning.entity.Enrollment;
import com.platform.learning.entity.LearningPath;
import com.platform.learning.entity.LearningPathItemSource;
import com.platform.learning.repository.EnrollmentRepository;
import com.platform.learning.repository.LearningPathRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Enrolls a child in a curriculum. A LearningPath is created atomically with the
 * Enrollment (one always implies the other for Phase 1 - see LearningPath's javadoc),
 * and is seeded with the curriculum's own activities as CURRICULUM-sourced items -
 * see {@link #seedInitialPath} - so {@link LearningPathService#getNextItem} has
 * something to return immediately after enrollment, without waiting for the
 * Adaptive Engine or a teacher to add anything.
 */
@Service
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final LearningPathRepository learningPathRepository;
    private final LearningPathService learningPathService;
    private final CurriculumService curriculumService;
    private final LearningObjectiveSkillService learningObjectiveSkillService;
    private final ActivitySkillRepository activitySkillRepository;
    private final ActivityVersionRepository activityVersionRepository;
    private final ChildRepository childRepository;

    public EnrollmentService(EnrollmentRepository enrollmentRepository, LearningPathRepository learningPathRepository,
                              LearningPathService learningPathService, CurriculumService curriculumService,
                              LearningObjectiveSkillService learningObjectiveSkillService,
                              ActivitySkillRepository activitySkillRepository,
                              ActivityVersionRepository activityVersionRepository, ChildRepository childRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.learningPathRepository = learningPathRepository;
        this.learningPathService = learningPathService;
        this.curriculumService = curriculumService;
        this.learningObjectiveSkillService = learningObjectiveSkillService;
        this.activitySkillRepository = activitySkillRepository;
        this.activityVersionRepository = activityVersionRepository;
        this.childRepository = childRepository;
    }

    /**
     * Enrolls in whichever CurriculumVersion is currently published (throws if
     * none is). Idempotent-friendly for the common case: if the child is already
     * enrolled in this exact version, returns the existing Enrollment rather than
     * erroring or duplicating - matching uq_enrollments_child_curriculum_version
     * (V14). Not wrapped in a full IdempotencyService guard the way answer
     * submission is: enrollment is comparatively rare and low-risk, so the small
     * remaining race window (two concurrent enroll() calls both passing the
     * check, one losing to the unique constraint and surfacing as a 409 the client
     * can safely retry) is an accepted, minor trade-off here.
     */
    @Transactional
    public Enrollment enroll(UUID childId, UUID curriculumId) {
        if (!childRepository.existsById(childId)) {
            throw new ResourceNotFoundException("Child", childId);
        }

        CurriculumVersion publishedVersion = curriculumService.getPublishedVersion(curriculumId);

        Optional<Enrollment> existing = enrollmentRepository.findByChildIdAndCurriculumVersionId(childId, publishedVersion.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        Enrollment enrollment = enrollmentRepository.save(Enrollment.enroll(childId, publishedVersion.getId(), Instant.now()));
        LearningPath learningPath = learningPathRepository.save(LearningPath.create(enrollment.getId()));
        seedInitialPath(learningPath.getId(), publishedVersion.getId());
        return enrollment;
    }

    /**
     * Walks the curriculum version's objectives in display order and, for every
     * skill each objective covers, appends every published ActivityVersion of
     * every Activity tagged with that skill (Content -&gt; Knowledge:
     * CurriculumObjective -&gt; LearningObjective -&gt;[LearningObjectiveSkill]-&gt; Skill
     * -&gt;[ActivitySkill]-&gt; Activity -&gt; published ActivityVersion). An Activity
     * reachable through more than one skill/objective is only placed once
     * (addedActivityIds). An Activity with no PUBLISHED version yet is silently
     * skipped rather than failing the whole enrollment - draft-only content simply
     * isn't placeable yet.
     */
    private void seedInitialPath(UUID learningPathId, UUID curriculumVersionId) {
        Set<UUID> addedActivityIds = new LinkedHashSet<>();

        for (CurriculumObjective objective : curriculumService.listObjectives(curriculumVersionId)) {
            for (LearningObjectiveSkill skillMapping
                    : learningObjectiveSkillService.listSkillsForObjective(objective.getLearningObjectiveId())) {

                for (ActivitySkill activitySkill : activitySkillRepository.findBySkillId(skillMapping.getSkillId())) {
                    UUID activityId = activitySkill.getActivityId();
                    if (!addedActivityIds.add(activityId)) {
                        continue;
                    }

                    Optional<ActivityVersion> publishedVersion = activityVersionRepository
                            .findFirstByActivityIdAndStatusOrderByVersionNumberDesc(activityId, PublicationStatus.PUBLISHED);

                    publishedVersion.ifPresent(version ->
                            learningPathService.appendItem(learningPathId, version.getId(), LearningPathItemSource.CURRICULUM));
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public Enrollment getEnrollment(UUID id) {
        return enrollmentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Enrollment", id));
    }

    @Transactional(readOnly = true)
    public LearningPath getLearningPath(UUID enrollmentId) {
        return learningPathRepository.findByEnrollmentId(enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException("LearningPath for enrollment", enrollmentId));
    }

}