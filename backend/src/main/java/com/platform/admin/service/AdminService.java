package com.platform.admin.service;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.common.web.PageResponse;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.common.web.SearchText;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.entity.AccountStatus;
import com.platform.identity.repository.AccountRepository;
import com.platform.identity.service.AccountRecoveryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class AdminService {
    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final AccountRecoveryService recovery;
    private final AuditService auditService;
    private final SecureRandom random = new SecureRandom();

    private static final List<PlatformRole> STAFF_ROLES = List.of(PlatformRole.TEACHER, PlatformRole.PRINCIPAL, PlatformRole.ADMIN);

    public AdminService(AccountRepository accounts, PasswordEncoder passwordEncoder,
                        AccountRecoveryService recovery, AuditService auditService) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.recovery = recovery;
        this.auditService = auditService;
    }

    /**
     * Creates a TEACHER, PRINCIPAL or ADMIN account and emails its owner a single-use link to
     * choose their own password (TD-22): the account starts with a random password nobody knows,
     * so the administrator never learns or hands over anyone's password. Y14: a platform needs
     * more than the one administrator created at first start - for cover, and because a second
     * person's review (PD-20) needs a second administrator. Audited like every provisioning.
     */
    @Transactional
    public Account createStaffAccount(UUID adminId, String email, PlatformRole role, String requestedIp) {
        requireAdmin(adminId);
        if (!STAFF_ROLES.contains(role)) {
            throw new IllegalArgumentException("Staff accounts are teachers, principals or administrators; families sign up themselves");
        }
        String normalized = Account.normalize(email);
        if (accounts.findByNormalizedEmail(normalized).isPresent()) {
            throw new IllegalStateException("An account already exists for this email address");
        }
        byte[] secret = new byte[32];
        random.nextBytes(secret);
        Account account = accounts.saveAndFlush(Account.register(email.strip(),
                passwordEncoder.encode(Base64.getEncoder().encodeToString(secret)), role));
        auditService.record(new RecordAuditEvent(
                null, adminId, AuditActorType.ADMIN, "ACCOUNT_PROVISIONED", "ACCOUNT",
                account.getId(), java.util.Map.of("platformRole", role.name())));
        String roleName = switch (role) {
            case PRINCIPAL -> "a principal";
            case ADMIN -> "an administrator";
            default -> "a teacher";
        };
        recovery.sendAccountSetup(account, new AccountRecoveryService.SetupInvitation(
                "Your Bookeyond Kid account is ready to set up",
                "A Bookeyond Kid administrator has created " + roleName + " account for you.",
                ""), requestedIp);
        return account;
    }

    @Transactional(readOnly = true)
    public PageResponse<Account> listStaff(UUID adminId, String emailText, PlatformRole role, PageResponse.Request request) {
        requireAdmin(adminId);
        if (role == PlatformRole.PARENT) {
            throw new IllegalArgumentException("Families aren't staff: find a parent's account by their email address");
        }
        List<PlatformRole> roles = role == null ? STAFF_ROLES : List.of(role);
        var page = accounts.searchByRoles(roles, SearchText.containsPattern(emailText),
                PageRequest.of(request.page(), request.size()));
        return new PageResponse<>(page.getContent(), request.page(), request.size(), page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<Account> listPendingPrincipals(UUID adminId) {
        requireAdmin(adminId);
        return accounts.findByPlatformRoleAndStatusOrderByCreatedAtAsc(PlatformRole.PRINCIPAL, AccountStatus.PENDING_VERIFICATION);
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
