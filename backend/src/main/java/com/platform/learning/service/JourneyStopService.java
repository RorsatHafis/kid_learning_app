package com.platform.learning.service;

import com.platform.common.web.ResourceNotFoundException;
import com.platform.learning.entity.Enrollment;
import com.platform.learning.entity.EnrollmentStatus;
import com.platform.learning.repository.EnrollmentRepository;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A family stops one of its child's journeys (Y19). Without it a journey, once started, could never
 * end, so a plan allowing one journey per child left the family unable to change subject.
 *
 * <p>Stopped means withdrawn: the journey is no longer offered or counted against the plan, an
 * activity left open on it is closed, and nothing is deleted - answers, evidence and the path stay.
 * Choosing the same curriculum again carries on where it stopped. A journey a class the child is in
 * assigns is the class's while that lasts: it is refused here (the family can end the school's access).
 */
@Service
public class JourneyStopService {

    private final EnrollmentRepository enrollments;
    private final NamedParameterJdbcTemplate jdbc;

    public JourneyStopService(EnrollmentRepository enrollments, NamedParameterJdbcTemplate jdbc) {
        this.enrollments = enrollments;
        this.jdbc = jdbc;
    }

    @Transactional
    public Enrollment stop(UUID enrollmentId) {
        Enrollment enrollment = enrollments.findById(enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment", enrollmentId));
        if (enrollment.getStatus() == EnrollmentStatus.WITHDRAWN) {
            throw new IllegalStateException("This path is already stopped.");
        }
        if (enrollment.getStatus() == EnrollmentStatus.COMPLETED) {
            throw new IllegalStateException("This path is finished; there is nothing to stop.");
        }
        List<String> assignedBy = jdbc.queryForList("""
                select sc.name || ' at ' || s.name
                from class_enrollments ce
                join school_classes sc on sc.id = ce.school_class_id and sc.status = 'ACTIVE'
                join schools s on s.id = sc.school_id
                join school_child_access g on g.child_id = ce.child_id and g.school_id = sc.school_id and g.status = 'ACTIVE'
                join class_curriculum_assignments k on k.school_class_id = sc.id and k.status = 'ACTIVE'
                join curriculum_versions cv on cv.curriculum_id = k.curriculum_id
                where ce.child_id = :child and ce.status = 'ACTIVE' and cv.id = :version
                order by 1
                """, Map.of("child", enrollment.getChildId(), "version", enrollment.getCurriculumVersionId()), String.class);
        if (!assignedBy.isEmpty()) {
            throw new IllegalStateException(assignedBy.getFirst() + " assigns this path, so it stays while the class does. "
                    + "To stop it, end the school's access on this page.");
        }
        enrollment.withdraw();
        // An activity left open on this path is closed; its answers stay.
        jdbc.update("""
                update activity_attempts set status = 'ABANDONED', completed_at = now(), version = version + 1
                where status = 'IN_PROGRESS' and learning_path_item_id in (
                    select i.id from learning_path_items i join learning_paths p on p.id = i.learning_path_id
                    where p.enrollment_id = :enrollment)
                """, Map.of("enrollment", enrollmentId));
        return enrollments.saveAndFlush(enrollment);
    }

}
