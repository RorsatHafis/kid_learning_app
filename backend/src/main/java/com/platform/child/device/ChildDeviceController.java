package com.platform.child.device;

import com.platform.security.session.AccountSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * M10a: a parent opens one child's learning space on another device (ChildDeviceLinkService). The parent's side is
 * behind their sign-in and family access; the device's side ({@code /api/v1/auth/child-device/...}) has no sign-in
 * yet, so it sits with the other identity endpoints: public and rate limited per network (AuthRateLimitFilter).
 */
@RestController
public class ChildDeviceController {

    private final ChildDeviceLinkService links;

    public ChildDeviceController(ChildDeviceLinkService links) {
        this.links = links;
    }

    public record CodeResponse(UUID id, String code, Instant expiresAt) {
    }

    public record ClaimRequest(@NotBlank @Size(max = 32) String code) {
    }

    public record CollectRequest(@NotNull UUID claimId, @NotBlank @Size(max = 100) String secret) {
    }

    /** {@code status} WAITING until the parent approves; then the sign-in, once, for {@code childId} only. */
    public record CollectResponse(String status, UUID accountId, UUID childId, String childName, String accessToken, Instant expiresAt,
                                  String refreshToken, Instant refreshTokenExpiresAt) {
    }

    @PostMapping("/api/v1/children/{childId}/device-codes")
    public ResponseEntity<CodeResponse> create(@AuthenticationPrincipal UUID accountId, @PathVariable UUID childId) {
        ChildDeviceLinkService.NewCode code = links.create(accountId, childId);
        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new CodeResponse(code.id(), code.code(), code.expiresAt()));
    }

    @GetMapping("/api/v1/children/{childId}/device-codes/{codeId}")
    public ChildDeviceLinkService.CodeState state(@AuthenticationPrincipal UUID accountId, @PathVariable UUID childId,
                                                  @PathVariable UUID codeId) {
        return links.state(accountId, childId, codeId);
    }

    @PostMapping("/api/v1/children/{childId}/device-codes/{codeId}/approve")
    public ChildDeviceLinkService.CodeState approve(@AuthenticationPrincipal UUID accountId, @PathVariable UUID childId,
                                                    @PathVariable UUID codeId) {
        return links.approve(accountId, childId, codeId);
    }

    @PostMapping("/api/v1/children/{childId}/device-codes/{codeId}/cancel")
    public ChildDeviceLinkService.CodeState cancel(@AuthenticationPrincipal UUID accountId, @PathVariable UUID childId,
                                                   @PathVariable UUID codeId) {
        return links.cancel(accountId, childId, codeId);
    }

    @PostMapping("/api/v1/auth/child-device/claim")
    public ResponseEntity<ChildDeviceLinkService.Claim> claim(@Valid @RequestBody ClaimRequest request, HttpServletRequest http) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(links.claim(request.code(), http.getHeader(HttpHeaders.USER_AGENT)));
    }

    @PostMapping("/api/v1/auth/child-device/collect")
    public ResponseEntity<CollectResponse> collect(@Valid @RequestBody CollectRequest request, HttpServletRequest http) {
        ChildDeviceLinkService.Collected collected = links.collect(request.claimId(), request.secret(),
                http.getHeader(HttpHeaders.USER_AGENT), http.getRemoteAddr());
        if (collected.waiting()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(new CollectResponse("WAITING", null, null, null, null, null, null, null));
        }
        AccountSessionService.RefreshResult session = collected.session();
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(new CollectResponse("SIGNED_IN",
                session.accountId(), collected.childId(), collected.childName(), session.accessToken(), session.accessTokenExpiresAt(),
                session.refreshToken(), session.refreshTokenExpiresAt()));
    }

}
