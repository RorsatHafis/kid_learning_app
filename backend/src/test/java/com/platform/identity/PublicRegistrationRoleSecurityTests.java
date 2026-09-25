package com.platform.identity;

import com.platform.TestcontainersConfiguration;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.web.AuthController;
import com.platform.identity.web.AuthResponse;
import com.platform.identity.web.RegisterRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mission Section 9 / 10: public self-registration must never be able to create a
 * TEACHER, PRINCIPAL, or ADMIN account - only PARENT, either explicitly requested or
 * (the common case) left unspecified. Exercises the real {@link AuthController} bean
 * wired by the actual Spring context, not a re-implementation of its role check.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
@Tag("integration")
class PublicRegistrationRoleSecurityTests {

    @Autowired
    private AuthController authController;

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

    @Test
    void registrationRequestingPrincipalIsRejected() {
        RegisterRequest request = new RegisterRequest(
                uniqueEmail(), "CorrectHorseBattery9!", "Test Family", UUID.randomUUID().toString(), "PRINCIPAL");

        assertThatThrownBy(() -> authController.register(request)).isInstanceOf(AccessDeniedException.class);
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
