package com.platform.identity.exception;

/**
 * An emailed link that cannot be used: unknown, expired, already used, replaced by a
 * newer link, or for an account that can no longer use it. Deliberately one outcome -
 * the response never tells a caller which, or whether the link ever existed.
 */
public class InvalidActionLinkException extends RuntimeException {

    public InvalidActionLinkException() {
        super("This link is invalid, has expired, or has already been used");
    }

}
