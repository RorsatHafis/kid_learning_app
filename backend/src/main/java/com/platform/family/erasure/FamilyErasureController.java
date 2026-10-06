package com.platform.family.erasure;

import com.platform.child.web.ChildDtos.ChildResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Asking to delete a child or one's own family account, and changing one's mind (see
 * {@link FamilyErasureService}). The current password confirms each request (422 when wrong,
 * same lockout as login).
 */
@RestController
public class FamilyErasureController {

    private final FamilyErasureService erasure;

    public FamilyErasureController(FamilyErasureService erasure) {
        this.erasure = erasure;
    }

    public record DeletionRequest(@NotBlank String currentPassword) {
    }

    public record DeletionScheduled(Instant eraseAfter) {
    }

    /** 202: the child leaves learning and schools now and is erased at {@code eraseAfter}. */
    @PostMapping("/api/v1/children/{childId}/deletion")
    public ResponseEntity<DeletionScheduled> deleteChild(@PathVariable UUID childId, @Valid @RequestBody DeletionRequest request,
                                                        @AuthenticationPrincipal UUID accountId) {
        return ResponseEntity.accepted()
                .body(new DeletionScheduled(erasure.scheduleChildErasure(accountId, childId, request.currentPassword())));
    }

    /** Keeps the child (409 if it isn't being deleted; a plan without room for it also answers 409/422). */
    @DeleteMapping("/api/v1/children/{childId}/deletion")
    public ChildResponse keepChild(@PathVariable UUID childId, @AuthenticationPrincipal UUID accountId) {
        return ChildResponse.from(erasure.cancelChildErasure(accountId, childId));
    }

    /** 202: everything keeps working until {@code eraseAfter}; then the account and its family are erased. */
    @PostMapping("/api/v1/account/deletion")
    public ResponseEntity<DeletionScheduled> deleteAccount(@Valid @RequestBody DeletionRequest request,
                                                          @AuthenticationPrincipal UUID accountId) {
        return ResponseEntity.accepted()
                .body(new DeletionScheduled(erasure.scheduleAccountErasure(accountId, request.currentPassword())));
    }

    @DeleteMapping("/api/v1/account/deletion")
    public ResponseEntity<Void> keepAccount(@AuthenticationPrincipal UUID accountId) {
        erasure.cancelAccountErasure(accountId);
        return ResponseEntity.noContent().build();
    }

}
