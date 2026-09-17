package com.platform.admin.web;

import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public final class AdminDtos {
    private AdminDtos() {}

    public record CreateStaffRequest(
            @Email @NotBlank String email,
            @NotBlank String password,
            @NotNull PlatformRole role) {}

    public record StaffAccountResponse(
            UUID id,
            String email,
            PlatformRole platformRole,
            String status,
            Instant emailVerifiedAt) {
        public static StaffAccountResponse of(Account account) {
            return new StaffAccountResponse(account.getId(), account.getEmail(),
                    account.getPlatformRole(), account.getStatus().name(), account.getEmailVerifiedAt());
        }
    }
}
