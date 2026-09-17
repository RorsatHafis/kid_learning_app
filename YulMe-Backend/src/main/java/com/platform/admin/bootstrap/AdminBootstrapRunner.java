package com.platform.admin.bootstrap;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import com.platform.identity.service.PasswordPolicy;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
public class AdminBootstrapRunner implements CommandLineRunner {
    private final AdminBootstrapProperties properties;
    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuditService auditService;

    public AdminBootstrapRunner(AdminBootstrapProperties properties, AccountRepository accounts,
                                PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
                                AuditService auditService) {
        this.properties = properties;
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!properties.isEnabled()) return;
        if (properties.getEmail() == null || properties.getEmail().isBlank()
                || properties.getPassword() == null || properties.getPassword().isBlank()) {
            throw new IllegalStateException("Bootstrap admin requires email and password");
        }
        if (accounts.findAll().stream().anyMatch(a -> a.getPlatformRole() == PlatformRole.ADMIN)) return;
        passwordPolicy.validate(properties.getPassword());
        Account admin = accounts.saveAndFlush(Account.register(
                properties.getEmail(), passwordEncoder.encode(properties.getPassword()), PlatformRole.ADMIN));
        auditService.record(new RecordAuditEvent(null, admin.getId(), AuditActorType.SYSTEM,
                "ADMIN_BOOTSTRAPPED", "ACCOUNT", admin.getId(), Map.of("platformRole", "ADMIN")));
    }
}
