package com.platform.common.config;

import com.platform.identity.entity.PlatformRole;
import com.platform.learner.MasteryProperties;
import com.platform.notification.PlatformMailProperties;
import com.platform.security.SecurityProperties;
import com.platform.security.ratelimit.RateLimitProperties;
import com.platform.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Battle N6: the documented environment variables are the ones the application
 * actually uses, in every profile. Each case starts a real Spring application
 * against the real application*.yaml files, with a controlled operating-system
 * environment in place of the developer's own, so Spring's genuine relaxed-binding
 * rules are exercised and the result doesn't depend on the machine.
 */
class ConfigurationContractTests {

    private static final String PROD_SECRET = "prod-secret-used-only-by-configuration-tests-0123456789";

    /** Every documented variable, each set to a non-default value. */
    private static Map<String, Object> documentedProdEnvironment() {
        Map<String, Object> env = new HashMap<>();
        env.put("JWT_SECRET", PROD_SECRET);
        env.put("JWT_ISSUER", "bk-prod-issuer");
        env.put("JWT_ACCESS_TOKEN_TTL", "7m");
        env.put("JWT_REFRESH_TOKEN_TTL", "3d");
        env.put("REFRESH_TOKEN_REUSE_GRACE", "45s");
        env.put("LOGIN_MAX_ATTEMPTS", "3");
        env.put("LOGIN_LOCKOUT_DURATION", "2m");
        env.put("RATE_LIMIT_LOGIN_PER_MINUTE", "7");
        env.put("RATE_LIMIT_REGISTER_PER_HOUR", "8");
        env.put("RATE_LIMIT_REFRESH_PER_MINUTE", "9");
        env.put("MASTERY_LEARNING_RATE", "0.5");
        env.put("MASTERY_CONFIDENCE_SATURATION_COUNT", "4");
        env.put("MASTERY_OBJECTIVE_THRESHOLD", "0.7");
        env.put("CORS_ALLOWED_ORIGINS", "https://app.bk-test.invalid");
        env.put("DB_URL", "jdbc:postgresql://db.bk-test.invalid:5432/bk");
        env.put("DB_USERNAME", "bk_prod");
        env.put("DB_PASSWORD", "bk_prod_password");
        env.put("RATE_LIMIT_RECOVERY_PER_HOUR", "6");
        env.put("MAIL_HOST", "smtp.bk-test.invalid");
        env.put("MAIL_PORT", "2525");
        env.put("MAIL_USERNAME", "bk_mailer");
        env.put("MAIL_PASSWORD", "bk_mail_password");
        env.put("MAIL_SMTP_AUTH", "false");
        env.put("MAIL_SMTP_STARTTLS", "false");
        env.put("MAIL_FROM", "no-reply@bk-test.invalid");
        env.put("APP_PUBLIC_URL", "https://app.bk-test.invalid");
        return env;
    }

    // ------------------------------------------------------------------ documented names bind

