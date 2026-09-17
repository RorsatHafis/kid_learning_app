package com.platform.learning.web;

import com.platform.learning.entity.Enrollment;
import com.platform.learning.entity.EnrollmentStatus;
import com.platform.learning.entity.LearningPathItem;
import com.platform.learning.entity.LearningPathItemSource;
import com.platform.learning.entity.LearningPathItemStatus;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public final class EnrollmentDtos {

    private EnrollmentDtos() {
    }

    public record EnrollRequest(
            @NotNull UUID curriculumId
    ) {
    }

    public record EnrollmentResponse(
            UUID id,
            UUID childId,
            UUID curriculumVersionId,
            EnrollmentStatus status,
            Instant enrolledAt
    ) {
        public static EnrollmentResponse from(Enrollment enrollment) {
            return new EnrollmentResponse(enrollment.getId(), enrollment.getChildId(),
                    enrollment.getCurriculumVersionId(), enrollment.getStatus(), enrollment.getEnrolledAt());
        }
    }

    public record LearningPathItemResponse(
            UUID id,
            UUID activityVersionId,
            int sequenceOrder,
            LearningPathItemStatus status,
            LearningPathItemSource source
    ) {
        public static LearningPathItemResponse from(LearningPathItem item) {
            return new LearningPathItemResponse(
                    item.getId(), item.getActivityVersionId(), item.getSequenceOrder(), item.getStatus(), item.getSource());
        }
    }

}
