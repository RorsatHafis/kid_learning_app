package com.platform.identity.dto;

/**
 * {@code userAgent}/{@code ipAddress} are optional, caller-supplied context
 * (from the HTTP request) recorded on the refresh-token session this login
 * issues - see {@link com.platform.security.session.AccountSession}. Neither is
 * used for authentication or authorization decisions.
 */
public record LoginCommand(String email, String rawPassword, String userAgent, String ipAddress) {

}
