package com.platform.content.withdrawal;

import com.platform.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A published version taken down (V55, Y16). In force while {@code restoredAt} is null. */
@Entity
@Table(name = "content_withdrawals")
public class ContentWithdrawal extends AuditableEntity {

    public enum Kind { QUESTION, ACTIVITY }

    @Enumerated(EnumType.STRING)
    @Column(name = "content_kind", nullable = false, updatable = false, length = 20)
    private Kind kind;

    @Column(name = "version_id", nullable = false, updatable = false)
    private UUID versionId;

    @Column(name = "reason", nullable = false, updatable = false, length = 500)
    private String reason;

    @Column(name = "withdrawn_by_account_id", nullable = false, updatable = false)
    private UUID withdrawnBy;

    @Column(name = "withdrawn_at", nullable = false, updatable = false)
    private Instant withdrawnAt;

    @Column(name = "restored_by_account_id")
    private UUID restoredBy;

    @Column(name = "restored_at")
    private Instant restoredAt;

    protected ContentWithdrawal() {
        // JPA
    }

    static ContentWithdrawal of(Kind kind, UUID versionId, String reason, UUID by, Instant at) {
        ContentWithdrawal withdrawal = new ContentWithdrawal();
        withdrawal.kind = kind;
        withdrawal.versionId = versionId;
        withdrawal.reason = reason.strip();
        withdrawal.withdrawnBy = by;
        withdrawal.withdrawnAt = at;
        return withdrawal;
    }

    void restore(UUID by, Instant at) {
        this.restoredBy = by;
        this.restoredAt = at;
    }

    public Kind getKind() {
        return kind;
    }

    public UUID getVersionId() {
        return versionId;
    }

    public String getReason() {
        return reason;
    }

    public UUID getWithdrawnBy() {
        return withdrawnBy;
    }

    public Instant getWithdrawnAt() {
        return withdrawnAt;
    }

}
