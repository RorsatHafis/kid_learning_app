package com.platform.identity.phone;

import com.platform.common.config.ConfigurationContract;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** Refuses to start production with the local test sender: a code written to a mail catcher is never a real text. */
@Component
public class SmsSafetyGuard implements InitializingBean {

    private final SmsProperties properties;
    private final Environment environment;

    public SmsSafetyGuard(SmsProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        ConfigurationContract.require(!(properties.sender().equals("local-test") && environment.acceptsProfiles(Profiles.of("prod"))),
                "SMS_SENDER=local-test is for development only: production sends no text until a provider is chosen (D-04)");
    }

}
