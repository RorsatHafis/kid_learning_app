package com.platform.child.caregiver;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * M10b: a child's trusted caregivers (CaregiverService). The family's side is under the child; the caregiver's own
 * side ({@code /api/v1/caregiving}) is what the signed-in adult was invited to or helps with.
 */
@RestController
public class CaregiverController {

    private final CaregiverService caregivers;

    public CaregiverController(CaregiverService caregivers) {
        this.caregivers = caregivers;
    }

    public record InviteRequest(@NotBlank @Email @Size(max = 320) String email, @NotBlank String scope) {
    }

    public record ScopeRequest(@NotBlank String scope) {
    }

    @GetMapping("/api/v1/children/{childId}/caregivers")
    public List<CaregiverService.Caregiver> of(@AuthenticationPrincipal UUID accountId, @PathVariable UUID childId) {
        return caregivers.of(accountId, childId);
    }

    @PostMapping("/api/v1/children/{childId}/caregivers")
    public ResponseEntity<CaregiverService.Caregiver> invite(@AuthenticationPrincipal UUID accountId, @PathVariable UUID childId,
                                                             @Valid @RequestBody InviteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(caregivers.invite(accountId, childId, request.email(), request.scope()));
    }

    @PutMapping("/api/v1/children/{childId}/caregivers/{caregiverId}")
    public CaregiverService.Caregiver changeScope(@AuthenticationPrincipal UUID accountId, @PathVariable UUID childId,
                                                  @PathVariable UUID caregiverId, @Valid @RequestBody ScopeRequest request) {
        return caregivers.changeScope(accountId, childId, caregiverId, request.scope());
    }

    @DeleteMapping("/api/v1/children/{childId}/caregivers/{caregiverId}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal UUID accountId, @PathVariable UUID childId, @PathVariable UUID caregiverId) {
        caregivers.remove(accountId, childId, caregiverId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/caregiving")
    public List<CaregiverService.CaredFor> caring(@AuthenticationPrincipal UUID accountId) {
        return caregivers.caring(accountId);
    }

    @PostMapping("/api/v1/caregiving/{caregiverId}/accept")
    public CaregiverService.CaredFor accept(@AuthenticationPrincipal UUID accountId, @PathVariable UUID caregiverId) {
        return caregivers.accept(accountId, caregiverId);
    }

    @PostMapping("/api/v1/caregiving/{caregiverId}/decline")
    public ResponseEntity<Void> decline(@AuthenticationPrincipal UUID accountId, @PathVariable UUID caregiverId) {
        caregivers.decline(accountId, caregiverId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/v1/caregiving/{caregiverId}")
    public ResponseEntity<Void> leave(@AuthenticationPrincipal UUID accountId, @PathVariable UUID caregiverId) {
        caregivers.leave(accountId, caregiverId);
        return ResponseEntity.noContent().build();
    }

}
