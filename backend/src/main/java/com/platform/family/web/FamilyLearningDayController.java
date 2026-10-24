package com.platform.family.web;

import com.platform.child.service.DailyLearningTimeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The time zone the family's learning day follows (when today's learning time starts again): read by the family's
 * members, set by a parent; null puts it back to the platform's.
 */
@RestController
public class FamilyLearningDayController {

    public record ZoneRequest(@Size(max = 64) String zone) {
    }

    private final DailyLearningTimeService dailyTime;

    public FamilyLearningDayController(DailyLearningTimeService dailyTime) {
        this.dailyTime = dailyTime;
    }

    @GetMapping("/api/v1/family/learning-day")
    public DailyLearningTimeService.FamilyDay get(@AuthenticationPrincipal UUID accountId) {
        return dailyTime.familyDay(accountId);
    }

    @PutMapping("/api/v1/family/learning-day")
    public DailyLearningTimeService.FamilyDay set(@AuthenticationPrincipal UUID accountId, @Valid @RequestBody ZoneRequest request) {
        return dailyTime.setFamilyZone(accountId, request.zone());
    }

}
