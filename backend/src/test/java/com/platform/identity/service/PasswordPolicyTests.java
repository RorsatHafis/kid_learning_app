package com.platform.identity.service;

import com.platform.identity.exception.WeakPasswordException;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Whatever the policy accepts, the password encoder can hash: no password fails later with the encoder's error. */
class PasswordPolicyTests {

    private final PasswordPolicy policy = new PasswordPolicy();
    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Test
    void theLongestAcceptedPasswordCanBeHashed() {
        String longest = "a".repeat(PasswordPolicy.MAX_BYTES);
        assertThatCode(() -> policy.validate(longest)).doesNotThrowAnyException();
        assertThatCode(() -> encoder.encode(longest)).doesNotThrowAnyException();
    }

    @Test
    void longerPasswordsAreRefusedWithAMessageAPersonCanActOn() {
        assertThatThrownBy(() -> policy.validate("a".repeat(PasswordPolicy.MAX_BYTES + 1)))
                .isInstanceOf(WeakPasswordException.class)
                .hasMessageContaining("at most 72 letters, digits or symbols");
    }

    @Test
    void theLimitCountsBytesSoKhmerPasswordsAreJudgedAsTheEncoderJudgesThem() {
        String khmer24 = "\u1780".repeat(24); // 3 bytes each: 72 bytes
        assertThatCode(() -> policy.validate(khmer24)).doesNotThrowAnyException();
        assertThatCode(() -> encoder.encode(khmer24)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.validate("\u1780".repeat(25))).isInstanceOf(WeakPasswordException.class);
    }

    @Test
    void shortAndBlankPasswordsAreRefused() {
        assertThatThrownBy(() -> policy.validate("short-pass1")).isInstanceOf(WeakPasswordException.class);
        assertThatThrownBy(() -> policy.validate("")).isInstanceOf(WeakPasswordException.class);
    }

}
