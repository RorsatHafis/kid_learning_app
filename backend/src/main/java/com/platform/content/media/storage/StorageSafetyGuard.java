package com.platform.content.media.storage;

import com.platform.common.config.ConfigurationContract;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Refuses to start a production instance that would let a school point its storage at a private or loopback address
 * (an SSRF path into the platform's own network). Checked once, at startup.
 */
@Component
public class StorageSafetyGuard implements InitializingBean {

    private final StorageProperties properties;
    private final Environment environment;

    public StorageSafetyGuard(StorageProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        ConfigurationContract.require(!(properties.allowPrivateEndpoints() && environment.acceptsProfiles(Profiles.of("prod"))),
                "STORAGE_ALLOW_PRIVATE_ENDPOINTS must be false in production: a school's storage is never on the platform's network");
    }
}
