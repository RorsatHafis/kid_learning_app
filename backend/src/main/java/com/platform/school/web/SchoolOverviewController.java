package com.platform.school.web;

import com.platform.school.service.SchoolOverviewService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** The staff home: a teacher's classes, or a principal's schools and their classes (X7). */
@RestController
public class SchoolOverviewController {

    private final SchoolOverviewService overviews;

    public SchoolOverviewController(SchoolOverviewService overviews) {
        this.overviews = overviews;
    }

    @GetMapping("/api/v1/schools/overview")
    public SchoolOverviewService.StaffOverview overview(@AuthenticationPrincipal UUID accountId) {
        return overviews.overview(accountId);
    }
}
