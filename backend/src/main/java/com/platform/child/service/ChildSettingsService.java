package com.platform.child.service;

import com.platform.child.entity.ChildSettings;
import com.platform.child.repository.ChildRepository;
import com.platform.child.repository.ChildSettingsRepository;
import com.platform.common.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * A family's settings for one child's space (Y36). Callers have already checked the account's access
 * to the child (FamilyAccessGuard). The settings row is created the first time one is set, under the
 * child's row lock, so two saves at once can't both insert it.
 */
@Service
public class ChildSettingsService {

    private final ChildRepository children;
    private final ChildSettingsRepository settings;

    public ChildSettingsService(ChildRepository children, ChildSettingsRepository settings) {
        this.children = children;
        this.settings = settings;
    }

    /** Minutes of continuous learning after which the child's space suggests a break; null = off. */
    @Transactional(readOnly = true)
    public Integer breakReminderMinutes(UUID childId) {
        return settings.findByChildId(childId).map(ChildSettings::getBreakReminderMinutes).orElse(null);
    }

    @Transactional
    public Integer setBreakReminderMinutes(UUID childId, Integer minutes) {
        children.findByIdForUpdate(childId).orElseThrow(() -> new ResourceNotFoundException("Child", childId));
        ChildSettings row = settings.findByChildId(childId).orElseGet(() -> ChildSettings.of(childId));
        row.setBreakReminderMinutes(minutes);
        settings.saveAndFlush(row);
        return row.getBreakReminderMinutes();
    }
}
