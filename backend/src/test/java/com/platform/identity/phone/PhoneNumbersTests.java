package com.platform.identity.phone;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** M9b: numbers typed the way people type them, normalized; the test sender never runs in production. */
class PhoneNumbersTests {

    @Test
    void cambodianNumbersNeedNoCountryCodeAndOtherCountriesUseTheirs() {
        assertThat(PhoneNumbers.normalize("012 345 678")).isEqualTo("+85512345678");
        assertThat(PhoneNumbers.normalize("+855 12 345 678")).isEqualTo("+85512345678");
        assertThat(PhoneNumbers.normalize("(+855) 012-345-678")).isEqualTo("+85512345678");
        assertThat(PhoneNumbers.normalize("+66 81 234 5678")).isEqualTo("+66812345678");
        assertThat(PhoneNumbers.masked("+85512345678")).isEqualTo("+855 ••• ••• 678");
        assertThat(PhoneNumbers.display("+85512345678")).isEqualTo("+855 12 345 678");
    }

    @Test
    void refusesWhatCantReceiveAText() {
        for (String typed : new String[] {"", "123", "not a number", "023 123 456", "+855 99", "x".repeat(50)}) {
            assertThatThrownBy(() -> PhoneNumbers.normalize(typed)).as(typed).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(PhoneNumbers.NOT_A_NUMBER);
        }
    }

    @Test
    void theLocalTestSenderIsRefusedInProduction() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        assertThatThrownBy(() -> new SmsSafetyGuard(new SmsProperties("local-test"), prod).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class);
        new SmsSafetyGuard(new SmsProperties("none"), prod).afterPropertiesSet();
        MockEnvironment local = new MockEnvironment();
        local.setActiveProfiles("local");
        new SmsSafetyGuard(new SmsProperties("local-test"), local).afterPropertiesSet();
        assertThatThrownBy(() -> new SmsProperties("twilio")).isInstanceOf(IllegalStateException.class);
    }

}
