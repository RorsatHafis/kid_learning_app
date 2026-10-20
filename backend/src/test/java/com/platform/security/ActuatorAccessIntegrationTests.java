package com.platform.security;

import com.platform.TestcontainersConfiguration;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Operational endpoints: health and info are public (load balancers, uptime checks); anything
 * else a profile exposes - here metrics, as staging does - is for administrators only.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.endpoints.web.exposure.include=health,info,metrics")
/**
 * Health stays public while metrics and other actuator endpoints are for administrators only.
 */
@Tag("integration")
class ActuatorAccessIntegrationTests {

    private static final String PASSWORD = "Actuator-Access-Pass-2026";

    @Value("${local.server.port}") private int port;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void metricsAreForAdministratorsOnlyWhileHealthStaysPublic() throws Exception {
        assertThat(get("/actuator/health", null)).isEqualTo(200);
        assertThat(get("/actuator/info", null)).isEqualTo(200);
        assertThat(get("/actuator/metrics", null)).isEqualTo(401);
        for (PlatformRole role : new PlatformRole[] {PlatformRole.PARENT, PlatformRole.TEACHER, PlatformRole.PRINCIPAL}) {
            assertThat(get("/actuator/metrics", tokenFor(role))).as(role.name()).isEqualTo(403);
        }
        assertThat(get("/actuator/metrics", tokenFor(PlatformRole.ADMIN))).isEqualTo(200);
    }

    private String tokenFor(PlatformRole role) throws Exception {
        String email = "actuator-" + role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.com";
        accountRepository.saveAndFlush(Account.register(email, passwordEncoder.encode(PASSWORD), role));
        HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        return JsonMapper.builder().build().readTree(res.body()).get("accessToken").asString();
    }

    private int get(String path, String token) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString()).statusCode();
    }

}
