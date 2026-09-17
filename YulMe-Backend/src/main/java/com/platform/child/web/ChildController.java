package com.platform.child.web;

import com.platform.child.entity.Child;
import com.platform.child.service.ChildService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static com.platform.child.web.ChildDtos.ChildResponse;
import static com.platform.child.web.ChildDtos.CreateChildRequest;

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

    public ChildController(ChildService childService) {
        this.childService = childService;
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

        return ResponseEntity.status(HttpStatus.CREATED).body(ChildResponse.from(child));
    }

}
