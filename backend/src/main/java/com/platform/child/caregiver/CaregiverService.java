package com.platform.child.caregiver;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.child.entity.Child;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.family.entity.MembershipStatus;
import com.platform.family.repository.FamilyRepository;
import com.platform.family.repository.MembershipRepository;
import com.platform.family.service.FamilyAccessGuard;
import com.platform.identity.UiLanguage;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import com.platform.notification.EmailMessage;
import com.platform.notification.PlatformMailProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * M10b: trusted caregivers. A member of a child's family invites an adult by email for that one child, to see the
 * child's progress (VIEW_PROGRESS) or also to open the child's learning space (OPEN_SPACE). The person accepts after
 * signing in with that address, confirmed. A caregiver is never a member of the family: what they may reach is decided
 * per endpoint by {@link com.platform.family.service.ChildAccess}; the family's settings, billing, export, deletion and
 * other children stay out of reach. The family changes the scope or ends it at once. At most
 * {@code platform.caregivers.max-per-family} people per family (owner decision D-05; 2 proposed).
 */
@Service
public class CaregiverService {

    static final Set<String> SCOPES = Set.of("VIEW_PROGRESS", "OPEN_SPACE");

    static final String UNKNOWN_SCOPE = "A caregiver can see progress, or also open the child's space";
    static final String IN_FAMILY = "This person is already in your family";
    static final String YOURSELF = "You can't invite yourself";
    static final String ALREADY = "This address is already invited for this child";
    static final String TOO_MANY = "Your family already has as many caregivers as allowed: remove one first";
    static final String FAMILY_ACCOUNTS_ONLY = "Only a family account can help with a child";
    static final String CONFIRM_FIRST = "Confirm your email address before accepting an invitation";
    static final String NOT_OPEN = "This invitation is no longer open";

    /** One caregiver of a child, as the family sees it. */
    public record Caregiver(UUID id, String email, String scope, String status, Instant invitedAt, Instant acceptedAt) {
    }

    /** One child a caregiver helps with, or is invited to. */
    public record CaredFor(UUID id, UUID childId, String childName, String scope, String status, Instant invitedAt) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final FamilyAccessGuard guard;
    private final FamilyRepository families;
    private final MembershipRepository memberships;
    private final AccountRepository accounts;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final PlatformMailProperties mail;
    private final int maxPerFamily;

    public CaregiverService(NamedParameterJdbcTemplate jdbc, FamilyAccessGuard guard, FamilyRepository families,
                            MembershipRepository memberships, AccountRepository accounts, AuditService audit,
                            ApplicationEventPublisher events, PlatformMailProperties mail,
                            @Value("${platform.caregivers.max-per-family:2}") int maxPerFamily) {
        this.jdbc = jdbc;
        this.guard = guard;
        this.families = families;
        this.memberships = memberships;
        this.accounts = accounts;
        this.audit = audit;
        this.events = events;
        this.mail = mail;
        this.maxPerFamily = maxPerFamily;
    }

    // ------------------------------------------------------------------ the family's side

    @Transactional(readOnly = true)
    public List<Caregiver> of(UUID accountId, UUID childId) {
        guard.requireChildAccess(accountId, childId);
        return jdbc.query("""
                SELECT id, invited_email, scope, status, invited_at, accepted_at FROM child_caregivers
                 WHERE child_id = :c AND status IN ('INVITED', 'ACTIVE') ORDER BY invited_at
                """, Map.of("c", childId), (rs, i) -> new Caregiver(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getTimestamp(5).toInstant(), rs.getTimestamp(6) == null ? null : rs.getTimestamp(6).toInstant()));
    }

    /** Every caregiver and invitation the child has had (the family's data export; the caller checked access). */
    @Transactional(readOnly = true)
    public List<Caregiver> historyOf(UUID childId) {
        return jdbc.query("""
                SELECT id, invited_email, scope, status, invited_at, accepted_at FROM child_caregivers
                 WHERE child_id = :c ORDER BY invited_at
                """, Map.of("c", childId), (rs, i) -> new Caregiver(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getTimestamp(5).toInstant(), rs.getTimestamp(6) == null ? null : rs.getTimestamp(6).toInstant()));
    }

