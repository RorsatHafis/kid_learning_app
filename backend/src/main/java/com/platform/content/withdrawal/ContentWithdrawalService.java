package com.platform.content.withdrawal;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.content.service.ContentAccessGuard;
import com.platform.identity.entity.Account;
import com.platform.identity.repository.AccountRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Taking a published version down, and putting it back (Y16, PD-04's mechanism - please confirm).
 *
 * <p>Withdrawing stops the version reaching children at once: it is no longer chosen for any path,
 * practice or review, it can't be read or started, and the steps already waiting for it on
 * children's paths are skipped (open attempts on it are closed) so no child is left stuck on it. A
 * withdrawn question takes down every activity version that contains it. Nothing already recorded
 * changes - answers, evidence and history stay; whether to discount evidence gathered from a wrong
 * version is the owner's decision. Restoring lets it be chosen again; skipped steps stay skipped.
 * Content managers only (the author, an administrator, or a principal over their staff's content).
 */
@Service
public class ContentWithdrawalService {

    public record WithdrawalView(UUID id, ContentWithdrawal.Kind kind, UUID versionId, String reason, String withdrawnBy,
                                 Instant withdrawnAt) {
    }

    /** {@code withdrawal}: in force on this version itself; {@code viaQuestion}: an activity taken down by a question in it. */
    public record WithdrawalState(WithdrawalView withdrawal, boolean viaQuestion) {
    }

    private final ContentWithdrawalRepository withdrawals;
    private final WithdrawnContent withdrawn;
    private final ContentAccessGuard guard;
    private final AccountRepository accounts;
    private final AuditService audit;
    private final NamedParameterJdbcTemplate jdbc;

    public ContentWithdrawalService(ContentWithdrawalRepository withdrawals, WithdrawnContent withdrawn, ContentAccessGuard guard,
                                    AccountRepository accounts, AuditService audit, NamedParameterJdbcTemplate jdbc) {
        this.withdrawals = withdrawals;
        this.withdrawn = withdrawn;
        this.guard = guard;
        this.accounts = accounts;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public WithdrawalState state(UUID accountId, ContentWithdrawal.Kind kind, UUID versionId) {
        requireManage(accountId, kind, versionId);
        Optional<ContentWithdrawal> own = withdrawals.findActiveForRead(versionId);
        boolean viaQuestion = own.isEmpty() && kind == ContentWithdrawal.Kind.ACTIVITY && withdrawn.activityVersion(versionId);
        return new WithdrawalState(own.map(this::view).orElse(null), viaQuestion);
    }

    @Transactional
    public WithdrawalState withdraw(UUID accountId, ContentWithdrawal.Kind kind, UUID versionId, String reason) {
        requireManage(accountId, kind, versionId);
        String status = status(kind, versionId);
        if (!"PUBLISHED".equals(status) && !"RETIRED".equals(status)) {
            throw new IllegalStateException("Only a version children have been given can be taken down; a draft never reached them");
        }
        if (withdrawals.findActive(versionId).isPresent()) {
            throw new IllegalStateException("This version has already been taken down");
        }
        Instant now = Instant.now();
        ContentWithdrawal withdrawal = withdrawals.saveAndFlush(ContentWithdrawal.of(kind, versionId, reason, accountId, now));
        List<UUID> affected = kind == ContentWithdrawal.Kind.ACTIVITY ? List.of(versionId)
                : jdbc.queryForList("select distinct activity_version_id from activity_items where question_version_id = :q",
                        Map.of("q", versionId), UUID.class);
        int attemptsClosed = 0;
        int stepsSkipped = 0;
        if (!affected.isEmpty()) {
            MapSqlParameterSource params = new MapSqlParameterSource("versions", affected);
            attemptsClosed = jdbc.update("""
                    update activity_attempts set status = 'ABANDONED', completed_at = now(), version = version + 1
                    where status = 'IN_PROGRESS' and activity_version_id in (:versions)
                    """, params);
            stepsSkipped = jdbc.update("""
                    update learning_path_items set status = 'SKIPPED', version = version + 1
                    where status in ('PENDING', 'IN_PROGRESS') and activity_version_id in (:versions)
                    """, params);
        }
        record(accountId, "CONTENT_WITHDRAWN", withdrawal.getId(), Map.of("kind", kind.name(), "versionId", versionId.toString(),
                "reason", withdrawal.getReason(), "activityVersions", affected.size(), "stepsSkipped", stepsSkipped,
                "attemptsClosed", attemptsClosed));
        return state(accountId, kind, versionId);
    }

    @Transactional
    public WithdrawalState restore(UUID accountId, ContentWithdrawal.Kind kind, UUID versionId) {
        requireManage(accountId, kind, versionId);
        ContentWithdrawal withdrawal = withdrawals.findActive(versionId)
                .orElseThrow(() -> new IllegalStateException("This version isn't taken down"));
        withdrawal.restore(accountId, Instant.now());
        record(accountId, "CONTENT_RESTORED_AFTER_WITHDRAWAL", withdrawal.getId(),
                Map.of("kind", kind.name(), "versionId", versionId.toString()));
        withdrawals.flush();
        return state(accountId, kind, versionId);
    }

    // ------------------------------------------------------------------

    private void requireManage(UUID accountId, ContentWithdrawal.Kind kind, UUID versionId) {
        UUID contentId = jdbc.query(kind == ContentWithdrawal.Kind.ACTIVITY
                        ? "select activity_id from activity_versions where id = :v"
                        : "select question_id from question_versions where id = :v",
                Map.of("v", versionId), (rs, i) -> rs.getObject(1, UUID.class)).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(kind.name(), versionId));
        if (kind == ContentWithdrawal.Kind.ACTIVITY) {
            guard.requireActivityWrite(accountId, contentId);
        } else {
            guard.requireQuestionWrite(accountId, contentId);
        }
    }

    private String status(ContentWithdrawal.Kind kind, UUID versionId) {
        return jdbc.queryForObject(kind == ContentWithdrawal.Kind.ACTIVITY
                ? "select status from activity_versions where id = :v" : "select status from question_versions where id = :v",
                Map.of("v", versionId), String.class);
    }

    private WithdrawalView view(ContentWithdrawal w) {
        return new WithdrawalView(w.getId(), w.getKind(), w.getVersionId(), w.getReason(),
                accounts.findById(w.getWithdrawnBy()).map(Account::getEmail).orElse("a former account"), w.getWithdrawnAt());
    }

    private void record(UUID actorId, String action, UUID withdrawalId, Map<String, Object> metadata) {
        Account actor = accounts.findById(actorId).orElseThrow(() -> new ResourceNotFoundException("Account", actorId));
        audit.record(new RecordAuditEvent(null, actorId, AuditActorType.valueOf(actor.getPlatformRole().name()), action,
                "CONTENT_WITHDRAWAL", withdrawalId, metadata));
    }

}
