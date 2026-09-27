package com.platform.identity;

import com.platform.TestcontainersConfiguration;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sign-in security events an operator must be able to see: every failed sign-in is logged
 * against the account (never the address or the password tried), and a lockout is logged
 * and recorded once in the audit log, where administrators read it.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
@Tag("integration")
class SignInSecurityEventsIntegrationTests {

    private static final String PASSWORD = "Sign-In-Events-Pass-2026";
    private static final String WRONG = "Not-The-Password-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void repeatedFailuresLockTheAccountAndTheLockIsLoggedAndAuditedOnce(CapturedOutput output) throws Exception {
        String email = "events-" + UUID.randomUUID() + "@example.com";
        Account account = accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), PlatformRole.PARENT));

        for (int i = 0; i < 5; i++) {
            assertThat(login(email, WRONG)).isEqualTo(401);
        }
        // Locked: even the right password is refused, and nothing more is recorded.
        assertThat(login(email, PASSWORD)).isEqualTo(423);
        assertThat(login(email, WRONG)).isEqualTo(423);

        List<Map<String, Object>> locks = jdbc.queryForList(
                "select actor_type, metadata::text as metadata from audit_events where action = 'ACCOUNT_LOCKED' and resource_id = ?",
                account.getId());
        assertThat(locks).hasSize(1);
        assertThat(locks.getFirst().get("actor_type")).isEqualTo("SYSTEM");
        assertThat((String) locks.getFirst().get("metadata")).contains("lockedUntil", "failedAttempts");

        assertThat(output).contains("Sign-in failed for account " + account.getId());
        assertThat(output).contains("Account " + account.getId() + " locked");
        assertThat(output).doesNotContain(email).doesNotContain(WRONG).doesNotContain(PASSWORD);
    }

    @Test
    void failuresForUnknownAddressesAreLoggedWithoutTheAddress(CapturedOutput output) throws Exception {
        String unknown = "nobody-" + UUID.randomUUID() + "@example.com";

        assertThat(login(unknown, WRONG)).isEqualTo(401);

        assertThat(output).contains("Sign-in failed for an unknown address");
        assertThat(output).doesNotContain(unknown);
    }

    private int login(String email, String password) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                        .build(), HttpResponse.BodyHandlers.ofString()).statusCode();
    }

}
