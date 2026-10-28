package com.platform.school.web;

import com.platform.school.entity.School;
import com.platform.school.service.SchoolAccessGuard;
import com.platform.school.service.SchoolOrganizationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** M12 B0: a school organization and its branches, for its owner (all branches) or a branch's principal (theirs). */
@RestController
@RequestMapping("/api/v1/organizations")
public class SchoolOrganizationController {

    private final SchoolOrganizationService service;
    private final SchoolAccessGuard guard;

    public SchoolOrganizationController(SchoolOrganizationService service, SchoolAccessGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping("/mine")
    public List<OrganizationResponse> mine(@AuthenticationPrincipal UUID accountId) {
        guard.requireStaff(accountId);
        return service.mine(accountId).stream().map(o -> new OrganizationResponse(o.organization().getId(), o.organization().getName(),
                o.owner(), o.branches().stream().map(BranchResponse::of).toList())).toList();
    }

    @PostMapping("/{organizationId}/branches")
    public ResponseEntity<BranchResponse> addBranch(@PathVariable UUID organizationId, @Valid @RequestBody BranchRequest request,
                                                    @AuthenticationPrincipal UUID accountId) {
        guard.requireStaff(accountId);
        return ResponseEntity.status(HttpStatus.CREATED).body(BranchResponse.of(service.addBranch(organizationId, request.name(), accountId)));
    }

    public record BranchRequest(@NotBlank @Size(max = 200) String name) {}

    public record BranchResponse(UUID id, String name, UUID principalAccountId, boolean archived) {
        static BranchResponse of(School s) { return new BranchResponse(s.getId(), s.getName(), s.getPrincipalAccountId(), s.isArchived()); }
    }

    public record OrganizationResponse(UUID id, String name, boolean owner, List<BranchResponse> branches) {}
}
