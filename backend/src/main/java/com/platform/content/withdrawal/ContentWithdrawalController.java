package com.platform.content.withdrawal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Taking a published question or activity version down, and restoring it (Y16). A reason is required. */
@RestController
@RequestMapping("/api/v1/studio")
public class ContentWithdrawalController {

    private final ContentWithdrawalService withdrawals;

    public ContentWithdrawalController(ContentWithdrawalService withdrawals) {
        this.withdrawals = withdrawals;
    }

    public record WithdrawRequest(@NotBlank @Size(max = 500) String reason) {
    }

    @GetMapping("/{kind:question-versions|activity-versions}/{versionId}/withdrawal")
    public ContentWithdrawalService.WithdrawalState state(@AuthenticationPrincipal UUID accountId, @PathVariable String kind,
                                                          @PathVariable UUID versionId) {
        return withdrawals.state(accountId, kind(kind), versionId);
    }

    @PostMapping("/{kind:question-versions|activity-versions}/{versionId}/withdrawal")
    public ContentWithdrawalService.WithdrawalState withdraw(@AuthenticationPrincipal UUID accountId, @PathVariable String kind,
                                                             @PathVariable UUID versionId, @Valid @RequestBody WithdrawRequest body) {
        return withdrawals.withdraw(accountId, kind(kind), versionId, body.reason());
    }

    @DeleteMapping("/{kind:question-versions|activity-versions}/{versionId}/withdrawal")
    public ContentWithdrawalService.WithdrawalState restore(@AuthenticationPrincipal UUID accountId, @PathVariable String kind,
                                                            @PathVariable UUID versionId) {
        return withdrawals.restore(accountId, kind(kind), versionId);
    }

    private static ContentWithdrawal.Kind kind(String path) {
        return "question-versions".equals(path) ? ContentWithdrawal.Kind.QUESTION : ContentWithdrawal.Kind.ACTIVITY;
    }

}
