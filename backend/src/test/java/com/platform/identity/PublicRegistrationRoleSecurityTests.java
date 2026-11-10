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
 * TEACHER or ADMIN account. PARENT is the default; a PRINCIPAL signs up to a principal account holding no school
 * (M4); TEACHER and ADMIN are refused - and nothing registers without a verified email (2026-11-10). Exercises the real {@link AuthController} bean
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

    /** 2026-11-10: a registration carries a verified email's grant; this gets one through the real flow. */
    @Autowired
    private org.springframework.boot.web.servlet.FilterRegistrationBean<com.platform.VerifiedRegistrationTestFilter> verifiedRegistration;

    private RegisterRequest request(String role) {
        String email = uniqueEmail();
        return new RegisterRequest(email, "CorrectHorseBattery9!", "Test Family", UUID.randomUUID().toString(), role,
                null, null, verifiedRegistration.getFilter().grantFor(email, "en"));
    }

    @Test
    void registrationWithNoRoleDefaultsToParent() {
        RegisterRequest request = request(null);

        ResponseEntity<AuthResponse> response = authController.register(request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().platformRole()).isEqualTo(PlatformRole.PARENT);
    }

    @Test
    void registrationExplicitlyRequestingParentIsAllowed() {
        RegisterRequest request = request("PARENT");

        ResponseEntity<AuthResponse> response = authController.register(request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().platformRole()).isEqualTo(PlatformRole.PARENT);
    }

    @Test
    void registrationRequestingTeacherIsRejected() {
        RegisterRequest request = request("TEACHER");

        assertThatThrownBy(() -> authController.register(request)).isInstanceOf(AccessDeniedException.class);
    }

    /**
     * A principal may sign up publicly (M4 self-service: no administrator's approval). Since 2026-11-10 the address
     * is proven before the account exists, so the account opens at once - holding no school: a school is created
     * afterwards and its services wait for its payment (Y44). (Before: 202 and PENDING_VERIFICATION until the
     * address was confirmed.)
     */
    @Test
    void registrationRequestingPrincipalOpensAPrincipalAccountWithNoSchool() {
        RegisterRequest request = request("PRINCIPAL");

        ResponseEntity<AuthResponse> response = authController.register(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AuthResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.platformRole()).isEqualTo(PlatformRole.PRINCIPAL);
        assertThat(body.accessToken()).isNotNull();
        assertThat(accountRepository.findById(body.accountId()).orElseThrow().getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(accountRepository.findById(body.accountId()).orElseThrow().isEmailVerified()).isTrue();
    }

    @Test
    void registrationWithoutAVerifiedEmailIsRefused() {
        RegisterRequest request = new RegisterRequest(
                uniqueEmail(), "CorrectHorseBattery9!", "Test Family", UUID.randomUUID().toString(), null);

        assertThatThrownBy(() -> authController.register(request))
                .isInstanceOf(com.platform.identity.registration.RegistrationNotVerifiedException.class);
    }

    @Test
    void registrationRequestingAdminIsRejected() {
        RegisterRequest request = request("ADMIN");

        assertThatThrownBy(() -> authController.register(request)).isInstanceOf(AccessDeniedException.class);
    }

    private static String uniqueEmail() {
        return "parent-" + UUID.randomUUID() + "@example.com";
    }

}
