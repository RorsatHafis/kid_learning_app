package com.platform.content.media;

import com.platform.common.config.ConfigurationContract;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * Where uploaded media is kept (MEDIA_ROOT). Required: an environment that can't keep the
 * pictures and recordings educators upload must not start as though it could. In production
 * it is a persistent volume, backed up with the database (DEPLOYMENT.md). Bound as text so an
 * unset variable (a placeholder left unresolved) is refused like every other required one.
 */
@ConfigurationProperties(prefix = "platform.media")
public record MediaProperties(String root) {

    public MediaProperties {
        ConfigurationContract.requireText(root, "MEDIA_ROOT");
    }

    public Path path() {
        return Path.of(root);
    }

}
