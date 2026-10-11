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
        String longest = ("Mango-river-42-kite-".repeat(4)).substring(0, PasswordPolicy.MAX_BYTES);
        assertThatCode(() -> policy.validate(longest)).doesNotThrowAnyException();
        assertThatCode(() -> encoder.encode(longest)).doesNotThrowAnyException();
    }

    @Test
    void longerPasswordsAreRefusedWithAMessageAPersonCanActOn() {
        assertThatThrownBy(() -> policy.validate(("Mango-river-42-kite-".repeat(4)).substring(0, PasswordPolicy.MAX_BYTES + 1)))
                .isInstanceOf(WeakPasswordException.class)
                .hasMessageContaining("at most 72 letters, digits or symbols");
    }

    @Test
    void theLimitCountsBytesSoKhmerPasswordsAreJudgedAsTheEncoderJudgesThem() {
        String khmer24 = khmer(24); // 3 bytes each: 72 bytes
        assertThatCode(() -> policy.validate(khmer24)).doesNotThrowAnyException();
        assertThatCode(() -> encoder.encode(khmer24)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.validate(khmer(25))).hasMessageContaining("too long");
    }

    @Test
    void whatIsGuessedFirstIsRefused() {
        for (String guessable : new String[] {"aaaaaaaaaaaa", "abcabcabcabc", "121212121212", "123456789012", "987654321098",
                "abcdefghijklm", "qwertyuiopas", "Password1234!", "Bookeyond2026!", "bookeyondkid-12"}) {
            assertThatThrownBy(() -> policy.validate(guessable)).as(guessable).isInstanceOf(WeakPasswordException.class);
        }
    }

    @Test
    void theAccountsOwnEmailIsRefused() {
        assertThatThrownBy(() -> policy.validate("sokha.chan@example.org!", "Sokha.Chan@example.org"))
                .hasMessageContaining("email address");
        assertThatThrownBy(() -> policy.validate("SokhaChan2026!", "sokha.chan@example.org")).hasMessageContaining("email address");
        assertThatCode(() -> policy.validate("SokhaChan2026!", "dara@example.org")).doesNotThrowAnyException();
    }

    @Test
    void ordinaryPassphrasesAndKhmerAreAccepted() {
        for (String fine : new String[] {"correct horse battery", "Mango-river-42-kite", "E2E-Journey-Pass-2026", "Verify-Admin-Pass-2026",
                "កខគឃងចឆជឈញដឋ"}) {
            assertThatCode(() -> policy.validate(fine, "someone@example.org")).as(fine).doesNotThrowAnyException();
        }
    }

    @Test
    void shortAndBlankPasswordsAreRefused() {
        assertThatThrownBy(() -> policy.validate("short-pass1")).isInstanceOf(WeakPasswordException.class);
        assertThatThrownBy(() -> policy.validate("")).isInstanceOf(WeakPasswordException.class);
    }

    /** {@code count} different Khmer consonants (U+1780 onwards), 3 bytes each in UTF-8. */
    private static String khmer(int count) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < count; i++) {
            text.append((char) (0x1780 + i));
        }
        return text.toString();
    }

}
