package com.platform.admin.service;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.AccountStatus;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import com.platform.security.session.AccountSessionService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Running the platform (CLAUDE.md §22): reading the audit log, and suspending or reinstating
 * an account. ADMIN only; every change is itself audited.
 *
 * <p>Suspension is for a departed staff member or a compromised account. It changes the
 * account's status to DISABLED and ends every session at once: sign-in, refresh and emailed
 * links all refuse a disabled account, and a still-valid access token expires within its
 * short lifetime (PD-02). Nothing is deleted. Only ACTIVE accounts can be suspended - a
 * principal awaiting approval is simply not approved - so reinstating (DISABLED → ACTIVE)
 * never skips an approval. Administrators can't be suspended here: that would let one admin
 * lock out the others, or the last one lock out everyone.
 */
@Service
public class AdminOperationsService {

    static final int MAX_PAGE = 100;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AccountRepository accounts;
    private final AccountSessionService sessions;
    private final AuditService auditService;
    private final NamedParameterJdbcTemplate jdbc;

    public AdminOperationsService(AccountRepository accounts, AccountSessionService sessions, AuditService auditService,
                                  NamedParameterJdbcTemplate jdbc) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.auditService = auditService;
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------ audit log

    public record AuditEntry(UUID id, Instant occurredAt, UUID actorId, String actorEmail, String actorType, String action,
                             String resourceType, UUID resourceId, Map<String, Object> metadata, String correlationId) {
    }

    /** One page, newest first. {@code before}/{@code beforeId} continue from the last entry of the previous page. */
    public record AuditPage(List<AuditEntry> entries, Instant nextBefore, UUID nextBeforeId) {
    }

    @Transactional(readOnly = true)
    public AuditPage auditLog(UUID adminId, String action, UUID actorId, UUID resourceId, Instant before, UUID beforeId,
                              int limit) {
        requireAdmin(adminId);
        int size = Math.clamp(limit, 1, MAX_PAGE);
        StringBuilder sql = new StringBuilder("""
                select e.id, e.occurred_at, e.actor_id, a.email as actor_email, e.actor_type, e.action, e.resource_type,
                       e.resource_id, e.metadata::text as metadata, e.correlation_id
                from audit_events e left join accounts a on a.id = e.actor_id
                where true""");
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", size + 1);
        if (action != null && !action.isBlank()) {
            sql.append(" and e.action = :action");
            params.addValue("action", action.strip());
        }
        if (actorId != null) {
            sql.append(" and e.actor_id = :actorId");
            params.addValue("actorId", actorId);
        }
        if (resourceId != null) {
            sql.append(" and e.resource_id = :resourceId");
            params.addValue("resourceId", resourceId);
        }
        if (before != null && beforeId != null) {
            sql.append(" and (e.occurred_at, e.id) < (:before, :beforeId)");
            params.addValue("before", Timestamp.from(before)).addValue("beforeId", beforeId);
        }
        sql.append(" order by e.occurred_at desc, e.id desc limit :limit");

        List<AuditEntry> rows = new ArrayList<>(jdbc.query(sql.toString(), params, (rs, i) -> new AuditEntry(
                rs.getObject("id", UUID.class), rs.getTimestamp("occurred_at").toInstant(), rs.getObject("actor_id", UUID.class),
                rs.getString("actor_email"), rs.getString("actor_type"), rs.getString("action"), rs.getString("resource_type"),
                rs.getObject("resource_id", UUID.class), parseMetadata(rs.getString("metadata")), rs.getString("correlation_id"))));
        if (rows.size() <= size) {
            return new AuditPage(rows, null, null);
        }
        rows = rows.subList(0, size);
        AuditEntry last = rows.getLast();
        return new AuditPage(rows, last.occurredAt(), last.id());
    }

    /** The action names that occur in the log, for filtering. */
    @Transactional(readOnly = true)
    public List<String> auditActions(UUID adminId) {
        requireAdmin(adminId);
        return jdbc.queryForList("select distinct action from audit_events order by action", Map.of(), String.class);
    }

    private static Map<String, Object> parseMetadata(String json) {
        try {
            return json == null ? Map.of() : JSON.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (RuntimeException e) {
            return Map.of("unreadable", json);
        }
    }

    // ------------------------------------------------------------------ accounts

    /** Exact address only: an operator acts on a specific report, not by browsing people. */
    @Transactional(readOnly = true)
    public Optional<Account> findByEmail(UUID adminId, String email) {
        requireAdmin(adminId);
        return accounts.findByNormalizedEmail(Account.normalize(email));
    }

    @Transactional
    public Account suspend(UUID adminId, UUID accountId, String reason) {
        Account admin = requireAdmin(adminId);
        Account account = accounts.findForUpdate(accountId).orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        if (account.getId().equals(admin.getId()) || account.getPlatformRole() == PlatformRole.ADMIN) {
            throw new IllegalStateException("Administrator accounts can't be suspended here");
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Only an active account can be suspended");
        }
        account.disable();
        int ended = sessions.revokeAllSessions(account.getId());
        audit(adminId, "ACCOUNT_SUSPENDED", account, Map.of("reason", reason.strip(), "sessionsEnded", ended,
                "platformRole", account.getPlatformRole().name()));
        return account;
    }

    @Transactional
    public Account reinstate(UUID adminId, UUID accountId, String reason) {
        requireAdmin(adminId);
        Account account = accounts.findForUpdate(accountId).orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        if (account.getStatus() != AccountStatus.DISABLED) {
            throw new IllegalStateException("Only a suspended account can be reinstated");
        }
        account.reinstate();
        audit(adminId, "ACCOUNT_REINSTATED", account, Map.of("reason", reason.strip(), "platformRole", account.getPlatformRole().name()));
        return account;
    }

    // ------------------------------------------------------------------

    private void audit(UUID adminId, String action, Account account, Map<String, Object> metadata) {
        auditService.record(new RecordAuditEvent(null, adminId, AuditActorType.ADMIN, action, "ACCOUNT", account.getId(), metadata));
    }

    private Account requireAdmin(UUID accountId) {
        Account account = accounts.findById(accountId).orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        if (account.getPlatformRole() != PlatformRole.ADMIN) {
            throw new AccessDeniedException("Admin access required");
        }
        return account;
    }

}