    @Transactional
    public Caregiver invite(UUID accountId, UUID childId, String email, String scope) {
        Child child = guard.requireChildAccess(accountId, childId);
        requireScope(scope);
        Account inviter = accounts.findById(accountId).orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        String address = Account.normalize(email);
        if (address.equals(inviter.getNormalizedEmail())) {
            throw new IllegalArgumentException(YOURSELF);
        }
        // Serialised per family, so two invitations at once can't both take the last place.
        families.findByIdForUpdate(child.getFamilyId()).orElseThrow(() -> new ResourceNotFoundException("Family", child.getFamilyId()));
        boolean inFamily = accounts.findByNormalizedEmail(address)
                .flatMap(a -> memberships.findByFamilyIdAndAccountId(child.getFamilyId(), a.getId()))
                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE).isPresent();
        if (inFamily) {
            throw new IllegalStateException(IN_FAMILY);
        }
        Integer others = jdbc.queryForObject("""
                SELECT count(DISTINCT invited_email) FROM child_caregivers
                 WHERE family_id = :f AND status IN ('INVITED', 'ACTIVE') AND invited_email <> :e
                """, Map.of("f", child.getFamilyId(), "e", address), Integer.class);
        if (others != null && others >= maxPerFamily) {
            throw new IllegalStateException(TOO_MANY);
        }
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            jdbc.update("""
                    INSERT INTO child_caregivers (id, child_id, family_id, invited_email, scope, status, invited_by_account_id, invited_at)
                    VALUES (:id, :c, :f, :e, :s, 'INVITED', :by, :now)
                    """, new MapSqlParameterSource("id", id).addValue("c", childId).addValue("f", child.getFamilyId())
                    .addValue("e", address).addValue("s", scope).addValue("by", accountId).addValue("now", Timestamp.from(now)));
        } catch (DuplicateKeyException already) {
            throw new IllegalStateException(ALREADY);
        }
        tell(inviter, address, child.getDisplayName(), scope);
        record(accountId, AuditActorType.PARENT, "CAREGIVER_INVITED", childId, Map.of("caregiver", id.toString(), "scope", scope));
        return new Caregiver(id, address, scope, "INVITED", now, null);
    }

    @Transactional
    public Caregiver changeScope(UUID accountId, UUID childId, UUID caregiverId, String scope) {
        guard.requireChildAccess(accountId, childId);
        requireScope(scope);
        int changed = jdbc.update("""
                UPDATE child_caregivers SET scope = :s, version = version + 1
                 WHERE id = :id AND child_id = :c AND status IN ('INVITED', 'ACTIVE')
                """, Map.of("s", scope, "id", caregiverId, "c", childId));
        if (changed != 1) {
            throw new ResourceNotFoundException("Caregiver", caregiverId);
        }
        record(accountId, AuditActorType.PARENT, "CAREGIVER_SCOPE_CHANGED", childId, Map.of("caregiver", caregiverId.toString(), "scope", scope));
        return of(accountId, childId).stream().filter(c -> c.id().equals(caregiverId)).findFirst().orElseThrow();
    }

    /** The family ends a caregiver or withdraws an invitation: at once. */
    @Transactional
    public void remove(UUID accountId, UUID childId, UUID caregiverId) {
        guard.requireChildAccess(accountId, childId);
        int changed = jdbc.update("""
                UPDATE child_caregivers SET status = 'REVOKED', ended_at = now(), version = version + 1
                 WHERE id = :id AND child_id = :c AND status IN ('INVITED', 'ACTIVE')
                """, Map.of("id", caregiverId, "c", childId));
        if (changed != 1) {
            throw new ResourceNotFoundException("Caregiver", caregiverId);
        }
        record(accountId, AuditActorType.PARENT, "CAREGIVER_REMOVED", childId, Map.of("caregiver", caregiverId.toString()));
    }

    // ------------------------------------------------------------------ the caregiver's side

    /** Invitations to this account's address, and the children it helps with now. */
    @Transactional(readOnly = true)
    public List<CaredFor> caring(UUID accountId) {
        Account account = accounts.findById(accountId).orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        return jdbc.query("""
                SELECT g.id, g.child_id, c.display_name, g.scope, g.status, g.invited_at
                  FROM child_caregivers g JOIN children c ON c.id = g.child_id
                 WHERE c.status = 'ACTIVE'
                   AND ((g.status = 'ACTIVE' AND g.account_id = :a) OR (g.status = 'INVITED' AND g.invited_email = :e))
                 ORDER BY g.status, c.display_name
                """, Map.of("a", accountId, "e", account.getNormalizedEmail()), (rs, i) -> new CaredFor(rs.getObject(1, UUID.class),
                rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getString(5), rs.getTimestamp(6).toInstant()));
    }

    @Transactional
    public CaredFor accept(UUID accountId, UUID caregiverId) {
        Account account = accounts.findForUpdate(accountId).orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        Map<String, Object> row = openInvitation(caregiverId);
        if (!account.getNormalizedEmail().equals(row.get("invited_email"))) {
            throw new ResourceNotFoundException("Caregiver", caregiverId);
        }
        if (account.getPlatformRole() != PlatformRole.PARENT) {
            throw new IllegalStateException(FAMILY_ACCOUNTS_ONLY);
        }
        if (!account.isEmailVerified()) {
            throw new IllegalStateException(CONFIRM_FIRST);
        }
        UUID childId = (UUID) row.get("child_id");
        UUID familyId = (UUID) row.get("family_id");
        if (memberships.findByFamilyIdAndAccountId(familyId, accountId).filter(m -> m.getStatus() == MembershipStatus.ACTIVE).isPresent()) {
            throw new IllegalStateException(IN_FAMILY);
        }
        jdbc.update("""
                UPDATE child_caregivers SET status = 'ACTIVE', account_id = :a, accepted_at = now(), version = version + 1
                 WHERE id = :id AND status = 'INVITED'
                """, Map.of("a", accountId, "id", caregiverId));
        record(accountId, AuditActorType.PARENT, "CAREGIVER_ACCEPTED", childId, Map.of("caregiver", caregiverId.toString()));
        return caring(accountId).stream().filter(c -> c.id().equals(caregiverId)).findFirst().orElseThrow();
    }

    @Transactional
    public void decline(UUID accountId, UUID caregiverId) {
        Account account = accounts.findById(accountId).orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        Map<String, Object> row = openInvitation(caregiverId);
        if (!account.getNormalizedEmail().equals(row.get("invited_email"))) {
            throw new ResourceNotFoundException("Caregiver", caregiverId);
        }
        jdbc.update("UPDATE child_caregivers SET status = 'DECLINED', ended_at = now(), version = version + 1 WHERE id = :id AND status = 'INVITED'",
                Map.of("id", caregiverId));
        record(accountId, AuditActorType.PARENT, "CAREGIVER_DECLINED", (UUID) row.get("child_id"), Map.of("caregiver", caregiverId.toString()));
    }

    /** The caregiver stops helping. */
    @Transactional
    public void leave(UUID accountId, UUID caregiverId) {
        List<UUID> child = jdbc.queryForList("""
                UPDATE child_caregivers SET status = 'REVOKED', ended_at = now(), version = version + 1
                 WHERE id = :id AND account_id = :a AND status = 'ACTIVE' RETURNING child_id
                """, Map.of("id", caregiverId, "a", accountId), UUID.class);
        if (child.size() != 1) {
            throw new ResourceNotFoundException("Caregiver", caregiverId);
        }
        record(accountId, AuditActorType.PARENT, "CAREGIVER_LEFT", child.getFirst(), Map.of("caregiver", caregiverId.toString()));
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, Object> openInvitation(UUID caregiverId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT g.child_id, g.family_id, g.invited_email, g.status FROM child_caregivers g JOIN children c ON c.id = g.child_id
                 WHERE g.id = :id AND c.status = 'ACTIVE' FOR UPDATE OF g
                """, Map.of("id", caregiverId));
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Caregiver", caregiverId);
        }
        if (!"INVITED".equals(rows.getFirst().get("status"))) {
            throw new IllegalStateException(NOT_OPEN);
        }
        return rows.getFirst();
    }

    private static void requireScope(String scope) {
        if (scope == null || !SCOPES.contains(scope)) {
            throw new IllegalArgumentException(UNKNOWN_SCOPE);
        }
    }

    /** The invitation email, in the inviting parent's language; it names the child, never the parent's address. */
    private void tell(Account inviter, String address, String childName, String scope) {
        boolean space = scope.equals("OPEN_SPACE");
        events.publishEvent(UiLanguage.isKhmer(inviter.getPreferredLanguage())
                ? new EmailMessage(address, "អ្នកត្រូវបានអញ្ជើញឱ្យតាមដានការរៀនរបស់ " + childName + " លើ Bookeyond Kid", """
                សួស្ដី,

                ឪពុកម្ដាយម្នាក់នៅលើ Bookeyond Kid បានអញ្ជើញអ្នកឱ្យ%s របស់ %s។

                សូមចូលគណនី ឬបង្កើតគណនីដោយប្រើអាសយដ្ឋានអ៊ីមែលនេះ ហើយបញ្ជាក់វា នៅ៖

                %s/login

                បន្ទាប់មក អ្នកនឹងឃើញការអញ្ជើញនេះ ហើយអាចទទួលយក ឬបដិសេធវាបាន។ អ្នកនឹងមិនឃើញការកំណត់ ការបង់ប្រាក់ ឬកុមារផ្សេងទៀតរបស់គ្រួសារនោះទេ។

                ប្រសិនបើអ្នកមិនបានរំពឹងការនេះទេ អ្នកអាចមិនអើពើនឹងអ៊ីមែលនេះបាន។
                """.formatted(space ? "មើលការរៀន និងបើកកន្លែងរៀន" : "មើលការរៀន", childName, mail.publicUrl()))
                : new EmailMessage(address, "You're invited to follow " + childName + "'s learning on Bookeyond Kid", """
                Hello,

                A parent on Bookeyond Kid has invited you to %s.

                Sign in, or create an account with this email address and confirm it, at:

                %s/login

                You'll then see the invitation and can accept or decline it. You won't see the family's settings,
                payments or other children.

                If you weren't expecting this, you can ignore this email.
                """.formatted(space ? "see " + childName + "'s learning and open their learning space"
                : "see " + childName + "'s learning", mail.publicUrl())));
    }

    private void record(UUID accountId, AuditActorType actor, String action, UUID childId, Map<String, Object> details) {
        audit.record(new RecordAuditEvent(null, accountId, actor, action, "CHILD", childId, details));
    }

}
