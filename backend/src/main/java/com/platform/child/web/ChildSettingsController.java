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
 * Break reminders (Y36) and the daily learning time (M3): set by the parent on the child's page, read by the child's
 * space. Only the child's family may read or change them (another family's child is 404, as everywhere).
 */
@RestController
public class ChildSettingsController {

    private final ChildSettingsService settings;
    private final com.platform.child.service.DailyLearningTimeService dailyTime;
    private final FamilyAccessGuard familyAccessGuard;
    private final com.platform.family.service.ChildAccess childAccess;

    public ChildSettingsController(ChildSettingsService settings, com.platform.child.service.DailyLearningTimeService dailyTime,
                                   FamilyAccessGuard familyAccessGuard, com.platform.family.service.ChildAccess childAccess) {
        this.settings = settings;
        this.dailyTime = dailyTime;
        this.familyAccessGuard = familyAccessGuard;
        this.childAccess = childAccess;
    }

    /** M3: {@code minutes} null = no daily limit; otherwise 15, 20, 30, 45, 60, 90 or 120. */
    public record DailyLimit(Integer minutes) {
    }

    public record Extension(int minutes) {
    }

    @GetMapping("/api/v1/children/{childId}/daily-limit")
    public DailyLimit dailyLimit(@PathVariable UUID childId, @AuthenticationPrincipal UUID accountId) {
        familyAccessGuard.requireChildAccess(accountId, childId);
        return new DailyLimit(dailyTime.limitMinutes(childId));
    }

    @PutMapping("/api/v1/children/{childId}/daily-limit")
    public DailyLimit setDailyLimit(@PathVariable UUID childId, @RequestBody DailyLimit request, @AuthenticationPrincipal UUID accountId) {
        familyAccessGuard.requireChildAccess(accountId, childId);
        return new DailyLimit(dailyTime.setLimitMinutes(childId, request.minutes()));
    }

    /** Today's learning time: the limit, what is used, extra given, what is left. */
    @GetMapping("/api/v1/children/{childId}/today")
    public com.platform.child.service.DailyLearningTimeService.Today today(@PathVariable UUID childId,
                                                                          @AuthenticationPrincipal UUID accountId) {
        childAccess.requireLearner(accountId, childId);
        return dailyTime.today(childId);
    }

    @org.springframework.web.bind.annotation.PostMapping("/api/v1/children/{childId}/today/extensions")
    public com.platform.child.service.DailyLearningTimeService.Today extend(@PathVariable UUID childId, @RequestBody Extension request,
                                                                           @AuthenticationPrincipal UUID accountId) {
        familyAccessGuard.requireChildAccess(accountId, childId);
        return dailyTime.extend(accountId, childId, request.minutes());
    }

    /** {@code minutes} null = no reminder; otherwise 10, 15, 20, 30 or 45. */
    public record BreakReminder(Integer minutes) {
    }

    @GetMapping("/api/v1/children/{childId}/break-reminder")
    public BreakReminder get(@PathVariable UUID childId, @AuthenticationPrincipal UUID accountId) {
        childAccess.requireLearner(accountId, childId);
        return new BreakReminder(settings.breakReminderMinutes(childId));
    }

    @PutMapping("/api/v1/children/{childId}/break-reminder")
    public BreakReminder set(@PathVariable UUID childId, @RequestBody BreakReminder request,
                             @AuthenticationPrincipal UUID accountId) {
        familyAccessGuard.requireChildAccess(accountId, childId);
        return new BreakReminder(settings.setBreakReminderMinutes(childId, request.minutes()));
    }
}
