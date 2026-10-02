package com.platform.admin.web;

import com.platform.admin.service.AdminInsightService;
import com.platform.common.web.PageResponse;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** The administrator's operational reads (X15): schools, learning operations, reports, system. ADMIN only. */
@RestController
@Validated
@RequestMapping("/api/v1/admin")
public class AdminInsightController {

    private final AdminInsightService insights;

    public AdminInsightController(AdminInsightService insights) {
        this.insights = insights;
    }

    @GetMapping("/schools")
    public PageResponse<AdminInsightService.SchoolRow> schools(@AuthenticationPrincipal UUID accountId,
                                                               @RequestParam(required = false) @Size(max = 200) String q,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "25") int size) {
        return insights.schools(accountId, q, new PageResponse.Request(page, size));
    }

    @GetMapping("/schools/{schoolId}")
    public AdminInsightService.SchoolDetail school(@AuthenticationPrincipal UUID accountId, @PathVariable UUID schoolId) {
        return insights.school(accountId, schoolId);
    }

    @GetMapping("/learning-operations")
    public AdminInsightService.LearningOperations learningOperations(@AuthenticationPrincipal UUID accountId) {
        return insights.learningOperations(accountId);
    }

    @GetMapping("/reports/weekly")
    public List<AdminInsightService.Week> weekly(@AuthenticationPrincipal UUID accountId) {
        return insights.weekly(accountId);
    }

    @GetMapping("/system")
    public AdminInsightService.SystemStatus system(@AuthenticationPrincipal UUID accountId) {
        return insights.system(accountId);
    }

}
