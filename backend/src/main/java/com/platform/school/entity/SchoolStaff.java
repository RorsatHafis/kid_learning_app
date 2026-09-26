package com.platform.school.entity;

import com.platform.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.UUID;

/**
 * An account's membership of a school's staff (V39). Separate from class assignment: a
 * teacher belongs to a school first, and only a school's ACTIVE staff can be assigned to
 * its classes. One row per person per school - a later invitation reuses it.
 */
@Entity
@Table(name = "school_staff")
public class SchoolStaff extends AuditableEntity {

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SchoolStaffStatus status;

    @Column(name = "invited_by_account_id")
    private UUID invitedByAccountId;

    @Column(name = "invited_at", nullable = false)
    private Instant invitedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    protected SchoolStaff() {
        // JPA
    }

    public static SchoolStaff invite(UUID schoolId, UUID accountId, UUID invitedBy, Instant now) {
        Assert.notNull(schoolId, "schoolId must not be null");
        Assert.notNull(accountId, "accountId must not be null");
        SchoolStaff staff = new SchoolStaff();
        staff.schoolId = schoolId;
        staff.accountId = accountId;
        staff.status = SchoolStaffStatus.INVITED;
        staff.invitedByAccountId = invitedBy;
        staff.invitedAt = now;
        return staff;
    }

    /** Invites again after a decline or removal; an open invitation is simply re-dated. */
    public void reinvite(UUID invitedBy, Instant now) {
        if (status == SchoolStaffStatus.ACTIVE) {
            throw new IllegalStateException("This person is already on the school's staff");
        }
        status = SchoolStaffStatus.INVITED;
        invitedByAccountId = invitedBy;
        invitedAt = now;
        respondedAt = null;
    }

    public void accept(Instant now) {
        requireInvited();
        status = SchoolStaffStatus.ACTIVE;
        respondedAt = now;
    }

    public void decline(Instant now) {
        requireInvited();
        status = SchoolStaffStatus.DECLINED;
        respondedAt = now;
    }

    /** Ends membership, or withdraws an open invitation. */
    public void remove(Instant now) {
        if (status != SchoolStaffStatus.ACTIVE && status != SchoolStaffStatus.INVITED) {
            throw new IllegalStateException("This person is not on the school's staff");
        }
        status = SchoolStaffStatus.REMOVED;
        respondedAt = now;
    }

    private void requireInvited() {
        if (status != SchoolStaffStatus.INVITED) {
            throw new IllegalStateException("This invitation is no longer open");
        }
    }

    public UUID getSchoolId() { return schoolId; }
    public UUID getAccountId() { return accountId; }
    public SchoolStaffStatus getStatus() { return status; }
    public Instant getInvitedAt() { return invitedAt; }
    public Instant getRespondedAt() { return respondedAt; }

}
