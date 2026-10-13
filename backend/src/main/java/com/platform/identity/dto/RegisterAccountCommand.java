package com.platform.identity.dto;

import com.platform.identity.entity.PlatformRole;

public record RegisterAccountCommand(
    String email,
    String rawPassword,
    String familyName,
    String idempotencyKey,
    PlatformRole platformRole,
    /** The legal documents' version the person agreed to; null when none are configured (Y15). */
    String acceptedLegalVersion,
    /** en or km: the language chosen when signing up. */
    String preferredLanguage
) {

    private static final String DEFAULT_FAMILY_NAME = "My Family";

    public String resolvedFamilyName() {

        return (familyName == null || familyName.isBlank()) ? DEFAULT_FAMILY_NAME : familyName;
        
    }

    /** Public self-registration defaults to PARENT when the caller doesn't specify a role. */
    public PlatformRole resolvedPlatformRole() {

        return platformRole == null ? PlatformRole.PARENT : platformRole;

    }
    
}
