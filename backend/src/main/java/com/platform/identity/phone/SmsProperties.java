package com.platform.identity.phone;

import com.platform.common.config.ConfigurationContract;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code platform.sms.sender}: {@code none} (default) or {@code local-test}. A real provider is decision D-04. */
@ConfigurationProperties(prefix = "platform.sms")
public record SmsProperties(String sender) {

    public SmsProperties {
        sender = sender == null || sender.isBlank() ? "none" : sender.strip();
        ConfigurationContract.require(sender.equals("none") || sender.equals("local-test"),
                "SMS_SENDER must be none or local-test, was " + sender);
    }

}
