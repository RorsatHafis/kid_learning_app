package com.platform.payment.config;

import com.platform.common.config.ConfigurationContract;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Refuses to start a production instance with the development payment simulator switched on: in production a
 * simulated transaction must never be able to stand for money received. Checked once, at startup.
 */
@Component
public class PaymentSafetyGuard implements InitializingBean {

    private final PaymentProperties properties;
    private final Environment environment;

    public PaymentSafetyGuard(PaymentProperties properties, Environment environment) {

        this.properties = properties;
        this.environment = environment;

    }

    @Override
    public void afterPropertiesSet() {

        ConfigurationContract.require(!(properties.devSimulator().enabled() && environment.acceptsProfiles(Profiles.of("prod"))),
                "PAYMENTS_DEV_SIMULATOR_ENABLED must be false in production: simulated payments are never real money");

    }

}
