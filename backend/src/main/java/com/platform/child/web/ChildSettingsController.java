package com.platform.child.web;

import com.platform.child.service.ChildSettingsService;
import com.platform.family.service.FamilyAccessGuard;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Break reminders (Y36): set by the parent on the child's page, read by the child's space. Only the
 * child's family may read or change them (another family's child is 404, as everywhere).
 */
@RestController
public class ChildSettingsController {

    private final ChildSettingsService settings;
    private final FamilyAccessGuard familyAccessGuard;

    public ChildSettingsController(ChildSettingsService settings, FamilyAccessGuard familyAccessGuard) {
        this.settings = settings;
        this.familyAccessGuard = familyAccessGuard;
    }

    /** {@code minutes} null = no reminder; otherwise 10, 15, 20, 30 or 45. */
    public record BreakReminder(Integer minutes) {
    }

    @GetMapping("/api/v1/children/{childId}/break-reminder")
    public BreakReminder get(@PathVariable UUID childId, @AuthenticationPrincipal UUID accountId) {
        familyAccessGuard.requireChildAccess(accountId, childId);
        return new BreakReminder(settings.breakReminderMinutes(childId));
    }

    @PutMapping("/api/v1/children/{childId}/break-reminder")
    public BreakReminder set(@PathVariable UUID childId, @RequestBody BreakReminder request,
                             @AuthenticationPrincipal UUID accountId) {
        familyAccessGuard.requireChildAccess(accountId, childId);
        return new BreakReminder(settings.setBreakReminderMinutes(childId, request.minutes()));
    }
}
