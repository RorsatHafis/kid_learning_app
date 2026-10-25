package com.platform.identity.phone;

import com.platform.notification.EmailDispatcher;
import com.platform.notification.EmailMessage;
import org.springframework.stereotype.Component;

/**
 * The configured {@link SmsSender}. {@code local-test} writes each text to the local mail catcher, addressed
 * {@code sms-<digits>@sms.test}, so a developer or an end-to-end test reads the code there; nothing leaves the machine.
 */
@Component
public class ConfiguredSmsSender implements SmsSender {

    private final SmsProperties properties;
    private final EmailDispatcher mail;

    public ConfiguredSmsSender(SmsProperties properties, EmailDispatcher mail) {
        this.properties = properties;
        this.mail = mail;
    }

    @Override
    public boolean available() {
        return properties.sender().equals("local-test");
    }

    @Override
    public void send(String e164, String text) {
        if (!available()) {
            throw new PhoneUnavailableException();
        }
        mail.deliver(new EmailMessage("sms-" + e164.substring(1) + "@sms.test", "SMS to " + e164, text));
    }

}
