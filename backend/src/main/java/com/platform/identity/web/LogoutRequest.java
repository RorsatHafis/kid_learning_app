package com.platform.identity.web;

import jakarta.validation.constraints.NotBlank;

/**
 * The refresh token identifies which server-side session to revoke. Logout is
 * a real server-side operation, not a client-side token deletion - see {@link
 * com.platform.security.session.AccountSessionService#logout}.
 */
public record LogoutRequest(

        @NotBlank
        String refreshToken

) {
}
