package com.platform.content.media;

import com.platform.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.util.UUID;

/**
 * An administrator's exception to one author's storage allowance (V59): it replaces what their
 * schools' plans give, until it is cleared. {@code limitMb} null = unlimited. Every change is audited.
 */
@Entity
@Table(name = "account_storage_allowances")
public class StorageAllowance extends AuditableEntity {

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "limit_mb")
    private Integer limitMb;

    @Column(name = "note", nullable = false, length = 500)
    private String note;

    @Column(name = "set_by_account_id", nullable = false)
    private UUID setByAccountId;

    protected StorageAllowance() {
        // JPA
    }

    public static StorageAllowance of(UUID accountId) {
        Assert.notNull(accountId, "accountId must not be null");
        StorageAllowance allowance = new StorageAllowance();
        allowance.accountId = accountId;
        return allowance;
    }

    public void set(Integer limitMb, String note, UUID setBy) {
        if (limitMb != null && limitMb < 0) {
            throw new IllegalArgumentException("must be greater than or equal to 0");
        }
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("A note is required when changing a storage allowance");
        }
        this.limitMb = limitMb;
        this.note = note.strip();
        this.setByAccountId = setBy;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public Integer getLimitMb() {
        return limitMb;
    }

    public String getNote() {
        return note;
    }

    public UUID getSetByAccountId() {
        return setByAccountId;
    }
}
