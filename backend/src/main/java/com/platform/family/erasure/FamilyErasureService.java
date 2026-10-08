package com.platform.family.erasure;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.child.entity.Child;
import com.platform.child.entity.ChildStatus;
import com.platform.child.repository.ChildRepository;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.family.entity.Membership;
import com.platform.family.entity.MembershipStatus;
import com.platform.family.repository.MembershipRepository;
import com.platform.family.service.FamilyAccessGuard;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.exception.AccountLockedException;
import com.platform.identity.exception.CurrentPasswordMismatchException;
import com.platform.identity.repository.AccountRepository;
import com.platform.identity.service.PasswordConfirmation;
import com.platform.notification.EmailMessage;
import com.platform.notification.PlatformMailProperties;
import com.platform.school.service.SchoolJoinService;
import com.platform.subscription.service.EntitlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Deleting a child or a family account (PD-24, as adopted in Y3 - please confirm).
 *
 * <p><b>Asking.</b> A parent confirms with their password. A child's deletion takes effect
 * for learning straight away - the child leaves every learning and school flow (404, like
 * any child the caller can't see) and every school's access ends - but the child stays
 * listed to the family, who can cancel until the date. An account's deletion changes
 * nothing until the date, so the family keeps using Bookeyond Kid and can cancel by
 * signing in.
 *
 * <p><b>Erasing.</b> After the grace period ({@link ErasureProperties#gracePeriod()}) the
 * sweep erases, in one transaction per child or account: the child's profile, settings,
 * journeys, attempts, every answer and the evidence, skill states, reviews, insights,
 * alerts, school grants and class places, consent records; for an account, also its
 * family, plan, sessions, links and the account itself. Audit events are kept: they name
 * ids, never people, and record that consent was given and that the erasure happened.
 * Evidence tables are append-only; only an erasure transaction may delete from them (V51).
 */
@Service
public class FamilyErasureService {

    private static final Logger log = LoggerFactory.getLogger(FamilyErasureService.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);

    private static final String ATTEMPTS = "(select id from activity_attempts where child_id = :c)";
    private static final String REVIEW_ITEMS = "(select id from review_items where child_id = :c)";
    private static final String REQUESTS = "(select id from recommendation_requests where child_id = :c)";
    private static final String ENROLLMENTS = "(select id from enrollments where child_id = :c)";
    private static final String ASSESSMENTS = "(select id from assessment_attempts where child_id = :c)";

    /** Every row about one child, children of rows first. */
    private static final List<String> CHILD_ERASURE = List.of(
            "delete from review_attempts where activity_attempt_id in " + ATTEMPTS + " or review_item_id in " + REVIEW_ITEMS,
            "delete from review_schedules where review_item_id in " + REVIEW_ITEMS,
            "delete from recommendation_outcomes where activity_attempt_id in " + ATTEMPTS
                    + " or recommendation_id in (select id from recommendations where recommendation_request_id in " + REQUESTS + ")",
            "delete from recommendations where recommendation_request_id in " + REQUESTS,
            "delete from recommendation_requests where child_id = :c",
            "delete from adaptation_decisions where child_id = :c or activity_attempt_id in " + ATTEMPTS,
            "delete from answer_records where activity_attempt_id in " + ATTEMPTS,
            "delete from activity_events where activity_attempt_id in " + ATTEMPTS,
            "delete from activity_attempts where child_id = :c",
            "delete from learning_sessions where child_id = :c",
            "delete from learning_path_items where learning_path_id in (select id from learning_paths where enrollment_id in "
                    + ENROLLMENTS + ")",
            "delete from learning_paths where enrollment_id in " + ENROLLMENTS,
            "delete from enrollments where child_id = :c",
            "delete from assessment_skill_results where assessment_result_id in (select id from assessment_results "
                    + "where assessment_attempt_id in " + ASSESSMENTS + ")",
            "delete from assessment_results where assessment_attempt_id in " + ASSESSMENTS,
            "delete from assessment_answers where assessment_attempt_id in " + ASSESSMENTS,
            "delete from assessment_attempts where child_id = :c",
            "delete from review_items where child_id = :c",
            "delete from child_goals where child_id = :c",
            "delete from child_preferences where child_id = :c",
            "delete from child_preferred_subjects where child_id = :c",
            "delete from child_settings where child_id = :c",
            "delete from class_enrollments where child_id = :c",
            "delete from consent_records where child_id = :c",
            "delete from learner_objective_states where child_id = :c",
            "delete from learner_profiles where child_id = :c",
            "delete from learner_skill_history where child_id = :c",
            "delete from learner_skill_states where child_id = :c",
            "delete from learner_streaks where child_id = :c",
            "delete from parent_alerts where child_id = :c",
            "delete from parent_insights where child_id = :c",
            "delete from progress_snapshots where child_id = :c",
            "delete from school_child_access where child_id = :c",
            "delete from children where id = :c");

    /**
     * The tables {@link #CHILD_ERASURE} empties: every table whose rows depend on a child
     * through foreign keys. A test compares this with the live schema, so a new table about
     * children can't be forgotten here.
     */
    static final Set<String> CHILD_TABLES = Set.of("activity_attempts", "activity_events", "adaptation_decisions",
            "answer_records", "assessment_answers", "assessment_attempts", "assessment_results",
            "assessment_skill_results", "child_goals", "child_preferences", "child_preferred_subjects", "child_settings",
            "children", "class_enrollments", "consent_records", "enrollments", "learner_objective_states",
            "learner_profiles", "learner_skill_history", "learner_skill_states", "learner_streaks", "learning_path_items",
            "learning_paths", "learning_sessions", "parent_alerts", "parent_insights", "progress_snapshots",
            "recommendation_outcomes", "recommendation_requests", "recommendations", "review_attempts", "review_items",
            "review_schedules", "school_child_access");

    /** What an account erasure deletes besides its family's children, children of rows first. */
    private static final List<String> ACCOUNT_ERASURE = List.of(
            "delete from child_goals where parent_goal_id in (select id from parent_goals where account_id = :a)",
            "delete from parent_goals where account_id = :a",
            "delete from parent_alerts where account_id = :a",
            "delete from memberships where account_id = :a",
            "delete from account_sessions where account_id = :a",
            "delete from account_action_tokens where account_id = :a",
            "delete from idempotency_records where actor_id = :a",
            "delete from accounts where id = :a");

    private final ErasureProperties properties;
    private final PasswordConfirmation passwords;
    private final FamilyAccessGuard familyGuard;
    private final ChildRepository children;
    private final AccountRepository accounts;
    private final MembershipRepository memberships;
    private final SchoolJoinService schoolJoin;
    private final EntitlementService entitlements;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final PlatformMailProperties mail;
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate eachInItsOwnTransaction;

    public FamilyErasureService(ErasureProperties properties, PasswordConfirmation passwords,
                                FamilyAccessGuard familyGuard, ChildRepository children, AccountRepository accounts,
                                MembershipRepository memberships, SchoolJoinService schoolJoin,
                                EntitlementService entitlements, AuditService audit, ApplicationEventPublisher events,
                                PlatformMailProperties mail, NamedParameterJdbcTemplate jdbc,
                                PlatformTransactionManager transactions) {
        this.properties = properties;
        this.passwords = passwords;
        this.familyGuard = familyGuard;
        this.children = children;
        this.accounts = accounts;
        this.memberships = memberships;
        this.schoolJoin = schoolJoin;
        this.entitlements = entitlements;
        this.audit = audit;
        this.events = events;
        this.mail = mail;
        this.jdbc = jdbc;
        this.eachInItsOwnTransaction = new TransactionTemplate(transactions);
    }

    // ------------------------------------------------------------------ a child

    /** Schedules a child's erasure. The child leaves learning and every school now. */
    @Transactional(noRollbackFor = {CurrentPasswordMismatchException.class, AccountLockedException.class})
    public Instant scheduleChildErasure(UUID accountId, UUID childId, String password) {
        familyGuard.requireChildAccess(accountId, childId);
        Account parent = passwords.confirm(accountId, password);
        Child child = children.findByIdForUpdate(childId).orElseThrow(() -> new ResourceNotFoundException("Child", childId));
        int schoolsEnded = schoolJoin.revokeEverySchool(accountId, childId);
        Instant at = Instant.now().plus(properties.gracePeriod()).truncatedTo(ChronoUnit.SECONDS);
        child.scheduleErasure(at);
        record(parent.getId(), AuditActorType.PARENT, "CHILD_ERASURE_SCHEDULED", "CHILD", childId,
                Map.of("eraseAfter", at.toString(), "schoolAccessEnded", schoolsEnded));
        events.publishEvent(new EmailMessage(parent.getEmail(), child.getDisplayName() + " will be deleted from Bookeyond Kid", """
                Hello,

                You asked to delete %s from Bookeyond Kid. On %s we will erase %s's profile \
                and everything about their learning: journeys, answers, skills and insights.

                Until then you can change your mind: open your family page and choose \
                "Keep %s". Any school's access ended today.

                If you didn't ask for this, sign in and keep %s, then change your password.
                """.formatted(child.getDisplayName(), DAY.format(at), child.getDisplayName(), child.getDisplayName(),
                child.getDisplayName())));
        return at;
    }

    /** Keeps a child whose erasure was scheduled. School access does not come back by itself. */
    @Transactional
    public Child cancelChildErasure(UUID accountId, UUID childId) {
        familyGuard.requireChildRecord(accountId, childId);
        Child child = children.findByIdForUpdate(childId).orElseThrow(() -> new ResourceNotFoundException("Child", childId));
        if (child.getStatus() != ChildStatus.DELETION_PENDING) {
            throw new IllegalStateException("This child is not being deleted");
        }
        entitlements.requireRoomForChild(child.getFamilyId());
        child.cancelErasure();
        record(accountId, AuditActorType.PARENT, "CHILD_ERASURE_CANCELLED", "CHILD", childId, Map.of());
        return child;
    }

    // ------------------------------------------------------------------ an account

    /**
     * Schedules the erasure of a family account and, with it, the family and its children.
     * Only a family account can delete itself (staff accounts are closed by their school or an
     * administrator), and only when it is its family's sole parent.
     */
    @Transactional(noRollbackFor = {CurrentPasswordMismatchException.class, AccountLockedException.class})
    public Instant scheduleAccountErasure(UUID accountId, String password) {
        Account account = passwords.confirm(accountId, password);
        if (account.getPlatformRole() != PlatformRole.PARENT) {
            throw new AccessDeniedException("Only a family account can be deleted here");
        }
        if (account.getEraseAfter() != null) {
            throw new IllegalStateException("This account is already being deleted");
        }
        for (Membership membership : activeMemberships(accountId)) {
            boolean shared = memberships.findByFamilyId(membership.getFamilyId()).stream()
                    .anyMatch(other -> other.getStatus() == MembershipStatus.ACTIVE && !other.getAccountId().equals(accountId));
            if (shared) {
                throw new IllegalStateException("Another parent shares this family; deleting a shared family isn't supported yet");
            }
        }
        Instant at = Instant.now().plus(properties.gracePeriod()).truncatedTo(ChronoUnit.SECONDS);
        account.scheduleErasure(at);
        record(accountId, AuditActorType.PARENT, "ACCOUNT_ERASURE_SCHEDULED", "ACCOUNT", accountId,
                Map.of("eraseAfter", at.toString()));
        events.publishEvent(new EmailMessage(account.getEmail(), "Your Bookeyond Kid account will be deleted", """
                Hello,

                You asked to delete your Bookeyond Kid account. On %s we will erase it with \
                your family: every child's profile and learning, your plan, and your sign-in.

                Until then everything keeps working, and you can change your mind: sign in \
                at %s and choose "Keep my account" on your account page.

                If you didn't ask for this, sign in, keep your account, and change your password.
                """.formatted(DAY.format(at), mail.publicUrl())));
        return at;
    }

    @Transactional
    public void cancelAccountErasure(UUID accountId) {
        Account account = accounts.findForUpdate(accountId).orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        if (account.getEraseAfter() == null) {
            throw new IllegalStateException("This account is not being deleted");
        }
        account.cancelErasure();
        record(accountId, AuditActorType.valueOf(account.getPlatformRole().name()), "ACCOUNT_ERASURE_CANCELLED", "ACCOUNT",
                accountId, Map.of());
    }

    // ------------------------------------------------------------------ the sweep

    /** Carries out every deletion that is due, each in its own transaction. Returns how many were done. */
    public int eraseDue() {
        Timestamp now = Timestamp.from(Instant.now());
        int done = 0;
        List<UUID> dueChildren = jdbc.queryForList("select id from children where status = 'DELETION_PENDING' "
                + "and erase_after <= :now order by erase_after limit 200", Map.of("now", now), UUID.class);
        for (UUID childId : dueChildren) {
            done += run("child", childId, () -> eraseChildIfDue(childId));
        }
        List<UUID> dueAccounts = jdbc.queryForList("select id from accounts where erase_after <= :now "
                + "order by erase_after limit 200", Map.of("now", now), UUID.class);
        for (UUID accountId : dueAccounts) {
            done += run("account", accountId, () -> eraseAccountIfDue(accountId));
        }
        return done;
    }

    private int run(String what, UUID id, java.util.function.BooleanSupplier erasure) {
        try {
            return Boolean.TRUE.equals(eachInItsOwnTransaction.execute(status -> erasure.getAsBoolean())) ? 1 : 0;
        } catch (RuntimeException e) {
            // Left due: the next sweep tries again. An erasure that keeps failing needs a person.
            log.error("Erasure of {} {} failed; it stays scheduled and will be retried", what, id, e);
            return 0;
        }
    }

    private boolean eraseChildIfDue(UUID childId) {
        List<Map<String, Object>> due = jdbc.queryForList("select display_name, family_id from children where id = :c "
                + "and status = 'DELETION_PENDING' and erase_after <= now() for update skip locked", Map.of("c", childId));
        if (due.isEmpty()) {
            return false; // kept by the family meanwhile, or another instance has it
        }
        String name = (String) due.getFirst().get("display_name");
        List<String> parents = parentEmails((UUID) due.getFirst().get("family_id"));
        allowErasure();
        Map<String, Integer> erased = eraseChildRows(childId);
        record(null, AuditActorType.SYSTEM, "CHILD_ERASED", "CHILD", childId, Map.of("rowsErased", total(erased)));
        for (String to : parents) {
            events.publishEvent(new EmailMessage(to, name + " has been deleted from Bookeyond Kid", """
                    Hello,

                    As you asked, %s's profile and everything about their learning have been \
                    erased from Bookeyond Kid. This can't be undone.
                    """.formatted(name)));
        }
        return true;
    }

    private boolean eraseAccountIfDue(UUID accountId) {
        List<Map<String, Object>> due = jdbc.queryForList("select email from accounts where id = :a and erase_after <= now() "
                + "for update skip locked", Map.of("a", accountId));
        if (due.isEmpty()) {
            return false;
        }
        String email = (String) due.getFirst().get("email");
        allowErasure();
        int rows = 0;
        int childrenErased = 0;
        for (Membership membership : activeMemberships(accountId)) {
            UUID familyId = membership.getFamilyId();
            boolean shared = memberships.findByFamilyId(familyId).stream()
                    .anyMatch(other -> other.getStatus() == MembershipStatus.ACTIVE && !other.getAccountId().equals(accountId));
            if (shared) {
                throw new IllegalStateException("Family " + familyId + " is shared; not erased");
            }
            for (UUID childId : jdbc.queryForList("select id from children where family_id = :f", Map.of("f", familyId),
                    UUID.class)) {
                rows += total(eraseChildRows(childId));
                childrenErased++;
            }
            MapSqlParameterSource family = new MapSqlParameterSource("f", familyId);
            rows += jdbc.update("delete from plan_requests where family_id = :f", family);
            rows += jdbc.update("delete from family_subscriptions where family_id = :f", family);
            rows += jdbc.update("delete from memberships where family_id = :f", family);
            rows += jdbc.update("delete from families where id = :f", family);
        }
        MapSqlParameterSource account = new MapSqlParameterSource("a", accountId);
        for (String statement : ACCOUNT_ERASURE) {
            rows += jdbc.update(statement, account);
        }
        record(null, AuditActorType.SYSTEM, "ACCOUNT_ERASED", "ACCOUNT", accountId,
                Map.of("childrenErased", childrenErased, "rowsErased", rows));
        events.publishEvent(new EmailMessage(email, "Your Bookeyond Kid account has been deleted", """
                Hello,

                As you asked, your Bookeyond Kid account has been erased, with your family \
                and everything about your children's learning. This can't be undone.

                Thank you for learning with us.
                """));
        return true;
    }

    /** Lets this transaction - and only it - delete append-only evidence rows (V51). */
    private void allowErasure() {
        jdbc.queryForObject("select set_config('bookeyond.erasure', 'on', true)", Map.of(), String.class);
    }

    private Map<String, Integer> eraseChildRows(UUID childId) {
        MapSqlParameterSource child = new MapSqlParameterSource("c", childId);
        Map<String, Integer> erased = new LinkedHashMap<>();
        for (String statement : CHILD_ERASURE) {
            erased.put(statement.split(" ")[2], jdbc.update(statement, child));
        }
        return erased;
    }

    private static int total(Map<String, Integer> erased) {
        return erased.values().stream().mapToInt(Integer::intValue).sum();
    }

    private List<Membership> activeMemberships(UUID accountId) {
        return memberships.findByAccountId(accountId).stream()
                .filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE).toList();
    }

    private List<String> parentEmails(UUID familyId) {
        return memberships.findByFamilyId(familyId).stream()
                .filter(membership -> membership.getStatus() == MembershipStatus.ACTIVE)
                .map(membership -> accounts.findById(membership.getAccountId()))
                .flatMap(java.util.Optional::stream)
                .map(Account::getEmail).toList();
    }

    private void record(UUID actorId, AuditActorType actorType, String action, String resourceType, UUID resourceId,
                        Map<String, Object> metadata) {
        audit.record(new RecordAuditEvent(null, actorId, actorType, action, resourceType, resourceId, metadata));
    }

}
