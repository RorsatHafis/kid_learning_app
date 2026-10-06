package com.platform.identity.entity;

/** What an {@link AccountActionToken} proves mailbox access for. */
public enum AccountActionPurpose {
    EMAIL_VERIFICATION,
    PASSWORD_RESET,
    /** First password for an account created by an invitation (V39). */
    ACCOUNT_SETUP,
    /** Moving the sign-in address: the link goes to the new address (V50). */
    EMAIL_CHANGE
}
