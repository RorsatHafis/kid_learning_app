package com.platform.learning.service;

import com.platform.child.repository.ChildRepository;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.learning.entity.Enrollment;
import com.platform.learning.entity.EnrollmentStatus;
import com.platform.learning.repository.EnrollmentRepository;
import com.platform.subscription.entity.EntitlementKey;
import com.platform.subscription.service.EntitlementService;
import com.platform.subscription.service.PlanLimitException;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V88 (owner 2026-11-10): on a plan with a limit of active journeys per child (the free plan: one), the child's other
 * chosen journeys wait, paused. The family switches: the chosen waiting journey becomes active and, if the plan is
 * full, the family's journey active the longest waits instead - in one transaction, so the child is never left with
 * none or with more than the plan allows.
 *
 * <p>Nothing is lost on either side: answers, evidence and the path's progress stay, and an activity left open waits
 * with its journey. A journey a class assigns is the school's and is never paused here; if those alone fill the plan,
 * the switch is refused with the plan's limit.
 */
@Service
public class JourneySwitchService {

    private final EnrollmentRepository enrollments;
    private final ChildRepository children;
    private final EntitlementService entitlements;
    private final JourneyStopService stops;
    private final JourneyPlanCheck planCheck;

    public JourneySwitchService(EnrollmentRepository enrollments, ChildRepository children, EntitlementService entitlements,
                                JourneyStopService stops, JourneyPlanCheck planCheck) {
        this.planCheck = planCheck;
        this.enrollments = enrollments;
        this.children = children;
        this.entitlements = entitlements;
        this.stops = stops;
    }

    @Transactional
    public Enrollment switchTo(UUID enrollmentId) {
        Enrollment target = enrollments.findById(enrollmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment", enrollmentId));
        UUID childId = target.getChildId();
        // One switch or new journey at a time per child (EntitlementService locks the same row when adding one).
        children.findByIdForUpdate(childId).orElseThrow(() -> new ResourceNotFoundException("Child", childId));
        target = enrollments.findById(enrollmentId).orElseThrow();
        if (target.getStatus() == EnrollmentStatus.ACTIVE) {
            return target; // already being learned: a repeated tap changes nothing
        }
        if (target.getStatus() != EnrollmentStatus.PAUSED) {
            throw new IllegalStateException("Only a waiting path can be switched to.");
        }
        // V89: only to a subject the family's plan includes.
        planCheck.requireInPlan(target);

        Integer limit = entitlements.activeJourneyLimit(childId);
        if (limit != null) {
            List<Enrollment> active = enrollments.findByChildId(childId).stream()
                    .filter(enrollment -> enrollment.getStatus() == EnrollmentStatus.ACTIVE)
                    .sorted(Comparator.comparing(Enrollment::getEnrolledAt))
                    .toList();
            int over = active.size() + 1 - limit;
            if (over > 0) {
                List<Enrollment> familyChosen = active.stream().filter(enrollment -> stops.assignedBy(enrollment).isEmpty()).toList();
                if (familyChosen.size() < over) {
                    throw new PlanLimitException(EntitlementKey.ACTIVE_JOURNEYS_PER_CHILD_MAX, limit);
                }
                for (Enrollment waiting : familyChosen.subList(0, over)) {
                    waiting.pause();
                    enrollments.save(waiting);
                }
            }
        }
        target.resume();
        return enrollments.saveAndFlush(target);
    }
}
