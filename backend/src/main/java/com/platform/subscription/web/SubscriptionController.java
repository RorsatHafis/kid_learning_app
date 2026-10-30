package com.platform.subscription.web;

import org.springframework.web.bind.annotation.DeleteMapping;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import com.platform.subscription.service.PlanRequestService;
import com.platform.subscription.service.SubscriptionReportService;
import com.platform.common.web.PageResponse;
import com.platform.subscription.service.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * A family's plan (its own view) and the administrator's plan and grant operations. Every
 * admin operation re-checks the ADMIN role in {@link SubscriptionService}.
 */
@RestController
public class SubscriptionController {

    private final SubscriptionService service;

    private final PlanRequestService planRequests;

    private final com.platform.school.service.SchoolAccessGuard schoolGuard;

    private final SubscriptionReportService reports;

    public SubscriptionController(SubscriptionService service, PlanRequestService planRequests,
                                  com.platform.school.service.SchoolAccessGuard schoolGuard, SubscriptionReportService reports) {
        this.schoolGuard = schoolGuard;
        this.reports = reports;
        this.service = service;
        this.planRequests = planRequests;
    }

    public record PlanRequestBody(@NotNull UUID planId) {
    }

    public record DeclineBody(@Size(max = 500) String message) {
    }

    /** Y11: asks for an offered plan (no payment: an administrator answers). Returns the family's plan view. */
    @PostMapping("/api/v1/family/plan/request")
    public SubscriptionDtos.OwnFamilyPlanResponse requestPlan(@AuthenticationPrincipal UUID accountId,
                                                             @Valid @RequestBody PlanRequestBody body) {
        planRequests.request(accountId, body.planId());
        return SubscriptionDtos.OwnFamilyPlanResponse.of(service.familyPlan(accountId));
    }

    @DeleteMapping("/api/v1/family/plan/request")
    public SubscriptionDtos.OwnFamilyPlanResponse withdrawPlanRequest(@AuthenticationPrincipal UUID accountId) {
        planRequests.withdraw(accountId);
        return SubscriptionDtos.OwnFamilyPlanResponse.of(service.familyPlan(accountId));
    }

    @GetMapping("/api/v1/admin/plan-requests")
    public PageResponse<PlanRequestService.AdminRow> planRequests(
            @AuthenticationPrincipal UUID adminId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return planRequests.openRequests(adminId, new PageResponse.Request(page, size));
    }

    @PostMapping("/api/v1/admin/plan-requests/{requestId}/decline")
    public ResponseEntity<Void> declinePlanRequest(@AuthenticationPrincipal UUID adminId,
                                                                           @PathVariable UUID requestId,
                                                                           @Valid @RequestBody DeclineBody body) {
        planRequests.decline(adminId, requestId, body.message());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/family/plan")
    public SubscriptionDtos.OwnFamilyPlanResponse familyPlan(@AuthenticationPrincipal UUID accountId) {
        return SubscriptionDtos.OwnFamilyPlanResponse.of(service.familyPlan(accountId));
    }

    @GetMapping("/api/v1/admin/plans")
    public List<SubscriptionDtos.PlanResponse> plans(@AuthenticationPrincipal UUID adminId) {
        return service.listPlans(adminId).stream().map(SubscriptionDtos.PlanResponse::of).toList();
    }

    @PostMapping("/api/v1/admin/plans")
    public ResponseEntity<SubscriptionDtos.PlanResponse> createPlan(@AuthenticationPrincipal UUID adminId,
                                                                    @Valid @RequestBody SubscriptionDtos.CreatePlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SubscriptionDtos.PlanResponse.of(service.createPlan(adminId, request.code(), request.name(), request.description(),
                        request.audience() == null ? com.platform.subscription.entity.PlanAudience.FAMILY : request.audience(),
                        request.khmer() == null ? null : request.khmer().words())));
    }

