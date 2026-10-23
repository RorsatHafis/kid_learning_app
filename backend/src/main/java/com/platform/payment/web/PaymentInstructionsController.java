package com.platform.payment.web;

import com.platform.content.media.MediaService;
import com.platform.payment.service.PaymentInstructionsService;
import com.platform.security.stepup.RequiresStepUp;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * M8b: the administrator's view and editing of how customers pay by bank transfer (PaymentInstructionsService). Saving
 * moves money's destination, so it needs the password re-entered (RequiresStepUp).
 */
@RestController
public class PaymentInstructionsController {

    private final PaymentInstructionsService instructions;
    private final MediaService media;

    public PaymentInstructionsController(PaymentInstructionsService instructions, MediaService media) {
        this.instructions = instructions;
        this.media = media;
    }

    /** {@code fromEnvironment}: not saved in the app yet - the PAYMENTS_MANUAL_* variables apply. */
    public record InstructionsResponse(Integer version, boolean enabled, String instructionsEn, String instructionsKm, UUID qrAssetId,
                                       String qrImageUrl, Instant savedAt, boolean fromEnvironment) {
    }

    public record InstructionsView(InstructionsResponse current, List<InstructionsResponse> history) {
    }

    /** {@code expectedVersion}: the version being edited (null for the first), so a concurrent save is refused. */
    public record SaveRequest(Integer expectedVersion, boolean enabled, @Size(max = 2000) String instructionsEn,
                              @Size(max = 2000) String instructionsKm, UUID qrAssetId) {
    }

    @GetMapping("/api/v1/admin/payment-instructions")
    public InstructionsView get(@AuthenticationPrincipal UUID adminId) {
        List<InstructionsResponse> history = instructions.history(adminId).stream().map(this::response).toList();
        return new InstructionsView(response(instructions.current()), history);
    }

    @RequiresStepUp
    @PutMapping("/api/v1/admin/payment-instructions")
    public InstructionsView save(@AuthenticationPrincipal UUID adminId, @Valid @RequestBody SaveRequest request) {
        instructions.save(adminId, request.expectedVersion(), request.enabled(), request.instructionsEn(), request.instructionsKm(),
                request.qrAssetId());
        return get(adminId);
    }

    private InstructionsResponse response(PaymentInstructionsService.Instructions saved) {
        String qr = saved.qrAssetId() == null ? null
                : Optional.ofNullable(media.refs(Set.of(saved.qrAssetId())).get(saved.qrAssetId())).map(MediaService.MediaRef::url).orElse(null);
        return new InstructionsResponse(saved.version(), saved.enabled(), saved.instructionsEn(), saved.instructionsKm(),
                saved.qrAssetId(), qr, saved.createdAt(), saved.fromEnvironment());
    }

}
