package com.platform.school.web;

import com.platform.school.service.SchoolLearningSummaryService;
import com.platform.school.service.SchoolOverviewService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** The staff home: a teacher's classes, or a principal's schools and their classes (X7). */
@RestController
public class SchoolOverviewController {

    private final SchoolOverviewService overviews;
    private final SchoolLearningSummaryService learning;

    public SchoolOverviewController(SchoolOverviewService overviews, SchoolLearningSummaryService learning) {
        this.overviews = overviews;
        this.learning = learning;
    }

    @GetMapping("/api/v1/schools/overview")
    public SchoolOverviewService.StaffOverview overview(@AuthenticationPrincipal UUID accountId) {
        return overviews.overview(accountId);
    }

    /** Learning across the school, class by class (X21): its principal only. */
    @GetMapping("/api/v1/schools/{schoolId}/learning")
    public SchoolLearningSummaryService.SchoolLearning learning(@PathVariable UUID schoolId, @AuthenticationPrincipal UUID accountId) {
        return learning.learning(accountId, schoolId);
    }
}
