package com.platform.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Email over Brevo's HTTPS API when MAIL_BREVO_API_KEY is set (hosts that block SMTP), SMTP otherwise - real delivery
 * either way; a refused or unreachable API is logged, never thrown into the request that asked for the email.
 */
class EmailDispatcherBrevoTests {

    private HttpServer server;
    private final AtomicReference<String> key = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private int status = 201;
    private final PlatformMailProperties mail = new PlatformMailProperties("hello@bookeyond.example", "https://app.bookeyond.example");

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v3/smtp/email", exchange -> {
            key.set(exchange.getRequestHeaders().getFirst("api-key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] answer = "{\"messageId\":\"<m@brevo>\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v3/smtp/email";
    }

    @Test
    void sendsOverTheBrevoApiWithTheKeyAndTheMessage() {
        JavaMailSender smtp = mock(JavaMailSender.class);
        new EmailDispatcher(smtp, mail, "test-key", url())
                .deliver(new EmailMessage("parent@example.com", "Reset your Bookeyond Kid password", "Open https://app/x?t=1"));

        assertThat(key.get()).isEqualTo("test-key");
        assertThat(body.get())
                .contains("\"sender\":{\"email\":\"hello@bookeyond.example\"}")
                .contains("\"to\":[{\"email\":\"parent@example.com\"}]")
                .contains("\"subject\":\"Reset your Bookeyond Kid password\"")
                .contains("\"textContent\":\"Open https://app/x?t=1\"");
        verifyNoInteractions(smtp);
    }

    @Test
    void aRefusalIsLoggedNotThrown() {
        status = 401;
        assertThatCode(() -> new EmailDispatcher(mock(JavaMailSender.class), mail, "bad-key", url())
                .deliver(new EmailMessage("parent@example.com", "Confirm", "x"))).doesNotThrowAnyException();
        assertThat(key.get()).isEqualTo("bad-key");
    }

    @Test
    void withoutAKeyItUsesSmtp() {
        JavaMailSender smtp = mock(JavaMailSender.class);
        new EmailDispatcher(smtp, mail, "", url()).deliver(new EmailMessage("parent@example.com", "Confirm", "x"));
        verify(smtp).send(any(SimpleMailMessage.class));
        assertThat(body.get()).isNull();
    }
}
