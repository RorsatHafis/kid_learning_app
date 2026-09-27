package com.platform.identity;

import com.platform.TestcontainersConfiguration;
import com.platform.identity.entity.AccountStatus;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import com.platform.security.session.AccountSessionRepository;
import com.platform.security.session.AccountSessionStatus;
import com.platform.identity.web.AuthController;
import com.platform.identity.web.AuthResponse;
import com.platform.identity.web.RegisterRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mission Section 9 / 10: public self-registration must never be able to create a working
 * TEACHER, PRINCIPAL, or ADMIN account. PARENT is the default; a PRINCIPAL request only
 * files an application that an administrator must approve; TEACHER and ADMIN are refused. Exercises the real {@link AuthController} bean
 * wired by the actual Spring context, not a re-implementation of its role check.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@Tag("integration")
class PublicRegistrationRoleSecurityTests {

    @Autowired
    private AuthController authController;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountSessionRepository sessionRepository;

    @Test
    void registrationWithNoRoleDefaultsToParent() {
        RegisterRequest request = new RegisterRequest(
                uniqueEmail(), "CorrectHorseBattery9!", "Test Family", UUID.randomUUID().toString(), null);

        ResponseEntity<AuthResponse> response = authController.register(request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().platformRole()).isEqualTo(PlatformRole.PARENT);
    }

    @Test
    void registrationExplicitlyRequestingParentIsAllowed() {
        RegisterRequest request = new RegisterRequest(
                uniqueEmail(), "CorrectHorseBattery9!", "Test Family", UUID.randomUUID().toString(), "PARENT");

        ResponseEntity<AuthResponse> response = authController.register(request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().platformRole()).isEqualTo(PlatformRole.PARENT);
    }

    @Test
    void registrationRequestingTeacherIsRejected() {
        RegisterRequest request = new RegisterRequest(
                uniqueEmail(), "CorrectHorseBattery9!", "Test Family", UUID.randomUUID().toString(), "TEACHER");

        assertThatThrownBy(() -> authController.register(request)).isInstanceOf(AccessDeniedException.class);
    }

    /**
     * A principal may apply publicly, but the application grants nothing: 202, no access
     * token, no refresh session, and an account that stays PENDING_VERIFICATION (which sign-in
     * refuses) until an administrator approves it. (TD-04: this test previously expected
     * outright rejection, which predates the approval flow the product is built on - the
     * registration page's "Application received" screen and the admin's principal approvals.)
     */
    @Test
    void registrationRequestingPrincipalOnlyCreatesAnApplicationAwaitingApproval() {
        RegisterRequest request = new RegisterRequest(
                uniqueEmail(), "CorrectHorseBattery9!", "Test Family", UUID.randomUUID().toString(), "PRINCIPAL");

        ResponseEntity<AuthResponse> response = authController.register(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        AuthResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.platformRole()).isEqualTo(PlatformRole.PRINCIPAL);
        assertThat(body.accessToken()).isNull();
        assertThat(body.refreshToken()).isNull();
        assertThat(accountRepository.findById(body.accountId()).orElseThrow().getStatus())
                .isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(sessionRepository.countByAccountIdAndStatus(body.accountId(), AccountSessionStatus.ACTIVE)).isZero();
    }

    @Test
    void registrationRequestingAdminIsRejected() {
        RegisterRequest request = new RegisterRequest(
                uniqueEmail(), "CorrectHorseBattery9!", "Test Family", UUID.randomUUID().toString(), "ADMIN");

        assertThatThrownBy(() -> authController.register(request)).isInstanceOf(AccessDeniedException.class);
    }

    private static String uniqueEmail() {
        return "parent-" + UUID.randomUUID() + "@example.com";
    }

}
