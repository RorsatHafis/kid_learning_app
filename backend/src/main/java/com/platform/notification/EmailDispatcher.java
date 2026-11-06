package com.platform.notification;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

/**
 * Delivers {@link EmailMessage}s after the publishing transaction commits
 * (a rolled-back registration or reset request sends nothing) and off the request
 * thread (a slow mail server never stalls a sign-in page). Delivery failures are
 * logged without the body - it may hold a single-use link - and the person can ask
 * again; nothing is retried silently.
 *
 * <p>Over SMTP by default. When {@code MAIL_BREVO_API_KEY} is set, over Brevo's HTTPS email API instead - for hosts
 * that block outgoing SMTP (Railway's trial and Hobby plans, found on the live deployment 2026-11-06). Either way it
 * is real delivery from {@code MAIL_FROM}; there is no pretend transport.
 */
@Component
public class EmailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EmailDispatcher.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JavaMailSender mailSender;
    private final PlatformMailProperties properties;
    private final String brevoKey;
    private final URI brevoUrl;
    private final HttpClient http;

    @Autowired
    public EmailDispatcher(JavaMailSender mailSender, PlatformMailProperties properties,
                           @Value("${platform.mail-api.brevo-key:}") String brevoKey,
                           @Value("${platform.mail-api.brevo-url:https://api.brevo.com/v3/smtp/email}") String brevoUrl) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.brevoKey = brevoKey == null ? "" : brevoKey.trim();
        this.brevoUrl = URI.create(brevoUrl);
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public EmailDispatcher(JavaMailSender mailSender, PlatformMailProperties properties) {
        this(mailSender, properties, "", "https://api.brevo.com/v3/smtp/email");
    }

    @Async("emailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void deliver(EmailMessage message) {
        if (!brevoKey.isEmpty()) {
            deliverByBrevo(message);
            return;
        }
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(properties.from());
        mail.setTo(message.to());
        mail.setSubject(message.subject());
        mail.setText(message.body());
        try {
            mailSender.send(mail);
            log.info("Sent \"{}\" to a {} address", message.subject(), domainOf(message.to()));
        } catch (MailException e) {
            log.error("Could not send \"{}\" to a {} address: {}", message.subject(), domainOf(message.to()), e.getMessage());
        }
    }

    private void deliverByBrevo(EmailMessage message) {
        String body = JSON.writeValueAsString(Map.of(
                "sender", Map.of("email", properties.from()),
                "to", List.of(Map.of("email", message.to())),
                "subject", message.subject(),
                "textContent", message.body()));
        HttpRequest request = HttpRequest.newBuilder(brevoUrl)
                .timeout(Duration.ofSeconds(20))
                .header("api-key", brevoKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 == 2) {
                log.info("Sent \"{}\" to a {} address (Brevo API)", message.subject(), domainOf(message.to()));
            } else {
                // Brevo's error says what is wrong (an unverified sender, a bad key); it never echoes the message.
                String reason = response.body() == null ? "" : response.body();
                log.error("Could not send \"{}\" to a {} address: Brevo API answered {} {}", message.subject(),
                        domainOf(message.to()), response.statusCode(), reason.length() > 300 ? reason.substring(0, 300) : reason);
            }
        } catch (IOException e) {
            log.error("Could not send \"{}\" to a {} address: Brevo API unreachable: {}", message.subject(), domainOf(message.to()), e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Could not send \"{}\" to a {} address: interrupted", message.subject(), domainOf(message.to()));
        }
    }

    private static String domainOf(String address) {
        int at = address.indexOf('@');
        return at < 0 ? "malformed" : address.substring(at + 1);
    }

}
