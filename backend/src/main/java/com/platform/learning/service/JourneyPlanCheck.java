package com.platform.learning.service;

import com.platform.child.repository.ChildRepository;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.curriculum.service.CurriculumService;
import com.platform.learning.entity.Enrollment;
import com.platform.subscription.subjects.SubjectNotInPlanException;
import com.platform.subscription.subjects.SubjectPlans;
import org.springframework.stereotype.Service;

/**
 * V89 (owner 2026-11-10): a journey is played only while the family's plan includes its subject. A journey a school
 * class assigns is the school's and always passes (the free-plan / school question stays the owner's). Nothing is
 * deleted when a plan stops including a subject: the journey, its answers and evidence stay, and play resumes when the
 * plan includes it again.
 */
@Service
public class JourneyPlanCheck {

    private final SubjectPlans subjectPlans;
    private final CurriculumService curricula;
    private final ChildRepository children;
    private final JourneyStopService stops;

    public JourneyPlanCheck(SubjectPlans subjectPlans, CurriculumService curricula, ChildRepository children,
                            JourneyStopService stops) {
        this.subjectPlans = subjectPlans;
        this.curricula = curricula;
        this.children = children;
        this.stops = stops;
    }

    /** Whether the family's plan includes this journey's subject (always, for a class's journey). */
    public boolean inPlan(Enrollment enrollment) {
        try {
            requireInPlan(enrollment);
            return true;
        } catch (SubjectNotInPlanException outside) {
            return false;
        }
    }

    /** Refuses (409, subjectNotInPlan) a journey whose subject the family's plan doesn't include. */
    public void requireInPlan(Enrollment enrollment) {
        if (!stops.assignedBy(enrollment).isEmpty()) {
            return;
        }
        var curriculum = curricula.getCurriculum(curricula.getVersion(enrollment.getCurriculumVersionId()).getCurriculumId());
        var child = children.findById(enrollment.getChildId())
                .orElseThrow(() -> new ResourceNotFoundException("Child", enrollment.getChildId()));
        subjectPlans.requireInFamilyPlan(child.getFamilyId(), curriculum.getSubjectId());
    }
}
