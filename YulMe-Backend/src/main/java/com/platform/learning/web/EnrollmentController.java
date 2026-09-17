package com.platform.learning.web;

import com.platform.family.service.FamilyAccessGuard;
import com.platform.learning.entity.Enrollment;
import com.platform.learning.entity.LearningPath;
import com.platform.learning.entity.LearningPathItem;
import com.platform.learning.service.EnrollmentService;
import com.platform.learning.service.LearningPathService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static com.platform.learning.web.EnrollmentDtos.EnrollRequest;
import static com.platform.learning.web.EnrollmentDtos.EnrollmentResponse;
import static com.platform.learning.web.EnrollmentDtos.LearningPathItemResponse;

/**
 * Every endpoint here is guarded by {@link FamilyAccessGuard} against the
 * authenticated account (the JWT subject, injected as the {@code UUID} principal -
 * see {@code JwtAuthenticationFilter}), never against a childId the client merely
 * claims - Section 25.
 */
@RestController
public class EnrollmentController {

    private final EnrollmentService enrollmentService;
    private final LearningPathService learningPathService;
    private final FamilyAccessGuard familyAccessGuard;

    public EnrollmentController(EnrollmentService enrollmentService, LearningPathService learningPathService,
                                 FamilyAccessGuard familyAccessGuard) {
        this.enrollmentService = enrollmentService;
        this.learningPathService = learningPathService;
        this.familyAccessGuard = familyAccessGuard;
    }

    @PostMapping("/api/v1/children/{childId}/enrollments")
    public ResponseEntity<EnrollmentResponse> enroll(@PathVariable UUID childId,
                                                       @Valid @RequestBody EnrollRequest request,
                                                       @AuthenticationPrincipal UUID accountId) {
        familyAccessGuard.requireChildAccess(accountId, childId);

        Enrollment enrollment = enrollmentService.enroll(childId, request.curriculumId());

        return ResponseEntity.status(HttpStatus.CREATED).body(EnrollmentResponse.from(enrollment));
    }

    @GetMapping("/api/v1/enrollments/{enrollmentId}")
    public ResponseEntity<EnrollmentResponse> getEnrollment(@PathVariable UUID enrollmentId,
                                                              @AuthenticationPrincipal UUID accountId) {
        Enrollment enrollment = enrollmentService.getEnrollment(enrollmentId);
        familyAccessGuard.requireChildAccess(accountId, enrollment.getChildId());

        return ResponseEntity.ok(EnrollmentResponse.from(enrollment));
    }

    /** The single item the frontend should present as "what to do next" for this enrollment. */
    @GetMapping("/api/v1/enrollments/{enrollmentId}/next-activity")
    public ResponseEntity<LearningPathItemResponse> getNextActivity(@PathVariable UUID enrollmentId,
                                                                      @AuthenticationPrincipal UUID accountId) {
        Enrollment enrollment = enrollmentService.getEnrollment(enrollmentId);
        familyAccessGuard.requireChildAccess(accountId, enrollment.getChildId());

        LearningPath learningPath = enrollmentService.getLearningPath(enrollmentId);
        LearningPathItem nextItem = learningPathService.getNextItem(learningPath.getId());

        return ResponseEntity.ok(LearningPathItemResponse.from(nextItem));
    }

}
