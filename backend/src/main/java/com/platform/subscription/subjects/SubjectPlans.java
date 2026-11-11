package com.platform.subscription.subjects;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.content.service.ContentAccessGuard;
import com.platform.curriculum.service.SubjectService;
import com.platform.identity.entity.Account;
import com.platform.subscription.entity.PlanAudience;
import com.platform.subscription.entity.PlanStatus;
import com.platform.subscription.entity.SubscriptionPlan;
import com.platform.subscription.repository.SubscriptionPlanRepository;
import com.platform.subscription.service.EntitlementService;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owner requirement 2026-11-10 (V89): which family plans include a subject. No rows: every family plan (every subject
 * that existed before, and a new one until an administrator chooses). Plans are read from the plan records - names,
 * prices and limits stay the plans' own. The rule is applied by the server wherever a family adds, switches to or plays
 * a subject; a journey a school class assigns is the school's and is not checked here.
 */
@Service
public class SubjectPlans {

    private final JdbcTemplate jdbc;
    private final SubscriptionPlanRepository plans;
    private final EntitlementService entitlements;
    private final SubjectService subjects;
    private final ContentAccessGuard guard;
    private final AuditService audit;
    @org.springframework.beans.factory.annotation.Autowired
    private com.platform.curriculum.service.CurriculumService curricula;

    public SubjectPlans(JdbcTemplate jdbc, SubscriptionPlanRepository plans, EntitlementService entitlements,
                        SubjectService subjects, ContentAccessGuard guard, AuditService audit) {
        this.jdbc = jdbc;
        this.plans = plans;
        this.entitlements = entitlements;
        this.subjects = subjects;
        this.guard = guard;
        this.audit = audit;
    }

    /** A plan as the Studio lists it: its own words (English and the administrator's Khmer), never copied. */
    public record PlanChoice(UUID id, String code, String name, String nameKm, PlanStatus status, boolean defaultPlan) {
    }

    /** A subject's plans: {@code everyPlan} when it has none chosen; otherwise the chosen ones. */
    public record Assignment(UUID subjectId, boolean everyPlan, List<UUID> planIds, List<PlanChoice> plans) {
    }

    // ------------------------------------------------------------------ reading

    /** The plans that include the subject (empty: every family plan). */
    public List<UUID> plansOf(UUID subjectId) {
        return jdbc.queryForList("select plan_id from subject_plans where subject_id = ? order by created_at, plan_id", UUID.class, subjectId);
    }

    /** Whether the plan includes the subject. */
    public boolean includes(UUID subjectId, UUID planId) {
        List<UUID> chosen = plansOf(subjectId);
        return chosen.isEmpty() || chosen.contains(planId);
    }

    /** The subjects a plan does not include (those with plans chosen, none of them this one). */
    public Set<UUID> subjectsOutside(UUID planId) {
        return new HashSet<>(jdbc.queryForList("""
                select distinct s.subject_id from subject_plans s
                where not exists (select 1 from subject_plans x where x.subject_id = s.subject_id and x.plan_id = ?)
                """, UUID.class, planId));
    }

    /**
     * How many subjects families can learn now (a published platform curriculum) that this plan doesn't include - what
     * the family is told other plans add. Never which ones' content, only that there are more.
     */
    @Transactional(readOnly = true)
    public int offeredSubjectsOutside(UUID planId) {
        Set<UUID> outside = subjectsOutside(planId);
        if (outside.isEmpty()) return 0;
        return (int) curricula.listFamilyCatalog().stream()
                .map(com.platform.curriculum.entity.Curriculum::getSubjectId)
                .filter(outside::contains)
                .distinct().count();
    }

    /** The subjects outside the family's effective plan (a subscription's, or the default one). */
    public Set<UUID> subjectsOutsideFamilyPlan(UUID familyId) {
        return subjectsOutside(entitlements.effectivePlan(familyId).plan().getId());
    }

    /** Refuses (409) when the family's effective plan doesn't include the subject. */
    public void requireInFamilyPlan(UUID familyId, UUID subjectId) {
        if (!includes(subjectId, entitlements.effectivePlan(familyId).plan().getId())) {
            throw new SubjectNotInPlanException();
        }
    }

    // ------------------------------------------------------------------ Studio

    @Transactional(readOnly = true)
    public Assignment view(UUID accountId, UUID subjectId) {
        guard.requireCatalogAdmin(accountId);
        subjects.getById(subjectId);
        return assignment(subjectId);
    }

    /**
     * Sets the subject's plans: {@code everyPlan}, or the listed family plans (at least one; drafts may be chosen ahead
     * of their offer, a retired plan only if already chosen). Administrators only; audited with before and after.
     */
    @Transactional
    public Assignment assign(UUID accountId, UUID subjectId, boolean everyPlan, List<UUID> planIds) {
        Account admin = guard.requireCatalogAdmin(accountId);
        subjects.getById(subjectId);
        List<UUID> before = plansOf(subjectId);
        Set<UUID> chosen = new LinkedHashSet<>(everyPlan || planIds == null ? List.of() : planIds);
        if (!everyPlan && chosen.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one plan, or every plan.");
        }
        for (UUID planId : chosen) {
            SubscriptionPlan plan = plans.findById(planId)
                    .orElseThrow(() -> new IllegalArgumentException("That plan doesn't exist."));
            if (plan.getAudience() != PlanAudience.FAMILY) {
                throw new IllegalArgumentException("A subject is assigned to family plans only.");
            }
            if (plan.getStatus() == PlanStatus.RETIRED && !before.contains(planId)) {
                throw new IllegalArgumentException("A retired plan can't be chosen.");
            }
        }
        jdbc.update("delete from subject_plans where subject_id = ?", subjectId);
        for (UUID planId : chosen) {
            jdbc.update("insert into subject_plans (subject_id, plan_id) values (?, ?)", subjectId, planId);
        }
        audit.record(new RecordAuditEvent(null, admin.getId(), AuditActorType.ADMIN, "CATALOG_SUBJECT_PLANS_CHANGED",
                "SUBJECT", subjectId, Map.of("before", before.stream().map(UUID::toString).toList(),
                "after", chosen.stream().map(UUID::toString).toList())));
        return assignment(subjectId);
    }

    /** A plan's new version (V79) includes the subjects its source included. */
    public void copyToNewVersion(UUID sourcePlanId, UUID copyPlanId) {
        jdbc.update("""
                insert into subject_plans (subject_id, plan_id)
                select subject_id, ? from subject_plans where plan_id = ?
                on conflict do nothing
                """, copyPlanId, sourcePlanId);
    }

    private Assignment assignment(UUID subjectId) {
        List<UUID> chosen = plansOf(subjectId);
        // Every family plan that can matter: active and draft ones, and a retired one still chosen.
        List<PlanChoice> choices = plans.findAllByOrderByCreatedAtAsc().stream()
                .filter(plan -> plan.getAudience() == PlanAudience.FAMILY)
                .filter(plan -> plan.getStatus() != PlanStatus.RETIRED || chosen.contains(plan.getId()))
                .sorted(Comparator.comparing((SubscriptionPlan plan) -> !plan.isDefaultPlan())
                        .thenComparing(plan -> plan.getStatus() != PlanStatus.ACTIVE))
                .map(plan -> new PlanChoice(plan.getId(), plan.getCode(), plan.getName(), plan.getNameKm(), plan.getStatus(),
                        plan.isDefaultPlan()))
                .toList();
        return new Assignment(subjectId, chosen.isEmpty(), chosen, choices);
    }
}
