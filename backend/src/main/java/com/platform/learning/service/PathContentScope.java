package com.platform.learning.service;

import com.platform.common.web.ResourceNotFoundException;
import com.platform.content.service.ContentOwnership;
import com.platform.curriculum.entity.Curriculum;
import com.platform.curriculum.service.CurriculumService;
import com.platform.learning.entity.Enrollment;
import com.platform.learning.entity.LearningPath;
import com.platform.learning.repository.EnrollmentRepository;
import com.platform.learning.repository.LearningPathRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * N7 content tenancy: which authored content a learning path may draw from.
 *
 * <p>Content ownership already exists: a lesson (and so every activity in it) and a
 * curriculum carry an {@code owner_account_id}; a null owner is platform content,
 * which only ADMIN may write ({@code ContentAccessGuard}). Before N7 nothing read
 * that ownership when choosing content for a child, so a teacher's activity tagged to
 * a platform skill was placed on every family child's path for that skill - by
 * enrollment seeding, by the Adaptive Engine and by Smart Review.
 *
 * <p>The rule (product directive §14.1: "A teacher's content MUST NOT accidentally
 * enter unrelated family children's learning paths. Enforce the boundary on the
 * server"): a path draws from platform content, plus - only when the path's own
 * curriculum is teacher-owned - that same owner's content. A family (platform)
 * curriculum therefore only ever places platform content, and a teacher's content
 * reaches a child only through a curriculum that teacher owns and assigned.
 */
@Service
public class PathContentScope {

    private final LearningPathRepository learningPathRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CurriculumService curriculumService;
    private final ContentOwnership contentOwnership;

    public PathContentScope(LearningPathRepository learningPathRepository, EnrollmentRepository enrollmentRepository,
                            CurriculumService curriculumService, ContentOwnership contentOwnership) {
        this.learningPathRepository = learningPathRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.curriculumService = curriculumService;
        this.contentOwnership = contentOwnership;
    }

    /**
     * The content a path may draw from. {@code curriculumOwnerAccountId} is null for a
     * platform curriculum, in which case only platform content is admitted.
     */
    public record Scope(UUID curriculumOwnerAccountId) {

        public static final Scope PLATFORM_ONLY = new Scope(null);

        public boolean admitsOwner(UUID contentOwnerAccountId) {
            return contentOwnerAccountId == null || Objects.equals(contentOwnerAccountId, curriculumOwnerAccountId);
        }
    }

    @Transactional(readOnly = true)
    public Scope forPath(UUID learningPathId) {
        LearningPath path = learningPathRepository.findById(learningPathId)
                .orElseThrow(() -> new ResourceNotFoundException("LearningPath", learningPathId));
        Enrollment enrollment = enrollmentRepository.findById(path.getEnrollmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment", path.getEnrollmentId()));
        return forCurriculumVersion(enrollment.getCurriculumVersionId());
    }

    @Transactional(readOnly = true)
    public Scope forCurriculumVersion(UUID curriculumVersionId) {
        UUID curriculumId = curriculumService.getVersion(curriculumVersionId).getCurriculumId();
        Curriculum curriculum = curriculumService.getCurriculum(curriculumId);
        return new Scope(curriculum.getOwnerAccountId());
    }

    /** Whether the activity's content may be placed on a path with this scope. */
    @Transactional(readOnly = true)
    public boolean admitsActivity(Scope scope, UUID activityId) {
        return scope.admitsOwner(activityOwner(activityId).orElse(null));
    }

    /** The account that owns an activity's content; empty for platform content. See {@link ContentOwnership}. */
    public Optional<UUID> activityOwner(UUID activityId) {
        return contentOwnership.activityOwner(activityId);
    }

    /**
     * Free practice (an attempt with no path item) has no path scope of its own, so it
     * is admitted by the scopes the child already holds: platform content, or content
     * whose owner also owns a curriculum the child is enrolled in.
     */
    @Transactional(readOnly = true)
    public boolean admitsActivityForChild(UUID childId, UUID activityId) {
        Optional<UUID> owner = activityOwner(activityId);
        if (owner.isEmpty()) {
            return true;
        }
        return enrollmentRepository.findByChildId(childId).stream()
                .map(enrollment -> forCurriculumVersion(enrollment.getCurriculumVersionId()))
                .anyMatch(scope -> owner.get().equals(scope.curriculumOwnerAccountId()));
    }

    /** A platform curriculum: no owner, so family learners may enroll in it directly. */
    @Transactional(readOnly = true)
    public boolean isPlatformCurriculum(UUID curriculumId) {
        return curriculumService.getCurriculum(curriculumId).getOwnerAccountId() == null;
    }

}
