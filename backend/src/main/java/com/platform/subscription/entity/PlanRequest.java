package com.platform.subscription.entity;

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
 * A family asking for one of the plans it is offered (V52). Answered by an administrator:
 * granted through the ordinary grant, or declined; the family may withdraw it while open.
 */
@Entity
@Table(name = "plan_requests")
public class PlanRequest extends AuditableEntity {

    public enum Status { OPEN, GRANTED, DECLINED, WITHDRAWN }

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @Column(name = "requested_by_account_id", nullable = false, updatable = false)
    private UUID requestedByAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by_account_id")
    private UUID resolvedByAccountId;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    protected PlanRequest() {
        // JPA
    }

    public static PlanRequest open(UUID familyId, UUID planId, UUID requestedBy, Instant at) {
        Assert.notNull(familyId, "familyId must not be null");
        Assert.notNull(planId, "planId must not be null");
        Assert.notNull(requestedBy, "requestedBy must not be null");
        PlanRequest request = new PlanRequest();
        request.familyId = familyId;
        request.planId = planId;
        request.requestedByAccountId = requestedBy;
        request.status = Status.OPEN;
        request.requestedAt = at;
        return request;
    }

    /** Answers an open request; {@code by} is null when the family withdrew it. */
    public void resolve(Status outcome, UUID by, String note, Instant at) {
        if (status != Status.OPEN) {
            throw new IllegalStateException("This request has already been answered");
        }
        Assert.isTrue(outcome != Status.OPEN, "a request is resolved to a final status");
        this.status = outcome;
        this.resolvedByAccountId = by;
        this.resolutionNote = note == null || note.isBlank() ? null : note.strip();
        this.resolvedAt = at;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public UUID getPlanId() {
        return planId;
    }

    public UUID getRequestedByAccountId() {
        return requestedByAccountId;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

}