    @ParameterizedTest
    @ValueSource(strings = {"prod", "staging"})
    void everyDocumentedVariableControlsItsSetting(String profile) {
        try (ConfigurableApplicationContext ctx = start(profile, documentedProdEnvironment())) {
            SecurityProperties security = ctx.getBean(SecurityProperties.class);
            MasteryProperties mastery = ctx.getBean(MasteryProperties.class);

            assertThat(security.jwt().secret()).isEqualTo(PROD_SECRET);
            assertThat(security.jwt().issuer()).isEqualTo("bk-prod-issuer");
            assertThat(security.jwt().accessTokenTtl()).isEqualTo(Duration.ofMinutes(7));
            assertThat(security.jwt().refreshTokenTtl()).isEqualTo(Duration.ofDays(3));
            assertThat(security.jwt().refreshReuseGrace()).isEqualTo(Duration.ofSeconds(45));
            assertThat(security.lockout().maxAttempts()).isEqualTo(3);
            assertThat(security.lockout().duration()).isEqualTo(Duration.ofMinutes(2));
            RateLimitProperties rateLimit = ctx.getBean(RateLimitProperties.class);
            assertThat(rateLimit.loginPerMinute()).isEqualTo(7);
            assertThat(rateLimit.registerPerHour()).isEqualTo(8);
            assertThat(rateLimit.refreshPerMinute()).isEqualTo(9);
            assertThat(rateLimit.recoveryPerHour()).isEqualTo(6);
            PlatformMailProperties mail = ctx.getBean(PlatformMailProperties.class);
            assertThat(mail.from()).isEqualTo("no-reply@bk-test.invalid");
            assertThat(mail.link("/verify-email", "abc")).isEqualTo("https://app.bk-test.invalid/verify-email#token=abc");
            assertThat(ctx.getEnvironment().getProperty("spring.mail.host")).isEqualTo("smtp.bk-test.invalid");
            assertThat(ctx.getEnvironment().getProperty("spring.mail.port")).isEqualTo("2525");
            assertThat(ctx.getEnvironment().getProperty("spring.mail.username")).isEqualTo("bk_mailer");
            assertThat(ctx.getEnvironment().getProperty("spring.mail.password")).isEqualTo("bk_mail_password");
            assertThat(ctx.getEnvironment().getProperty("spring.mail.properties[mail.smtp.auth]")).isEqualTo("false");
            assertThat(ctx.getEnvironment().getProperty("spring.mail.properties[mail.smtp.starttls.enable]")).isEqualTo("false");
            assertThat(ctx.getEnvironment().getProperty("spring.mail.properties[mail.smtp.starttls.required]")).isEqualTo("false");
            assertThat(mastery.learningRate()).isEqualByComparingTo("0.5");
            assertThat(mastery.confidenceSaturationCount()).isEqualTo(4);
            assertThat(mastery.objectiveMasteryThreshold()).isEqualByComparingTo("0.7");
            assertThat(ctx.getEnvironment().getProperty("platform.security.cors.allowed-origins"))
                    .isEqualTo("https://app.bk-test.invalid");
            assertThat(ctx.getEnvironment().getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://db.bk-test.invalid:5432/bk");
            assertThat(ctx.getEnvironment().getProperty("spring.datasource.username")).isEqualTo("bk_prod");
            assertThat(ctx.getEnvironment().getProperty("spring.datasource.password")).isEqualTo("bk_prod_password");
        }
    }

    @Test
    void configuredIssuerAndTtlAppearInIssuedAccessTokens() {
        try (ConfigurableApplicationContext ctx = start("prod", documentedProdEnvironment())) {
            JwtService.IssuedToken token = ctx.getBean(JwtService.class).issueAccessToken(UUID.randomUUID(), PlatformRole.PARENT);
            String claims = new String(Base64.getUrlDecoder().decode(token.token().split("\\.")[1]), StandardCharsets.UTF_8);

            assertThat(claims).contains("\"iss\":\"bk-prod-issuer\"");
            long iat = Long.parseLong(claims.replaceAll(".*\"iat\":(\\d+).*", "$1"));
            long exp = Long.parseLong(claims.replaceAll(".*\"exp\":(\\d+).*", "$1"));
            assertThat(exp - iat).isEqualTo(Duration.ofMinutes(7).toSeconds());
        }
    }

    @Test
    void optionalSettingsFallBackToTheirDocumentedDefaults() {
        Map<String, Object> env = new HashMap<>();
        env.put("JWT_SECRET", PROD_SECRET);
        env.put("JWT_ISSUER", "bk-prod-issuer");
        env.put("CORS_ALLOWED_ORIGINS", "https://app.bk-test.invalid");
        env.put("MAIL_HOST", "smtp.bk-test.invalid");
        env.put("MAIL_FROM", "no-reply@bk-test.invalid");
        env.put("APP_PUBLIC_URL", "https://app.bk-test.invalid");

        try (ConfigurableApplicationContext ctx = start("prod", env)) {
            SecurityProperties security = ctx.getBean(SecurityProperties.class);
            MasteryProperties mastery = ctx.getBean(MasteryProperties.class);

            assertThat(security.jwt().accessTokenTtl()).isEqualTo(Duration.ofMinutes(15));
            assertThat(security.jwt().refreshTokenTtl()).isEqualTo(Duration.ofDays(30));
            assertThat(security.jwt().refreshReuseGrace()).isEqualTo(Duration.ofSeconds(30));
            assertThat(security.lockout().maxAttempts()).isEqualTo(5);
            assertThat(security.lockout().duration()).isEqualTo(Duration.ofMinutes(15));
            RateLimitProperties rateLimit = ctx.getBean(RateLimitProperties.class);
            assertThat(rateLimit.loginPerMinute()).isEqualTo(20);
            assertThat(rateLimit.registerPerHour()).isEqualTo(20);
            assertThat(rateLimit.refreshPerMinute()).isEqualTo(60);
            assertThat(rateLimit.recoveryPerHour()).isEqualTo(10);
            // Mail is authenticated and encrypted unless explicitly turned off.
            assertThat(ctx.getEnvironment().getProperty("spring.mail.port")).isEqualTo("587");
            assertThat(ctx.getEnvironment().getProperty("spring.mail.properties[mail.smtp.auth]")).isEqualTo("true");
            assertThat(ctx.getEnvironment().getProperty("spring.mail.properties[mail.smtp.starttls.enable]")).isEqualTo("true");
            assertThat(ctx.getEnvironment().getProperty("spring.mail.properties[mail.smtp.starttls.required]")).isEqualTo("true");
            assertThat(mastery.learningRate()).isEqualByComparingTo("0.2");
            assertThat(mastery.confidenceSaturationCount()).isEqualTo(10);
            assertThat(mastery.objectiveMasteryThreshold()).isEqualByComparingTo("0.8");
        }
    }

    // ------------------------------------------------------------------ required values fail loudly

    @ParameterizedTest
    @CsvSource({"prod,JWT_SECRET", "prod,JWT_ISSUER", "staging,JWT_SECRET", "staging,JWT_ISSUER",
            "prod,MAIL_FROM", "prod,APP_PUBLIC_URL", "staging,MAIL_FROM", "staging,APP_PUBLIC_URL"})
    void missingRequiredSecurityValueFailsStartup(String profile, String variable) {
        Map<String, Object> env = documentedProdEnvironment();
        env.remove(variable);

        assertThatThrownBy(() -> start(profile, env)).hasStackTraceContaining(variable);
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "staging"})
    void missingValuesNeverFallBackToDevelopmentSettings(String profile) {
        Map<String, Object> env = documentedProdEnvironment();
        env.remove("CORS_ALLOWED_ORIGINS");
        env.remove("MAIL_HOST");

        try (ConfigurableApplicationContext ctx = start(profile, env)) {
            // Resolving it is what SecurityConfig does at startup; it must fail, not yield localhost.
            assertThatThrownBy(() -> ctx.getEnvironment().getProperty("platform.security.cors.allowed-origins"))
                    .hasMessageContaining("CORS_ALLOWED_ORIGINS");
            // Likewise the mail autoconfiguration: no silent fallback to a local mail catcher.
            assertThatThrownBy(() -> ctx.getEnvironment().getProperty("spring.mail.host"))
                    .hasMessageContaining("MAIL_HOST");
        }
        env.remove("JWT_SECRET");
        env.remove("JWT_ISSUER");
        assertThatThrownBy(() -> start(profile, env)).hasStackTraceContaining("JWT_SECRET");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "JWT_SECRET|too-short-secret|JWT_SECRET",
            "JWT_ISSUER|'   '|JWT_ISSUER",
            "JWT_ACCESS_TOKEN_TTL|0s|JWT_ACCESS_TOKEN_TTL",
            "JWT_ACCESS_TOKEN_TTL|-5m|JWT_ACCESS_TOKEN_TTL",
            "JWT_ACCESS_TOKEN_TTL|fifteen|access-token-ttl",
            "JWT_REFRESH_TOKEN_TTL|0d|JWT_REFRESH_TOKEN_TTL",
            "REFRESH_TOKEN_REUSE_GRACE|-1s|REFRESH_TOKEN_REUSE_GRACE",
            "REFRESH_TOKEN_REUSE_GRACE|10m|REFRESH_TOKEN_REUSE_GRACE",
            "LOGIN_MAX_ATTEMPTS|0|LOGIN_MAX_ATTEMPTS",
            "LOGIN_MAX_ATTEMPTS|-1|LOGIN_MAX_ATTEMPTS",
            "RATE_LIMIT_LOGIN_PER_MINUTE|0|RATE_LIMIT_LOGIN_PER_MINUTE",
            "RATE_LIMIT_REGISTER_PER_HOUR|0|RATE_LIMIT_REGISTER_PER_HOUR",
            "RATE_LIMIT_REFRESH_PER_MINUTE|0|RATE_LIMIT_REFRESH_PER_MINUTE",
            "RATE_LIMIT_RECOVERY_PER_HOUR|0|RATE_LIMIT_RECOVERY_PER_HOUR",
            "MAIL_FROM|no-reply|MAIL_FROM",
            "APP_PUBLIC_URL|app.bk-test.invalid|APP_PUBLIC_URL",
            "APP_PUBLIC_URL|https://app.bk-test.invalid/some/path|APP_PUBLIC_URL",
            "APP_PUBLIC_URL|ftp://app.bk-test.invalid|APP_PUBLIC_URL",
            "LOGIN_LOCKOUT_DURATION|0s|LOGIN_LOCKOUT_DURATION",
            "LOGIN_LOCKOUT_DURATION|-15m|LOGIN_LOCKOUT_DURATION",
            "MASTERY_LEARNING_RATE|0|MASTERY_LEARNING_RATE",
            "MASTERY_LEARNING_RATE|1.5|MASTERY_LEARNING_RATE",
            "MASTERY_CONFIDENCE_SATURATION_COUNT|0|MASTERY_CONFIDENCE_SATURATION_COUNT",
            "MASTERY_OBJECTIVE_THRESHOLD|0|MASTERY_OBJECTIVE_THRESHOLD",
            "MASTERY_OBJECTIVE_THRESHOLD|1.2|MASTERY_OBJECTIVE_THRESHOLD"})
    void invalidValueFailsStartupNamingTheVariable(String variable, String value, String expectedInMessage) {
        Map<String, Object> env = documentedProdEnvironment();
        env.put(variable, value);

        assertThatThrownBy(() -> start("prod", env)).hasStackTraceContaining(expectedInMessage);
    }

    // ------------------------------------------------------------------ one name per setting

    @ParameterizedTest
    @CsvSource({
            "PLATFORM_SECURITY_JWT_SECRET,JWT_SECRET",
            "PLATFORM_SECURITY_JWT_ISSUER,JWT_ISSUER",
            "PLATFORM_SECURITY_JWT_ACCESS_TOKEN_TTL,JWT_ACCESS_TOKEN_TTL",
            "PLATFORM_SECURITY_JWT_ACCESSTOKENTTL,JWT_ACCESS_TOKEN_TTL",
            "PLATFORM_SECURITY_LOCKOUT_MAX_ATTEMPTS,LOGIN_MAX_ATTEMPTS",
            "PLATFORM_SECURITY_RATELIMIT_LOGINPERMINUTE,RATE_LIMIT_LOGIN_PER_MINUTE",
            "PLATFORM_SECURITY_CORS_ALLOWED_ORIGINS,CORS_ALLOWED_ORIGINS",
            "PLATFORM_MASTERY_LEARNING_RATE,MASTERY_LEARNING_RATE",
            "SPRING_DATASOURCE_URL,DB_URL",
            "SPRING_MAIL_HOST,MAIL_HOST",
            "SPRING_MAIL_PASSWORD,MAIL_PASSWORD",
            "PLATFORM_MAIL_PUBLICURL,APP_PUBLIC_URL",
            "SPRING_DATASOURCE_PASSWORD,DB_PASSWORD"})
    void anUndocumentedVariableCannotSilentlyOverrideADocumentedOne(String bypass, String documented) {
        Map<String, Object> env = documentedProdEnvironment();
        env.put(bypass, env.get(documented));

        for (String profile : new String[] {"prod", "local", "test"}) {
            assertThatThrownBy(() -> start(profile, env)).as(profile)
                    .hasStackTraceContaining(bypass)
                    .hasStackTraceContaining("set " + documented + " instead");
        }
    }

    @Test
    void anExplicitCommandLineOverrideStillApplies() {
        try (ConfigurableApplicationContext ctx = start("prod", documentedProdEnvironment(),
                "--platform.security.jwt.issuer=bk-command-line-issuer")) {
            assertThat(ctx.getBean(SecurityProperties.class).jwt().issuer()).isEqualTo("bk-command-line-issuer");
        }
    }

    // ------------------------------------------------------------------ local and test profiles

    @Test
    void localProfileRunsWithDevelopmentDefaults() {
        assumeFalse(new File(".env").exists(), "the local profile imports ./.env, which would change these defaults");

        try (ConfigurableApplicationContext ctx = start("local", Map.of())) {
            SecurityProperties security = ctx.getBean(SecurityProperties.class);

            assertThat(security.jwt().secret()).startsWith("local-development-only-secret");
            assertThat(security.jwt().issuer()).isEqualTo("yulme-api");
            assertThat(security.lockout().maxAttempts()).isEqualTo(5);
            assertThat(ctx.getEnvironment().getProperty("platform.security.cors.allowed-origins")).isEqualTo("http://localhost:5173");
            assertThat(ctx.getEnvironment().getProperty("spring.datasource.username")).isEqualTo("learning_platform");
            // Mailpit, without authentication or TLS.
            assertThat(ctx.getEnvironment().getProperty("spring.mail.host")).isEqualTo("localhost");
            assertThat(ctx.getEnvironment().getProperty("spring.mail.port")).isEqualTo("1025");
            assertThat(ctx.getEnvironment().getProperty("spring.mail.properties[mail.smtp.starttls.required]")).isEqualTo("false");
            assertThat(ctx.getBean(PlatformMailProperties.class).publicUrl()).isEqualTo("http://localhost:5173");
        }
    }

    @Test
    void localProfileStillHonoursTheDocumentedVariables() {
        try (ConfigurableApplicationContext ctx = start("local", documentedProdEnvironment())) {
            SecurityProperties security = ctx.getBean(SecurityProperties.class);

            assertThat(security.jwt().issuer()).isEqualTo("bk-prod-issuer");
            assertThat(security.lockout().maxAttempts()).isEqualTo(3);
            assertThat(ctx.getBean(MasteryProperties.class).learningRate()).isEqualByComparingTo("0.5");
            assertThat(ctx.getEnvironment().getProperty("platform.security.cors.allowed-origins")).isEqualTo("https://app.bk-test.invalid");
        }
    }

    @Test
    void localDatasourceCredentialsPreferTheDocumentedNamesOverTheComposeDatabaseNames() {
        Map<String, Object> both = new HashMap<>(Map.of("DB_USERNAME", "from_db_username", "DB_PASSWORD", "from_db_password",
                "POSTGRES_USER", "from_postgres_user", "POSTGRES_PASSWORD", "from_postgres_password"));
        try (ConfigurableApplicationContext ctx = start("local", both)) {
            assertThat(ctx.getEnvironment().getProperty("spring.datasource.username")).isEqualTo("from_db_username");
            assertThat(ctx.getEnvironment().getProperty("spring.datasource.password")).isEqualTo("from_db_password");
        }
        try (ConfigurableApplicationContext ctx = start("local",
                Map.of("POSTGRES_USER", "from_postgres_user", "POSTGRES_PASSWORD", "from_postgres_password"))) {
            assertThat(ctx.getEnvironment().getProperty("spring.datasource.username")).isEqualTo("from_postgres_user");
            assertThat(ctx.getEnvironment().getProperty("spring.datasource.password")).isEqualTo("from_postgres_password");
        }
    }

    @Test
    void testProfileIsDeterministicWhateverTheMachineEnvironment() {
        try (ConfigurableApplicationContext ctx = start("test", documentedProdEnvironment())) {
            SecurityProperties security = ctx.getBean(SecurityProperties.class);

            assertThat(security.jwt().secret()).isEqualTo("test-secret-key-for-automated-tests-only-0123456789ab");
            assertThat(security.jwt().issuer()).isEqualTo("yulme-test");
            assertThat(security.jwt().accessTokenTtl()).isEqualTo(Duration.ofMinutes(15));
            assertThat(security.lockout().maxAttempts()).isEqualTo(5);
            assertThat(ctx.getBean(MasteryProperties.class).learningRate()).isEqualByComparingTo(new BigDecimal("0.2"));
            assertThat(ctx.getEnvironment().getProperty("platform.security.cors.allowed-origins")).isEqualTo("http://localhost:5173");
        }
    }

    // ------------------------------------------------------------------ harness

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({SecurityProperties.class, MasteryProperties.class, RateLimitProperties.class,
            PlatformMailProperties.class})
    @Import({ConfigurationContract.class, JwtService.class})
    static class ConfigurationOnly {
    }

    /** Starts the application's configuration layer with {@code env} as the only OS environment. */
    private static ConfigurableApplicationContext start(String profile, Map<String, Object> env, String... args) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, env));
        environment.setActiveProfiles(profile);

        SpringApplication application = new SpringApplication(ConfigurationOnly.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setEnvironment(environment);
        application.setBannerMode(org.springframework.boot.Banner.Mode.OFF);
        return application.run(args);
    }

}
