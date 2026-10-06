package com.platform.review.service;

import com.platform.learning.entity.Enrollment;
import com.platform.learning.entity.LearningPath;
import com.platform.learning.entity.LearningPathItemStatus;
import com.platform.learning.repository.EnrollmentRepository;
import com.platform.learning.repository.LearningPathItemRepository;
import com.platform.learning.repository.LearningPathRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Due reviews for a child who has nothing waiting (X25). Smart Review places a due review after the
 * child completes a step on a path ({@code PostCompletionService}); a child who has finished every step
 * completes nothing more, so a review falling due afterwards was never placed and their path read
 * "all done" while a review was due. Asked for when the child's space opens: on each of the child's
 * active paths with nothing waiting, the earliest due review of a skill that path's curriculum teaches
 * is placed - the same placement (path lock, one waiting review per path, the lowest-difficulty published
 * activity the path's scope admits). Paths with a step waiting are left to the completion flow.
 */
@Service
public class DueReviewService {

    private final SmartReviewService smartReview;
    private final EnrollmentRepository enrollments;
    private final LearningPathRepository paths;
    private final LearningPathItemRepository items;
    private final NamedParameterJdbcTemplate jdbc;

    public DueReviewService(SmartReviewService smartReview, EnrollmentRepository enrollments, LearningPathRepository paths,
                            LearningPathItemRepository items, NamedParameterJdbcTemplate jdbc) {
        this.smartReview = smartReview;
        this.enrollments = enrollments;
        this.paths = paths;
        this.items = items;
        this.jdbc = jdbc;
    }

    /** How many reviews were placed (0 when nothing was due, or every path has a step waiting). Callers check the family. */
    @Transactional
    public int placeDueReviews(UUID childId) {
        int placed = 0;
        List<Enrollment> active = enrollments.findByChildId(childId).stream()
                .filter(enrollment -> enrollment.getStatus() == com.platform.learning.entity.EnrollmentStatus.ACTIVE)
                .sorted(Comparator.comparing(Enrollment::getEnrolledAt))
                .toList();
        for (Enrollment enrollment : active) {
            LearningPath path = paths.findByEnrollmentId(enrollment.getId()).orElse(null);
            if (path == null || hasWaiting(path.getId())) {
                continue;
            }
            if (smartReview.insertDueReviewOnPath(childId, path.getId(), skillsTaught(enrollment.getCurriculumVersionId())).isPresent()) {
                placed++;
            }
        }
        return placed;
    }

    private boolean hasWaiting(UUID pathId) {
        return items.findByLearningPathIdOrderBySequenceOrderAsc(pathId).stream()
                .anyMatch(item -> item.getStatus() == LearningPathItemStatus.PENDING || item.getStatus() == LearningPathItemStatus.IN_PROGRESS);
    }

    private Set<UUID> skillsTaught(UUID curriculumVersionId) {
        return new HashSet<>(jdbc.queryForList("""
                SELECT DISTINCT los.skill_id FROM curriculum_objectives co
                JOIN learning_objective_skills los ON los.learning_objective_id = co.learning_objective_id
                WHERE co.curriculum_version_id = :version
                """, new MapSqlParameterSource("version", curriculumVersionId), UUID.class));
    }

}
