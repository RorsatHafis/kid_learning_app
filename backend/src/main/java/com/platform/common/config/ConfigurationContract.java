package com.platform.common.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.source.ConfigurationProperty;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.stereotype.Component;

/**
 * The external configuration contract: each operator-facing environment variable and
 * the property it sets. application.yaml performs the mapping (${JWT_SECRET} and so
 * on), so every profile honours the same names.
 *
 * <p>Spring's relaxed binding also lets an environment variable named after the
 * property itself (PLATFORM_SECURITY_JWT_SECRET, SPRING_DATASOURCE_URL, ...) set it,
 * and such a variable outranks the value application.yaml maps from the documented
 * name. Two environment variables would then control one setting, and the
 * documented one would be silently ignored. Startup is refused instead.
 * Command-line arguments and test properties are deliberate overrides and remain
 * allowed.
 */
@Component
public class ConfigurationContract {

    /** Documented environment variable -> the property it sets. */
    public static final Map<String, String> VARIABLES;

    static {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("DB_URL", "spring.datasource.url");
        variables.put("DB_USERNAME", "spring.datasource.username");
        variables.put("DB_PASSWORD", "spring.datasource.password");
        variables.put("JWT_SECRET", "platform.security.jwt.secret");
        variables.put("JWT_ISSUER", "platform.security.jwt.issuer");
        variables.put("JWT_ACCESS_TOKEN_TTL", "platform.security.jwt.access-token-ttl");
        variables.put("JWT_REFRESH_TOKEN_TTL", "platform.security.jwt.refresh-token-ttl");
        variables.put("REFRESH_TOKEN_REUSE_GRACE", "platform.security.jwt.refresh-reuse-grace");
        variables.put("LOGIN_MAX_ATTEMPTS", "platform.security.lockout.max-attempts");
        variables.put("LOGIN_LOCKOUT_DURATION", "platform.security.lockout.duration");
        variables.put("RATE_LIMIT_LOGIN_PER_MINUTE", "platform.security.rate-limit.login-per-minute");
        variables.put("RATE_LIMIT_REGISTER_PER_HOUR", "platform.security.rate-limit.register-per-hour");
        variables.put("RATE_LIMIT_REFRESH_PER_MINUTE", "platform.security.rate-limit.refresh-per-minute");
        variables.put("CORS_ALLOWED_ORIGINS", "platform.security.cors.allowed-origins");
        variables.put("MASTERY_LEARNING_RATE", "platform.mastery.learning-rate");
        variables.put("MASTERY_CONFIDENCE_SATURATION_COUNT", "platform.mastery.confidence-saturation-count");
        variables.put("MASTERY_OBJECTIVE_THRESHOLD", "platform.mastery.objective-mastery-threshold");
        VARIABLES = Collections.unmodifiableMap(variables);
    }

    public ConfigurationContract(ConfigurableEnvironment environment) {

        rejectVariablesBypassingTheContract(environment);

    }

    static void rejectVariablesBypassingTheContract(ConfigurableEnvironment environment) {

        PropertySource<?> systemEnvironment = environment.getPropertySources()
                .get(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        ConfigurationPropertySource source = systemEnvironment == null ? null : ConfigurationPropertySource.from(systemEnvironment);
        if (source == null) {
            return;
        }

        List<String> conflicts = new ArrayList<>();
        VARIABLES.forEach((variable, property) -> {
            ConfigurationProperty bypass = source.getConfigurationProperty(ConfigurationPropertyName.of(property));
            if (bypass != null) {
                conflicts.add((bypass.getOrigin() != null ? bypass.getOrigin() : "An environment variable")
                        + " sets " + property + " directly; set " + variable + " instead");
            }
        });
        require(conflicts.isEmpty(), "Unsupported configuration variables: " + String.join("; ", conflicts));

    }

    public static void require(boolean condition, String message) {

        if (!condition) {
            throw new IllegalStateException(message);
        }

    }

    /** Also rejects a placeholder left unresolved because its variable was never set. */
    public static void requireText(String value, String variable) {

        require(value != null && !value.isBlank() && !value.contains("${"), variable + " must be set");

    }

    public static void requirePositive(Duration value, String variable) {

        require(value != null && value.isPositive(), variable + " must be a positive duration, was " + value);

    }

}
