package com.platform.subscription.service;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.common.web.PageResponse;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.family.entity.Membership;
import com.platform.family.entity.MembershipStatus;
import com.platform.family.repository.FamilyRepository;
import com.platform.family.repository.MembershipRepository;
import com.platform.identity.UiLanguage;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import com.platform.notification.EmailMessage;
import com.platform.notification.PlatformMailProperties;
import com.platform.subscription.entity.PlanRequest;
import com.platform.subscription.entity.PlanStatus;
import com.platform.subscription.entity.SubscriptionPlan;
import com.platform.subscription.repository.PlanRequestRepository;
import com.platform.subscription.repository.SubscriptionPlanRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A family asking for another plan, and an administrator answering (Y11). Until a payment
 * provider is chosen (PD-09) nothing is bought online: the request reaches the administrators'
 * "Waiting on you", and an administrator grants the plan with the ordinary grant - which answers
 * the request - or declines it with a message. No money changes hands here.
 */
@Service
public class PlanRequestService {

    private final PlanRequestRepository requests;
    private final SubscriptionPlanRepository plans;
    private final FamilyRepository families;
    private final MembershipRepository memberships;
    private final AccountRepository accounts;
    private final EntitlementService entitlements;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final PlatformMailProperties mail;
    private final NamedParameterJdbcTemplate jdbc;

    public PlanRequestService(PlanRequestRepository requests, SubscriptionPlanRepository plans, FamilyRepository families,
                              MembershipRepository memberships, AccountRepository accounts, EntitlementService entitlements,
                              AuditService audit, ApplicationEventPublisher events, PlatformMailProperties mail,
                              NamedParameterJdbcTemplate jdbc) {
        this.requests = requests;
        this.plans = plans;
        this.families = families;
        this.memberships = memberships;
        this.accounts = accounts;
        this.entitlements = entitlements;
        this.audit = audit;
        this.events = events;
        this.mail = mail;
        this.jdbc = jdbc;
    }

    /** A family's open request, as the family sees it. */
    public record OpenRequest(UUID planId, String planName, Instant requestedAt) {
    }

    /** An open request, as an administrator sees it. */
    public record AdminRow(UUID id, UUID familyId, String familyName, String parentEmail, String planName, Instant requestedAt) {
    }

    // ------------------------------------------------------------------ the family

    /**
     * Asks for {@code planId}: a plan the family is offered (active and self-service) and not on
     * already. Asking again for the same plan changes nothing; asking for another replaces the open
     * request.
     */
    @Transactional
    public void request(UUID accountId, UUID planId) {
        Account parent = accounts.findById(accountId).orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        UUID familyId = familyOf(accountId);
        families.findByIdForUpdate(familyId).orElseThrow(() -> new ResourceNotFoundException("Family", familyId));
        SubscriptionPlan plan = plans.findById(planId)
                .filter(p -> p.getAudience() == com.platform.subscription.entity.PlanAudience.FAMILY)
                .filter(p -> p.getStatus() == PlanStatus.ACTIVE && p.isSelfService())
                .orElseThrow(() -> new ResourceNotFoundException("Plan", planId));
        if (entitlements.effectivePlan(familyId).plan().getId().equals(planId)) {
            throw new IllegalStateException("Your family is already on this plan");
        }
        Instant now = Instant.now();
        Optional<PlanRequest> open = requests.findOpenForUpdate(familyId);
        if (open.isPresent() && open.get().getPlanId().equals(planId)) {
            return;
        }
        open.ifPresent(previous -> {
            previous.resolve(PlanRequest.Status.WITHDRAWN, null, "Replaced by a request for another plan", now);
            requests.flush();
        });
        PlanRequest request = requests.save(PlanRequest.open(familyId, planId, accountId, now));
        record(accountId, AuditActorType.PARENT, "PLAN_REQUESTED", request.getId(), Map.of("plan", plan.getCode()));
        // The plan's name is the administrator's own wording, so it is quoted as it is in both languages.
        events.publishEvent(UiLanguage.isKhmer(parent.getPreferredLanguage())
                ? new EmailMessage(parent.getEmail(), "សំណើរបស់អ្នកសម្រាប់កញ្ចប់ " + plan.getName(), """
                សួស្ដី,

                យើងបានទទួលសំណើរបស់គ្រួសារអ្នក សម្រាប់កញ្ចប់ %s ហើយ។ កញ្ចប់មិនទាន់អាចទិញតាមអ៊ីនធឺណិតបាននៅឡើយទេ៖ នរណាម្នាក់នៅ Bookeyond Kid នឹងឆ្លើយតបមកអាសយដ្ឋាននេះ។

                គ្មានអ្វីផ្លាស់ប្ដូរទេ រហូតដល់ពេលនោះ ហើយអ្នកអាចដកសំណើនេះវិញ ពីទំព័រគណនីរបស់អ្នកនៅ៖

                %s/parent/account
                """.formatted(plan.getName(), mail.publicUrl()))
                : new EmailMessage(parent.getEmail(), "Your request for the " + plan.getName() + " plan", """
                Hello,

                We've received your family's request for the %s plan. Plans can't be bought \
                online yet: someone at Bookeyond Kid will reply to this address.

                Nothing changes until then, and you can withdraw the request from your account \
                page at %s/parent/account.
                """.formatted(plan.getName(), mail.publicUrl())));
    }

