package com.platform.admin.web;

import com.platform.admin.service.AdminOperationsService;
import com.platform.common.web.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Platform operations for administrators (see {@link AdminOperationsService}). */
@RestController
@Validated
@RequestMapping("/api/v1/admin")
public class AdminOperationsController {

    private final AdminOperationsService service;

    public AdminOperationsController(AdminOperationsService service) {
        this.service = service;
    }

    public record ReasonRequest(@NotBlank @Size(max = 500) String reason) {
    }

    @GetMapping("/audit-events")
    public AdminOperationsService.AuditPage auditEvents(@AuthenticationPrincipal UUID adminId,
                                                        @RequestParam(required = false) String action,
                                                        @RequestParam(required = false) UUID actorId,
                                                        @RequestParam(required = false) UUID resourceId,
                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant before,
                                                        @RequestParam(required = false) UUID beforeId,
                                                        @RequestParam(defaultValue = "50") int limit) {
        return service.auditLog(adminId, action, actorId, resourceId, before, beforeId, limit);
    }

    @GetMapping("/audit-events/actions")
    public List<String> auditActions(@AuthenticationPrincipal UUID adminId) {
        return service.auditActions(adminId);
    }

    /** An account by its exact email address; 404 when there is none. */
    @GetMapping("/accounts")
    public AdminDtos.StaffAccountResponse findAccount(@AuthenticationPrincipal UUID adminId,
                                                      @RequestParam @NotBlank @Email @Size(max = 320) String email) {
        return service.findByEmail(adminId, email).map(AdminDtos.StaffAccountResponse::of)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "with that email address"));
    }

    @PostMapping("/accounts/{accountId}/suspend")
    public AdminDtos.StaffAccountResponse suspend(@AuthenticationPrincipal UUID adminId, @PathVariable UUID accountId,
                                                  @Valid @RequestBody ReasonRequest request) {
        return AdminDtos.StaffAccountResponse.of(service.suspend(adminId, accountId, request.reason()));
    }

    @PostMapping("/accounts/{accountId}/reinstate")
    public AdminDtos.StaffAccountResponse reinstate(@AuthenticationPrincipal UUID adminId, @PathVariable UUID accountId,
                                                    @Valid @RequestBody ReasonRequest request) {
        return AdminDtos.StaffAccountResponse.of(service.reinstate(adminId, accountId, request.reason()));
    }

}
