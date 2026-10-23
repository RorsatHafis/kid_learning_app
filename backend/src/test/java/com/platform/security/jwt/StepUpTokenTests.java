package com.platform.security.jwt;

import com.platform.identity.entity.PlatformRole;
import com.platform.security.SecurityProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M8: a step-up proof is valid only for the account and the sign-in that made it, only until it expires, and it never
 * stands in for an access token (nor an access token for it).
 */
class StepUpTokenTests {

    private static final String SECRET = "step-up-tests-secret-that-is-long-enough-0123456789";

    private final JwtService jwt = service("bookeyond-test");

    private static JwtService service(String issuer) {
        return new JwtService(new SecurityProperties(
                new SecurityProperties.Jwt(SECRET, issuer, Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofSeconds(30)),
                new SecurityProperties.Lockout(5, Duration.ofMinutes(15))));
    }

    @Test
    void validOnlyForItsAccountAndSignIn() {
        UUID account = UUID.randomUUID();
        UUID signIn = UUID.randomUUID();
        String proof = jwt.issueStepUp(account, signIn, Duration.ofMinutes(5)).token();

        assertThat(jwt.isValidStepUp(proof, account, signIn)).isTrue();
        assertThat(jwt.isValidStepUp(proof, UUID.randomUUID(), signIn)).as("another account").isFalse();
        assertThat(jwt.isValidStepUp(proof, account, UUID.randomUUID())).as("another sign-in").isFalse();
        assertThat(jwt.isValidStepUp(proof, account, null)).as("a sign-in without a session").isFalse();
        assertThat(jwt.isValidStepUp(null, account, signIn)).isFalse();
        assertThat(jwt.isValidStepUp("not-a-token", account, signIn)).isFalse();
        assertThat(service("another-environment").isValidStepUp(proof, account, signIn)).as("another issuer").isFalse();
    }

    @Test
    void expiresAndIsNeverAnAccessTokenNorTheOtherWayRound() {
        UUID account = UUID.randomUUID();
        UUID signIn = UUID.randomUUID();
        String expired = jwt.issueStepUp(account, signIn, Duration.ofSeconds(-1)).token();
        assertThat(jwt.isValidStepUp(expired, account, signIn)).isFalse();

        String proof = jwt.issueStepUp(account, signIn, Duration.ofMinutes(5)).token();
        assertThatThrownBy(() -> jwt.validateAndGetPrincipal(proof)).isInstanceOf(InvalidTokenException.class);

        String access = jwt.issueAccessToken(account, PlatformRole.ADMIN, signIn).token();
        assertThat(jwt.isValidStepUp(access, account, signIn)).as("an access token is no proof").isFalse();
    }

}
