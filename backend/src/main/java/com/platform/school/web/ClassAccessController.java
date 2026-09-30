package com.platform.school.web;

import com.platform.school.entity.ClassJoinCode;
import com.platform.school.service.SchoolJoinService;
import com.platform.school.service.SchoolLearningService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * A class's side of PD-01: its join code, its members' learning in the curricula it assigned, and
 * letting a child go. Only the class's teachers and its school's principal (SchoolAccessGuard#requireClassStaff).
 */
@RestController
@RequestMapping("/api/v1/schools/classes/{classId}")
public class ClassAccessController {

    private final SchoolJoinService joins;
    private final SchoolLearningService learning;

    public ClassAccessController(SchoolJoinService joins, SchoolLearningService learning) {
        this.joins = joins;
        this.learning = learning;
    }

    /** The class's current code; {@code expired} once its 14 days have passed. */
    public record JoinCodeResponse(String code, Instant expiresAt, boolean expired) {
        static JoinCodeResponse of(ClassJoinCode code) {
            return new JoinCodeResponse(code.getCode(), code.getExpiresAt(), !code.isOpenAt(Instant.now()));
        }
    }

    @GetMapping("/join-code")
    public ResponseEntity<JoinCodeResponse> joinCode(@PathVariable UUID classId, @AuthenticationPrincipal UUID accountId) {
        return joins.currentCode(accountId, classId).map(JoinCodeResponse::of).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/join-code")
    @ResponseStatus(HttpStatus.CREATED)
    public JoinCodeResponse issueJoinCode(@PathVariable UUID classId, @AuthenticationPrincipal UUID accountId) {
        return JoinCodeResponse.of(joins.issueCode(accountId, classId));
    }

    @DeleteMapping("/join-code")
    public ResponseEntity<Void> revokeJoinCode(@PathVariable UUID classId, @AuthenticationPrincipal UUID accountId) {
        joins.revokeCode(accountId, classId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/learning")
    public SchoolLearningService.ClassLearning learning(@PathVariable UUID classId, @AuthenticationPrincipal UUID accountId) {
        return learning.classLearning(accountId, classId);
    }

    @GetMapping("/children/{childId}/learning")
    public SchoolLearningService.MemberLearning memberLearning(@PathVariable UUID classId, @PathVariable UUID childId,
                                                               @AuthenticationPrincipal UUID accountId) {
        return learning.memberLearning(accountId, classId, childId);
    }

    @DeleteMapping("/children/{childId}")
    public ResponseEntity<Void> removeFromClass(@PathVariable UUID classId, @PathVariable UUID childId,
                                                @AuthenticationPrincipal UUID accountId) {
        joins.removeFromClass(accountId, classId, childId);
        return ResponseEntity.noContent().build();
    }
}