    @Transactional
    public void withdraw(UUID accountId) {
        UUID familyId = familyOf(accountId);
        PlanRequest open = requests.findOpenForUpdate(familyId)
                .orElseThrow(() -> new IllegalStateException("Your family has no plan request open"));
        open.resolve(PlanRequest.Status.WITHDRAWN, null, null, Instant.now());
        record(accountId, AuditActorType.PARENT, "PLAN_REQUEST_WITHDRAWN", open.getId(), Map.of());
    }

    @Transactional(readOnly = true)
    public Optional<OpenRequest> openRequestOf(UUID familyId) {
        return requests.findOpen(familyId).map(request -> new OpenRequest(request.getPlanId(),
                plans.findById(request.getPlanId()).map(SubscriptionPlan::getName).orElse("?"), request.getRequestedAt()));
    }

    // ------------------------------------------------------------------ administrators

    /** Open requests, oldest first. */
    @Transactional(readOnly = true)
    public PageResponse<AdminRow> openRequests(UUID adminId, PageResponse.Request page) {
        requireAdmin(adminId);
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", page.size()).addValue("offset", page.offset());
        Long total = jdbc.queryForObject("select count(*) from plan_requests where status = 'OPEN'", params, Long.class);
        List<AdminRow> rows = jdbc.query("""
                select r.id, r.family_id, f.name as family_name, a.email as parent_email, p.name as plan_name, r.requested_at
                from plan_requests r
                join families f on f.id = r.family_id
                join subscription_plans p on p.id = r.plan_id
                join accounts a on a.id = r.requested_by_account_id
                where r.status = 'OPEN'
                order by r.requested_at, r.id
                limit :limit offset :offset
                """, params, (rs, i) -> new AdminRow(rs.getObject("id", UUID.class), rs.getObject("family_id", UUID.class),
                rs.getString("family_name"), rs.getString("parent_email"), rs.getString("plan_name"),
                rs.getTimestamp("requested_at").toInstant()));
        return new PageResponse<>(rows, page.page(), page.size(), total == null ? 0 : total);
    }

