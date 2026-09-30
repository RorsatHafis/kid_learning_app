package com.platform.school.web;

import com.platform.school.service.SchoolJoinService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The family's side of PD-01: look up a class code, join, see which schools have access to the
 * child's learning, and end that access. Only the child's family (FamilyAccessGuard).
 */
@RestController
@RequestMapping("/api/v1/children/{childId}/schools")
public class FamilySchoolAccessController {

    private final SchoolJoinService joins;

    public FamilySchoolAccessController(SchoolJoinService joins) {
        this.joins = joins;
    }

    public record CodeRequest(@NotBlank @Size(max = 20) String code) {
    }

    @GetMapping
    public List<SchoolJoinService.SchoolAccessView> schools(@PathVariable UUID childId, @AuthenticationPrincipal UUID accountId) {
        return joins.accessOf(accountId, childId);
    }

    /** What joining with this code would mean; changes nothing. */
    @PostMapping("/preview")
    public SchoolJoinService.JoinPreview preview(@PathVariable UUID childId, @Valid @RequestBody CodeRequest request,
                                                 @AuthenticationPrincipal UUID accountId) {
        return joins.preview(accountId, childId, request.code());
    }

    @PostMapping("/join")
    public SchoolJoinService.JoinPreview join(@PathVariable UUID childId, @Valid @RequestBody CodeRequest request,
                                              @AuthenticationPrincipal UUID accountId) {
        return joins.join(accountId, childId, request.code());
    }

    @DeleteMapping("/{schoolId}")
    public ResponseEntity<Void> revoke(@PathVariable UUID childId, @PathVariable UUID schoolId,
                                       @AuthenticationPrincipal UUID accountId) {
        joins.revoke(accountId, childId, schoolId);
        return ResponseEntity.noContent().build();
    }
}
