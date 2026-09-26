package com.platform.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Delivers {@link EmailMessage}s over SMTP after the publishing transaction commits
 * (a rolled-back registration or reset request sends nothing) and off the request
 * thread (a slow mail server never stalls a sign-in page). Delivery failures are
 * logged without the body - it may hold a single-use link - and the person can ask
 * again; nothing is retried silently.
 */
@Component
public class EmailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EmailDispatcher.class);

    private final JavaMailSender mailSender;
    private final PlatformMailProperties properties;

    public EmailDispatcher(JavaMailSender mailSender, PlatformMailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Async("emailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void deliver(EmailMessage message) {
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

    private static String domainOf(String address) {
        int at = address.indexOf('@');
        return at < 0 ? "malformed" : address.substring(at + 1);
    }

}
