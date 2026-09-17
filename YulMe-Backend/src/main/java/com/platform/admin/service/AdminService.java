package com.platform.admin.service;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.entity.AccountStatus;
import com.platform.identity.repository.AccountRepository;
import com.platform.identity.service.PasswordPolicy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AdminService {
    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuditService auditService;

    public AdminService(AccountRepository accounts, PasswordEncoder passwordEncoder,
                        PasswordPolicy passwordPolicy, AuditService auditService) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.auditService = auditService;
    }

    @Transactional
    public Account createStaffAccount(UUID adminId, String email, String password,
                                      PlatformRole role) {
        requireAdmin(adminId);
        if (role != PlatformRole.TEACHER && role != PlatformRole.PRINCIPAL) {
            throw new IllegalArgumentException("Admin provisioning supports TEACHER or PRINCIPAL accounts only");
        }
        passwordPolicy.validate(password);
        String normalized = Account.normalize(email);
        if (accounts.findByNormalizedEmail(normalized).isPresent()) {
            throw new IllegalArgumentException("An account already exists for this email address");
        }
        Account account = accounts.saveAndFlush(
                Account.register(email, passwordEncoder.encode(password), role));
        auditService.record(new RecordAuditEvent(
                null, adminId, AuditActorType.ADMIN, "ACCOUNT_PROVISIONED", "ACCOUNT",
                account.getId(), java.util.Map.of("platformRole", role.name())));
        return account;
    }

    @Transactional(readOnly = true)
    public List<Account> listStaff(UUID adminId) {
        requireAdmin(adminId);
        return accounts.findAll().stream()
                .filter(a -> a.getPlatformRole() != PlatformRole.PARENT)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Account> listPendingPrincipals(UUID adminId) {
        requireAdmin(adminId);
        return accounts.findAll().stream()
                .filter(a -> a.getPlatformRole() == PlatformRole.PRINCIPAL)
                .filter(a -> a.getStatus() == AccountStatus.PENDING_VERIFICATION)
                .toList();
    }

    @Transactional
    public Account approvePrincipal(UUID adminId, UUID principalId) {
        requireAdmin(adminId);
        Account account = accounts.findById(principalId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", principalId));
        if (account.getPlatformRole() != PlatformRole.PRINCIPAL) {
            throw new IllegalArgumentException("Only PRINCIPAL accounts can be approved here");
        }
        account.approve();
        auditService.record(new RecordAuditEvent(
                null, adminId, AuditActorType.ADMIN, "PRINCIPAL_APPROVED", "ACCOUNT",
                account.getId(), java.util.Map.of("platformRole", "PRINCIPAL")));
        return account;
    }

    private void requireAdmin(UUID accountId) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        if (account.getPlatformRole() != PlatformRole.ADMIN) {
            throw new AccessDeniedException("Admin access required");
        }
    }
}
