package com.platform.child.entity;

import com.platform.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.util.Assert;

import java.util.Set;
import java.util.UUID;

/**
 * A family's settings for one child's space (V9, one row per child, created when first set).
 * Only the break reminder is used (Y36, V60); the V9 columns for a daily time limit, content
 * restrictions and notifications have never been read or written by the product.
 */
@Entity
@Table(name = "child_settings")
public class ChildSettings extends AuditableEntity {

    /** The only lengths a parent can choose, in minutes (V60 CHECK). */
    public static final Set<Integer> BREAK_REMINDER_MINUTES = Set.of(10, 15, 20, 30, 45);

    @Column(name = "child_id", nullable = false, updatable = false)
    private UUID childId;

    @Column(name = "break_reminder_minutes")
    private Integer breakReminderMinutes;

    protected ChildSettings() {
        // JPA
    }

    public static ChildSettings of(UUID childId) {
        Assert.notNull(childId, "childId must not be null");
        ChildSettings settings = new ChildSettings();
        settings.childId = childId;
        return settings;
    }

    /** {@code minutes} null turns the reminder off. */
    public void setBreakReminderMinutes(Integer minutes) {
        if (minutes != null && !BREAK_REMINDER_MINUTES.contains(minutes)) {
            throw new IllegalArgumentException("A break can be suggested after 10, 15, 20, 30 or 45 minutes");
        }
        this.breakReminderMinutes = minutes;
    }

    public UUID getChildId() {
        return childId;
    }

    public Integer getBreakReminderMinutes() {
        return breakReminderMinutes;
    }
}
