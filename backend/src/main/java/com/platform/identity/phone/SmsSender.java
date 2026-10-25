package com.platform.identity.phone;

/**
 * M9b: where text messages go. No provider is configured (owner decision D-04 pending): {@code none} sends nothing and
 * phone verification says it isn't available; {@code local-test} hands each message to the local mail catcher, for
 * development and tests, and refuses to start in production ({@link SmsSafetyGuard}).
 */
public interface SmsSender {

    /** Whether a message can be sent at all. */
    boolean available();

    void send(String e164, String text);

}