    @PostMapping("/api/v1/admin/plans/{planId}/duplicate")
    public ResponseEntity<SubscriptionDtos.PlanResponse> duplicatePlan(@AuthenticationPrincipal UUID adminId, @PathVariable UUID planId,
                                                                       @Valid @RequestBody SubscriptionDtos.DuplicatePlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SubscriptionDtos.PlanResponse.of(service.duplicatePlan(adminId, planId, request.code(), request.name())));
    }

    /** V80: when new customers can take the plan up; subscribers are unaffected. Step-up, like retiring it. */
    @com.platform.security.stepup.RequiresStepUp
    @PutMapping("/api/v1/admin/plans/{planId}/availability")
    public SubscriptionDtos.PlanResponse changeAvailability(@AuthenticationPrincipal UUID adminId, @PathVariable UUID planId,
                                                            @RequestBody SubscriptionDtos.AvailabilityRequest request) {
        return SubscriptionDtos.PlanResponse.of(service.changeAvailability(adminId, planId, request.availableFrom(),
                request.availableUntil()));
    }

    /** Who is on which plan now: counts per plan and standing; schools whose paid period ends soon. */
    @GetMapping("/api/v1/admin/subscriptions/summary")
    public SubscriptionReportService.Summary subscriptionSummary(@AuthenticationPrincipal UUID adminId) {
        return reports.summary(adminId);
    }

    @PutMapping("/api/v1/admin/plans/{planId}")
    public SubscriptionDtos.PlanResponse revisePlan(@AuthenticationPrincipal UUID adminId, @PathVariable UUID planId,
                                                    @Valid @RequestBody SubscriptionDtos.RevisePlanRequest request) {
        return SubscriptionDtos.PlanResponse.of(service.revisePlan(adminId, planId, request.name(), request.description(),
                request.khmer() == null ? null : request.khmer().words(), request.selfService(), request.priceAmountMinor(), request.priceCurrency(), request.billingInterval(), request.limits()));
    }

    @com.platform.security.stepup.RequiresStepUp
    @PostMapping("/api/v1/admin/plans/{planId}/activate")
    public SubscriptionDtos.PlanResponse activate(@AuthenticationPrincipal UUID adminId, @PathVariable UUID planId) {
        return SubscriptionDtos.PlanResponse.of(service.activatePlan(adminId, planId));
    }

    @com.platform.security.stepup.RequiresStepUp
    @PostMapping("/api/v1/admin/plans/{planId}/retire")
    public SubscriptionDtos.PlanResponse retire(@AuthenticationPrincipal UUID adminId, @PathVariable UUID planId) {
        return SubscriptionDtos.PlanResponse.of(service.retirePlan(adminId, planId));
    }

    @com.platform.security.stepup.RequiresStepUp
    @PostMapping("/api/v1/admin/plans/{planId}/make-default")
    public SubscriptionDtos.PlanResponse makeDefault(@AuthenticationPrincipal UUID adminId, @PathVariable UUID planId) {
        return SubscriptionDtos.PlanResponse.of(service.makeDefault(adminId, planId));
    }

    /** Exact parent address only; 404 when no family is found (no directory browsing). */
    @GetMapping("/api/v1/admin/families")
    public SubscriptionDtos.FamilyResponse findFamily(@AuthenticationPrincipal UUID adminId, @RequestParam String parentEmail) {
        return service.findFamilyByParentEmail(adminId, parentEmail)
                .map(SubscriptionDtos.FamilyResponse::of)
                .orElseThrow(() -> new com.platform.common.web.ResourceNotFoundException("Family for", parentEmail));
    }

    @com.platform.security.stepup.RequiresStepUp
    @PostMapping("/api/v1/admin/families/{familyId}/subscription")
    public SubscriptionDtos.FamilyResponse grant(@AuthenticationPrincipal UUID adminId, @PathVariable UUID familyId,
                                                 @Valid @RequestBody SubscriptionDtos.GrantRequest request) {
        return SubscriptionDtos.FamilyResponse.of(service.grant(adminId, familyId, request.planId(), request.periodEnd(), request.note()));
    }

    @com.platform.security.stepup.RequiresStepUp
    @PutMapping("/api/v1/admin/families/{familyId}/subscription/period")
    public SubscriptionDtos.FamilyResponse changePeriod(@AuthenticationPrincipal UUID adminId, @PathVariable UUID familyId,
                                                        @Valid @RequestBody SubscriptionDtos.PeriodRequest request) {
        return SubscriptionDtos.FamilyResponse.of(service.changePeriodEnd(adminId, familyId, request.periodEnd(), request.reason()));
    }

    @com.platform.security.stepup.RequiresStepUp
    @PostMapping("/api/v1/admin/families/{familyId}/subscription/end")
    public SubscriptionDtos.FamilyResponse end(@AuthenticationPrincipal UUID adminId, @PathVariable UUID familyId,
                                               @Valid @RequestBody SubscriptionDtos.EndRequest request) {
        return SubscriptionDtos.FamilyResponse.of(service.end(adminId, familyId, request.reason()));
    }

    // ------------------------------------------------------------------ schools (V59)

    /** The school's plan and what it holds, for its principal (or an administrator). */
    @GetMapping("/api/v1/schools/{schoolId}/plan")
    public SubscriptionDtos.OwnSchoolPlanResponse schoolPlan(@AuthenticationPrincipal UUID accountId, @PathVariable UUID schoolId) {
        schoolGuard.requireSchoolBilling(accountId, schoolId);
        return SubscriptionDtos.OwnSchoolPlanResponse.of(service.schoolView(schoolId));
    }

    @GetMapping("/api/v1/admin/schools/{schoolId}/subscription")
    public SubscriptionDtos.SchoolPlanResponse adminSchoolPlan(@AuthenticationPrincipal UUID adminId, @PathVariable UUID schoolId) {
        return SubscriptionDtos.SchoolPlanResponse.of(service.adminSchoolView(adminId, schoolId));
    }

    @com.platform.security.stepup.RequiresStepUp
    @PostMapping("/api/v1/admin/schools/{schoolId}/subscription")
    public SubscriptionDtos.SchoolPlanResponse grantSchool(@AuthenticationPrincipal UUID adminId, @PathVariable UUID schoolId,
                                                           @Valid @RequestBody SubscriptionDtos.GrantRequest request) {
        return SubscriptionDtos.SchoolPlanResponse.of(service.grantSchool(adminId, schoolId, request.planId(), request.periodEnd(), request.note()));
    }

    @com.platform.security.stepup.RequiresStepUp
    @PutMapping("/api/v1/admin/schools/{schoolId}/subscription/period")
    public SubscriptionDtos.SchoolPlanResponse changeSchoolPeriod(@AuthenticationPrincipal UUID adminId, @PathVariable UUID schoolId,
                                                                  @Valid @RequestBody SubscriptionDtos.PeriodRequest request) {
        return SubscriptionDtos.SchoolPlanResponse.of(service.changeSchoolPeriodEnd(adminId, schoolId, request.periodEnd(), request.reason()));
    }

    @com.platform.security.stepup.RequiresStepUp
    @PostMapping("/api/v1/admin/schools/{schoolId}/subscription/end")
    public SubscriptionDtos.SchoolPlanResponse endSchool(@AuthenticationPrincipal UUID adminId, @PathVariable UUID schoolId,
                                                         @Valid @RequestBody SubscriptionDtos.EndRequest request) {
        return SubscriptionDtos.SchoolPlanResponse.of(service.endSchool(adminId, schoolId, request.reason()));
    }
}