    /** Declines an open request; the family is told, with the administrator's message if there is one. */
    @Transactional
    public void decline(UUID adminId, UUID requestId, String message) {
        requireAdmin(adminId);
        PlanRequest request = requests.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan request", requestId));
        request.resolve(PlanRequest.Status.DECLINED, adminId, message, Instant.now());
        Optional<String> named = plans.findById(request.getPlanId()).map(SubscriptionPlan::getName);
        String planName = named.orElse("requested");
        record(adminId, AuditActorType.ADMIN, "PLAN_REQUEST_DECLINED", requestId, Map.of());
        // The administrator's message is quoted as they wrote it, whatever the email's language.
        accounts.findById(request.getRequestedByAccountId()).ifPresent(parent ->
                events.publishEvent(UiLanguage.isKhmer(parent.getPreferredLanguage())
                        ? new EmailMessage(parent.getEmail(), "អំពីសំណើរបស់អ្នកសម្រាប់កញ្ចប់ " + named.orElse("ដែលបានស្នើ"), """
                        សួស្ដី,

                        យើងមិនអាចប្ដូរគ្រួសាររបស់អ្នកទៅកញ្ចប់ %s នៅពេលនេះបានទេ។%s

                        គ្រួសាររបស់អ្នកនៅតែប្រើកញ្ចប់បច្ចុប្បន្នរបស់ខ្លួន ហើយអ្វីដែលកូនៗរបស់អ្នកបានធ្វើ មិនផ្លាស់ប្ដូរទេ។
                        """.formatted(named.orElse("ដែលបានស្នើ"), message == null || message.isBlank() ? "" : "\n\n" + message.strip()))
                        : new EmailMessage(parent.getEmail(), "About your request for the " + planName + " plan", """
                        Hello,

                        We couldn't move your family to the %s plan just now.%s

                        Your family keeps its current plan, and nothing your children have done changes.
                        """.formatted(planName, message == null || message.isBlank() ? "" : "\n\n" + message.strip()))));
    }

    /**
     * Called when an administrator grants {@code planId} to the family: an open request for that
     * plan is answered by it, and the family is told. A grant of another plan leaves it open.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onGranted(UUID adminId, UUID familyId, UUID planId) {
        requests.findOpenForUpdate(familyId).filter(request -> request.getPlanId().equals(planId)).ifPresent(request -> {
            request.resolve(PlanRequest.Status.GRANTED, adminId, null, Instant.now());
            record(adminId, AuditActorType.ADMIN, "PLAN_REQUEST_GRANTED", request.getId(), Map.of());
            Optional<String> named = plans.findById(planId).map(SubscriptionPlan::getName);
            String planName = named.orElse("requested");
            accounts.findById(request.getRequestedByAccountId()).ifPresent(parent ->
                    events.publishEvent(UiLanguage.isKhmer(parent.getPreferredLanguage())
                            ? new EmailMessage(parent.getEmail(), "គ្រួសាររបស់អ្នកឥឡូវប្រើកញ្ចប់ " + named.orElse("ដែលបានស្នើ"), """
                            សួស្ដី,

                            តាមការស្នើសុំរបស់អ្នក គ្រួសាររបស់អ្នកឥឡូវប្រើកញ្ចប់ %s ហើយ។ អ្នកអាចមើលអ្វីដែលកញ្ចប់នេះរួមបញ្ចូល នៅលើទំព័រគណនីរបស់អ្នកនៅ៖

                            %s/parent/account
                            """.formatted(named.orElse("ដែលបានស្នើ"), mail.publicUrl()))
                            : new EmailMessage(parent.getEmail(), "Your family is on the " + planName + " plan", """
                            Hello,

                            As you asked, your family is now on the %s plan. You can see what it \
                            includes on your account page at %s/parent/account.
                            """.formatted(planName, mail.publicUrl()))));
        });
    }

    // ------------------------------------------------------------------

    private UUID familyOf(UUID accountId) {
        return memberships.findByAccountId(accountId).stream()
                .filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
                .map(Membership::getFamilyId)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Family for account", accountId));
    }

    private void requireAdmin(UUID accountId) {
        Account account = accounts.findById(accountId).orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        if (account.getPlatformRole() != PlatformRole.ADMIN) {
            throw new AccessDeniedException("Admin access required");
        }
    }

    private void record(UUID actorId, AuditActorType actorType, String action, UUID requestId, Map<String, Object> metadata) {
        audit.record(new RecordAuditEvent(null, actorId, actorType, action, "PLAN_REQUEST", requestId, metadata));
    }

}
