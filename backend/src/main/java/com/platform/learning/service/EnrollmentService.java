package com.platform.learning.service;

import com.platform.child.entity.Child;
import com.platform.child.repository.ChildRepository;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.curriculum.entity.AgeHub;
import com.platform.curriculum.entity.Curriculum;
import com.platform.curriculum.entity.CurriculumVersion;
import com.platform.curriculum.repository.AgeHubRepository;
import com.platform.curriculum.service.CurriculumService;
import com.platform.learning.entity.Enrollment;
import com.platform.learning.entity.LearningPath;
import com.platform.learning.entity.LearningPathItemSource;
import com.platform.learning.repository.EnrollmentRepository;
import com.platform.learning.repository.LearningPathRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
    private final ChildRepository childRepository;
    private final AgeHubRepository ageHubRepository;
    private final PathContentScope pathContentScope;
    private final com.platform.subscription.service.EntitlementService entitlementService;
    private final CurriculumPathPlanner pathPlanner;

    public EnrollmentService(EnrollmentRepository enrollmentRepository, LearningPathRepository learningPathRepository,
                              LearningPathService learningPathService, CurriculumService curriculumService,
                              ChildRepository childRepository,
                              AgeHubRepository ageHubRepository, PathContentScope pathContentScope,
                              com.platform.subscription.service.EntitlementService entitlementService,
                              CurriculumPathPlanner pathPlanner) {
        this.enrollmentRepository = enrollmentRepository;
        this.learningPathRepository = learningPathRepository;
        this.learningPathService = learningPathService;
        this.curriculumService = curriculumService;
        this.childRepository = childRepository;
        this.ageHubRepository = ageHubRepository;
        this.pathContentScope = pathContentScope;
        this.entitlementService = entitlementService;
        this.pathPlanner = pathPlanner;
    }

    /**
     * N7: a family enrolls a child only in a platform curriculum. A teacher-owned
     * curriculum reaches children through its owner's class assignment
     * ({@code SchoolService#assignCurriculum}), never by a parent naming its id -
     * answered as "not found" so a family can't probe for another tenant's curricula.
     */
    @Transactional
    public Enrollment enrollInPlatformCurriculum(UUID childId, UUID curriculumId) {
        if (!pathContentScope.isPlatformCurriculum(curriculumId)) {
            throw new ResourceNotFoundException("Curriculum", curriculumId);
        }
        // A family starting another journey is a plan-limited action (§3); re-asking for one the
        // child already follows is not, so it stays idempotent at any limit.
        CurriculumVersion published = curriculumService.getPublishedVersion(curriculumId);
        if (enrollmentRepository.findByChildIdAndCurriculumVersionId(childId, published.getId()).isEmpty()) {
            entitlementService.requireRoomForJourney(childId);
        }
        return enroll(childId, curriculumId);
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
     * Battle 2B: best-effort automatic enrollment for a newly created child, so a
     * parent does not have to know about (or discover) the separate explicit
     * {@link #enroll} endpoint just to get a first learning path. Resolves the
     * {@link AgeHub} the child's current age falls into, then looks for ACTIVE
     * curricula for that age hub - across every {@code Subject} - that also have a
     * published version ready to enroll into.
     *
     * <p>Enrolls only when that search finds <em>exactly one</em> candidate. Which
     * subject a new child should be started in when more than one is eligible is a
     * genuine product decision this repository does not capture anywhere - neither
     * {@code Child} nor {@code CreateChildRequest} records a subject preference,
     * and {@code Curriculum} is explicitly scoped by both subject and age hub, not
     * age hub alone. Guessing (e.g. "always pick Khmer", "pick the oldest
     * curriculum") would be inventing that decision rather than reading it from the
     * domain model, so any other case - zero eligible curricula (nothing published
     * yet for this age) or more than one (ambiguous subject choice) - is left for
     * deliberate enrollment via {@link #enroll} instead, exactly the same as it was
     * before this method existed. Returns {@link Optional#empty()} in either case,
     * never a fabricated/default enrollment.
     *
     * <p>Runs in its own transaction ({@code REQUIRES_NEW}), deliberately separate
     * from whatever transaction created the child. Child creation must succeed
     * regardless of curriculum content state (an empty/mid-authoring catalog is a
     * normal, product-accepted state - see Scenario 22 - not a reason to fail
     * adding a child), so the caller ({@code ChildService#createForAccount}) always
     * invokes this after the child is already persisted and catches whatever it
     * throws rather than letting a curriculum-resolution problem roll back the
     * child itself.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Enrollment> autoEnrollIfUnambiguous(Child child) {
        int age = child.ageInYears(LocalDate.now());

        Optional<AgeHub> ageHub = ageHubRepository.findForAge(age);
        if (ageHub.isEmpty()) {
            return Optional.empty();
        }

        List<Curriculum> candidates = curriculumService.listActiveForAgeHub(ageHub.get().getId()).stream()
                .filter(curriculum -> curriculum.getOwnerAccountId() == null)
                .filter(curriculum -> curriculumService.hasPublishedVersion(curriculum.getId()))
                .toList();
        if (candidates.size() != 1) {
            return Optional.empty();
        }

        return Optional.of(enrollInPlatformCurriculum(child.getId(), candidates.get(0).getId()));
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
     * isn't placeable yet. N7: so is an Activity outside the curriculum's content
     * scope (see {@link PathContentScope}) - a platform curriculum never seeds a
     * teacher's activity just because it is tagged with one of its skills.
     */
    /** The curriculum's steps, as CurriculumPathPlanner defines them (the Studio's journey builder shows the same plan). */
    private void seedInitialPath(UUID learningPathId, UUID curriculumVersionId) {
        for (CurriculumPathPlanner.PlannedStep step : pathPlanner.plan(curriculumVersionId)) {
            learningPathService.appendItem(learningPathId, step.version().getId(), LearningPathItemSource.CURRICULUM);
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