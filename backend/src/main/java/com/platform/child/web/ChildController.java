package com.platform.child.web;

import com.platform.child.entity.Child;
import com.platform.child.service.ChildService;
import com.platform.family.service.FamilyAccessGuard;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static com.platform.child.web.ChildDtos.ChildResponse;
import static com.platform.child.web.ChildDtos.CreateChildRequest;
import static com.platform.child.web.ChildDtos.UpdateChildRequest;

/**
 * The missing "step 2" of the golden journey (login -&gt; child -&gt; enrollment -&gt; ...):
 * public registration never created a Child, and nothing exposed
 * {@code ChildRepository} over HTTP. Both endpoints are scoped to the
 * authenticated account's own family membership(s) only - never a
 * client-supplied childId/familyId - consistent with {@link com.platform.family.service.FamilyAccessGuard}'s
 * posture on every other child-scoped endpoint in the platform.
 */
@RestController
@RequestMapping("/api/v1/children")
public class ChildController {

    private final ChildService childService;
    private final FamilyAccessGuard familyAccessGuard;

    public ChildController(ChildService childService, FamilyAccessGuard familyAccessGuard) {
        this.childService = childService;
        this.familyAccessGuard = familyAccessGuard;
    }

    @GetMapping("/{childId}")
    public ResponseEntity<ChildResponse> getChild(@PathVariable UUID childId, @AuthenticationPrincipal UUID accountId) {
        return ResponseEntity.ok(ChildResponse.from(familyAccessGuard.requireChildAccess(accountId, childId)));
    }

    @PatchMapping("/{childId}")
    public ResponseEntity<ChildResponse> updateChild(@PathVariable UUID childId,
                                                     @Valid @RequestBody UpdateChildRequest request,
                                                     @AuthenticationPrincipal UUID accountId) {
        familyAccessGuard.requireChildAccess(accountId, childId);
        return ResponseEntity.ok(ChildResponse.from(childService.update(childId, request.displayName(), request.dateOfBirth())));
    }

    @GetMapping
    public ResponseEntity<List<ChildResponse>> listMyChildren(@AuthenticationPrincipal UUID accountId) {
        List<ChildResponse> children = childService.listForAccount(accountId).stream()
                .map(ChildResponse::from)
                .toList();

        return ResponseEntity.ok(children);
    }

    @PostMapping
    public ResponseEntity<ChildResponse> addChild(@Valid @RequestBody CreateChildRequest request,
                                                    @AuthenticationPrincipal UUID accountId) {
        Child child = childService.createForAccount(accountId, request.displayName(), request.dateOfBirth());

        // Separate call, on purpose: createForAccount's own transaction has already
        // committed by the time control returns here (crossing back out through its
        // @Transactional proxy boundary), so the child row is now visible to the
        // fresh transaction this starts - see ChildService#completeAutoEnrollment.
        childService.completeAutoEnrollment(child);

        return ResponseEntity.status(HttpStatus.CREATED).body(ChildResponse.from(child));
    }

}
