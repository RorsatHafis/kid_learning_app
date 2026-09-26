package com.platform.notification;

/**
 * One plain-text email. Bodies can carry single-use links, so they are never logged.
 * Published as an application event inside the transaction that decided to send it;
 * {@link EmailDispatcher} delivers it only once that transaction has committed.
 */
public record EmailMessage(String to, String subject, String body) {
}
