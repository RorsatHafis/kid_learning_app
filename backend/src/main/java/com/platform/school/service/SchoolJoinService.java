package com.platform.school.service;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.child.entity.Child;
import com.platform.child.repository.ChildRepository;
import com.platform.common.entity.CatalogStatus;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.common.web.TooManyAttemptsException;
import com.platform.curriculum.repository.CurriculumRepository;
import com.platform.curriculum.service.CurriculumService;
import com.platform.family.service.FamilyAccessGuard;
import com.platform.identity.entity.Account;
import com.platform.identity.repository.AccountRepository;
import com.platform.learning.entity.LearningPathItem;
import com.platform.learning.repository.LearningPathItemRepository;
import com.platform.learning.service.EnrollmentService;
import com.platform.school.entity.ClassCurriculumAssignment;
import com.platform.school.entity.ClassCurriculumAssignmentStatus;
import com.platform.school.entity.ClassEnrollment;
import com.platform.school.entity.ClassEnrollmentStatus;
import com.platform.school.entity.ClassJoinCode;
import com.platform.school.entity.School;
import com.platform.school.entity.SchoolChildAccess;
import com.platform.school.entity.SchoolChildAccessStatus;
import com.platform.school.entity.SchoolClass;
import com.platform.school.repository.ClassCurriculumAssignmentRepository;
import com.platform.school.repository.ClassEnrollmentRepository;
import com.platform.school.repository.ClassJoinCodeRepository;
import com.platform.school.repository.SchoolChildAccessRepository;
import com.platform.school.repository.SchoolClassRepository;
import com.platform.school.repository.SchoolRepository;
import com.platform.security.ratelimit.TokenBucketLimiter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * PD-01, family-granted school access (PLATFORM-EXPANSION.md §4). A class's staff hand out a code;
 * a parent of the child looks it up, sees the school, the class and what the school will see, and
 * joins. Joining grants the school access to the child's school-assigned learning, puts the child
 * in the class and starts the class's curricula. Only the family ends the grant; the class may
 * remove a child from itself. Everything here is audited.
 */
@Service
public class SchoolJoinService {

    /** Letters and digits that can't be confused when read aloud or copied (no 0/O, 1/I/L); V43 checks it. */
    static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int CODE_LENGTH = 8;
    static final Duration CODE_LIFETIME = Duration.ofDays(14);

    private final SchoolAccessGuard schoolGuard;
    private final FamilyAccessGuard familyGuard;
    private final AccountRepository accounts;
    private final ChildRepository children;
    private final SchoolRepository schools;
    private final SchoolClassRepository classes;
    private final ClassJoinCodeRepository codes;
    private final SchoolChildAccessRepository grants;
    private final ClassEnrollmentRepository classEnrollments;
    private final ClassCurriculumAssignmentRepository classCurricula;
    private final CurriculumRepository curricula;
    private final CurriculumService curriculumService;
    private final EnrollmentService enrollmentService;
    private final AuditService audit;
    private final LearningPathItemRepository pathItems;
    private final SecureRandom random = new SecureRandom();
    private final com.platform.subscription.service.SchoolEntitlementService entitlements;

    /** Code lookups per parent account: enough for typos, far too few to guess a code (31^8). */
    private final TokenBucketLimiter lookups = new TokenBucketLimiter(10, Duration.ofHours(1), System::nanoTime);

    public SchoolJoinService(SchoolAccessGuard schoolGuard, FamilyAccessGuard familyGuard, AccountRepository accounts,
                             ChildRepository children, SchoolRepository schools, SchoolClassRepository classes,
                             ClassJoinCodeRepository codes, SchoolChildAccessRepository grants,
                             ClassEnrollmentRepository classEnrollments, ClassCurriculumAssignmentRepository classCurricula,
                             CurriculumRepository curricula, CurriculumService curriculumService,
                             EnrollmentService enrollmentService, AuditService audit, LearningPathItemRepository pathItems,
                             com.platform.subscription.service.SchoolEntitlementService entitlements) {
        this.entitlements = entitlements;
        this.schoolGuard = schoolGuard;
        this.familyGuard = familyGuard;
        this.accounts = accounts;
        this.children = children;
        this.schools = schools;
        this.classes = classes;
        this.codes = codes;
        this.grants = grants;
        this.classEnrollments = classEnrollments;
        this.classCurricula = classCurricula;
        this.curricula = curricula;
        this.curriculumService = curriculumService;
        this.enrollmentService = enrollmentService;
        this.audit = audit;
        this.pathItems = pathItems;
    }

    // ------------------------------------------------------------------ the class's side

    /** The class's code that hasn't been revoked, if any; it may have expired. */
    @Transactional(readOnly = true)
    public Optional<ClassJoinCode> currentCode(UUID accountId, UUID classId) {
        schoolGuard.requireClassStaff(accountId, classId);
        return codes.findBySchoolClassIdAndRevokedAtIsNull(classId);
    }

    /** A new code for the class, valid for 14 days; the previous one stops working. */
    @Transactional
    public ClassJoinCode issueCode(UUID accountId, UUID classId) {
        SchoolClass schoolClass = schoolGuard.requireClassStaff(accountId, classId);
        if (schoolClass.getStatus() != CatalogStatus.ACTIVE) {
            throw new IllegalStateException("This class is archived: families can't join it");
        }
        Instant now = Instant.now();
        codes.findBySchoolClassIdAndRevokedAtIsNull(classId).ifPresent(open -> {
            open.revoke(now);
            codes.saveAndFlush(open);
        });
        ClassJoinCode issued = codes.saveAndFlush(ClassJoinCode.issue(classId, freshCode(), accountId, now.plus(CODE_LIFETIME)));
        record(accountId, "CLASS_JOIN_CODE_ISSUED", "SCHOOL_CLASS", classId, Map.of("expiresAt", issued.getExpiresAt().toString()));
        return issued;
    }

    @Transactional
    public void revokeCode(UUID accountId, UUID classId) {
        schoolGuard.requireClassStaff(accountId, classId);
        codes.findBySchoolClassIdAndRevokedAtIsNull(classId).ifPresent(open -> {
            open.revoke(Instant.now());
            record(accountId, "CLASS_JOIN_CODE_REVOKED", "SCHOOL_CLASS", classId, Map.of());
        });
    }

    /**
     * The class lets a child go (a family joined the wrong class, or the child moved on). The
     * family's grant to the school stays theirs to end; the child's learning is untouched.
     */
    @Transactional
    public void removeFromClass(UUID accountId, UUID classId, UUID childId) {
        schoolGuard.requireClassStaff(accountId, classId);
        ClassEnrollment member = classEnrollments.findMembers(classId).stream()
                .filter(enrollment -> enrollment.getChildId().equals(childId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Class member", childId));
        member.remove();
        int withdrawn = withdrawClassPractice(List.of(classId), childId);
        record(accountId, "CLASS_MEMBER_REMOVED", "CLASS_ENROLLMENT", member.getId(),
                Map.of("childId", childId, "classId", classId, "practiceWithdrawn", withdrawn));
    }

    // ------------------------------------------------------------------ the family's side

    /** What a parent sees before joining: which school and class, and what the school will see. */
    public record JoinPreview(UUID schoolId, String schoolName, UUID classId, String className,
                              List<String> assignedCurricula, boolean alreadyInClass, boolean schoolHasAccess) {
    }

    @Transactional(readOnly = true)
    public JoinPreview preview(UUID accountId, UUID childId, String code) {
        familyGuard.requireChildAccess(accountId, childId);
        spendLookup(accountId);
        SchoolClass schoolClass = classFor(code);
        School school = schools.findById(schoolClass.getSchoolId()).orElseThrow();
        boolean hasAccess = grants.findBySchoolIdAndChildIdAndStatus(school.getId(), childId, SchoolChildAccessStatus.ACTIVE).isPresent();
        boolean inClass = hasAccess && classEnrollments.findMembers(schoolClass.getId()).stream()
                .anyMatch(enrollment -> enrollment.getChildId().equals(childId));
        return new JoinPreview(school.getId(), school.getName(), schoolClass.getId(), schoolClass.getName(),
                assignedCurriculumNames(schoolClass.getId()), inClass, hasAccess);
    }

    /**
     * The family joins: the school gets access to the child's school-assigned learning (a new grant,
     * or the one it already has), the child joins the class, and the class's curricula start. One
     * transaction, serialized per child. Needs a confirmed email address (PD-22): granting another
     * party access to a child is the first action that reaches beyond the family.
     */
    @Transactional
    public JoinPreview join(UUID accountId, UUID childId, String code) {
        familyGuard.requireChildAccess(accountId, childId);
        Account parent = accounts.findById(accountId).orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        if (!parent.isEmailVerified()) {
            throw new IllegalStateException("Confirm your email address before giving a school access to your child's learning");
        }
        spendLookup(accountId);
        SchoolClass schoolClass = classFor(code);
        Child child = children.findByIdForUpdate(childId).orElseThrow(() -> new ResourceNotFoundException("Child", childId));
        Instant now = Instant.now();

        // The school's plan (V59): a child it doesn't have yet takes one of its places.
        entitlements.requireRoomForChild(schoolClass.getSchoolId(), child.getId());
        SchoolChildAccess grant = grants.findBySchoolIdAndChildIdAndStatus(schoolClass.getSchoolId(), child.getId(),
                SchoolChildAccessStatus.ACTIVE).orElse(null);
        boolean newGrant = grant == null;
        if (newGrant) {
            grant = grants.saveAndFlush(SchoolChildAccess.grant(schoolClass.getSchoolId(), child.getId(), accountId, now));
            record(accountId, "SCHOOL_ACCESS_GRANTED", "SCHOOL_CHILD_ACCESS", grant.getId(),
                    Map.of("schoolId", schoolClass.getSchoolId(), "childId", child.getId()));
        }
        Optional<ClassEnrollment> existing = classEnrollments.findBySchoolClassIdAndChildId(schoolClass.getId(), child.getId());
        // A membership starts when the row is new, was ended, or is only now backed by a grant (a
        // pre-PD-01 row, TD-14). Joining a class the child is already in changes and records nothing.
        boolean joins = newGrant || existing.isEmpty() || existing.get().getStatus() != ClassEnrollmentStatus.ACTIVE;
        ClassEnrollment enrollment = existing.orElseGet(() -> ClassEnrollment.enroll(schoolClass.getId(), child.getId()));
        if (joins) {
            enrollment.reactivate();
            record(accountId, "CLASS_JOINED", "CLASS_ENROLLMENT", classEnrollments.saveAndFlush(enrollment).getId(),
                    Map.of("classId", schoolClass.getId(), "childId", child.getId()));
        }

        // The class's curricula start now. One that lost its published version since it was assigned
        // is skipped rather than failing the join; the class page shows it as not started.
        for (ClassCurriculumAssignment assignment : classCurricula.findBySchoolClassIdAndStatusOrderByCreatedAtAsc(
                schoolClass.getId(), ClassCurriculumAssignmentStatus.ACTIVE)) {
            if (curriculumService.hasPublishedVersion(assignment.getCurriculumId())) {
                enrollmentService.enroll(child.getId(), assignment.getCurriculumId());
            }
        }
        School school = schools.findById(schoolClass.getSchoolId()).orElseThrow();
        return new JoinPreview(school.getId(), school.getName(), schoolClass.getId(), schoolClass.getName(),
                assignedCurriculumNames(schoolClass.getId()), true, true);
    }

    /**
     * Every school the family has given access to the child, including access it has since ended -
     * the family's own decisions, for its data export. {@code classNames} only while access lasts.
     */
    public record SchoolAccessRecord(String schoolName, String status, Instant grantedAt, Instant endedAt,
                                     List<String> classNames) {
    }

    @Transactional(readOnly = true)
    public List<SchoolAccessRecord> accessHistoryOf(UUID accountId, UUID childId) {
        Map<UUID, List<String>> current = new HashMap<>();
        accessOf(accountId, childId).forEach(view -> current.put(view.schoolId(), view.classNames()));
        return grants.findByChildIdOrderByGrantedAtAsc(childId).stream().map(grant -> {
            boolean active = grant.getStatus() == SchoolChildAccessStatus.ACTIVE;
            return new SchoolAccessRecord(schools.findById(grant.getSchoolId()).map(School::getName).orElse(null),
                    grant.getStatus().name(), grant.getGrantedAt(), grant.getRevokedAt(),
                    active ? current.getOrDefault(grant.getSchoolId(), List.of()) : List.of());
        }).toList();
    }

    /** A school with access to the child, and the classes the child is in there. */
    public record SchoolAccessView(UUID schoolId, String schoolName, Instant grantedAt, List<String> classNames) {
    }

    @Transactional(readOnly = true)
    public List<SchoolAccessView> accessOf(UUID accountId, UUID childId) {
        familyGuard.requireChildAccess(accountId, childId);
        List<ClassEnrollment> memberships = classEnrollments.findMembershipsOf(childId);
        List<SchoolAccessView> views = new ArrayList<>();
        for (SchoolChildAccess grant : grants.findByChildIdAndStatusOrderByGrantedAtAsc(childId, SchoolChildAccessStatus.ACTIVE)) {
            School school = schools.findById(grant.getSchoolId()).orElseThrow();
            List<String> classNames = memberships.stream()
                    .map(membership -> classes.findById(membership.getSchoolClassId()).orElseThrow())
                    .filter(schoolClass -> schoolClass.getSchoolId().equals(school.getId()))
                    .map(SchoolClass::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
            views.add(new SchoolAccessView(school.getId(), school.getName(), grant.getGrantedAt(), classNames));
        }
        return views;
    }

    /**
     * The family ends a school's access: immediately, and the child leaves every class there. The
     * child keeps every journey and all their evidence; nothing is deleted.
     */
    @Transactional
    public void revoke(UUID accountId, UUID childId, UUID schoolId) {
        familyGuard.requireChildAccess(accountId, childId);
        children.findByIdForUpdate(childId).orElseThrow(() -> new ResourceNotFoundException("Child", childId));
        SchoolChildAccess grant = grants.findBySchoolIdAndChildIdAndStatus(schoolId, childId, SchoolChildAccessStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("School access", schoolId));
        grant.revoke(accountId, Instant.now());
        classEnrollments.findActiveInSchool(childId, schoolId).forEach(ClassEnrollment::remove);
        int withdrawn = withdrawClassPractice(classes.findBySchoolId(schoolId).stream().map(SchoolClass::getId).toList(), childId);
        record(accountId, "SCHOOL_ACCESS_REVOKED", "SCHOOL_CHILD_ACCESS", grant.getId(),
                Map.of("schoolId", schoolId, "childId", childId, "practiceWithdrawn", withdrawn));
    }

    /**
     * Ends every school's access to a child the family is deleting (Y3), exactly as if the
     * family ended each one. The caller has checked the family's access. Returns how many ended.
     */
    @Transactional
    public int revokeEverySchool(UUID accountId, UUID childId) {
        Instant now = Instant.now();
        List<SchoolChildAccess> active = grants.findByChildIdAndStatusOrderByGrantedAtAsc(childId, SchoolChildAccessStatus.ACTIVE);
        for (SchoolChildAccess grant : active) {
            UUID schoolId = grant.getSchoolId();
            grant.revoke(accountId, now);
            classEnrollments.findActiveInSchool(childId, schoolId).forEach(ClassEnrollment::remove);
            int withdrawn = withdrawClassPractice(classes.findBySchoolId(schoolId).stream().map(SchoolClass::getId).toList(), childId);
            record(accountId, "SCHOOL_ACCESS_REVOKED", "SCHOOL_CHILD_ACCESS", grant.getId(),
                    Map.of("schoolId", schoolId, "childId", childId, "practiceWithdrawn", withdrawn, "reason", "CHILD_DELETION"));
        }
        return active.size();
    }

    // ------------------------------------------------------------------ helpers

    /**
     * When a class can no longer work with a child, the practice its teachers placed and the child
     * hasn't started leaves the path (SKIPPED: kept as history, never served). Started practice is
     * left to finish; finished practice and its evidence stay (X10).
     */
    private int withdrawClassPractice(List<UUID> classIds, UUID childId) {
        if (classIds.isEmpty()) {
            return 0;
        }
        List<LearningPathItem> waiting = pathItems.findWaitingClassPractice(classIds, childId);
        waiting.forEach(LearningPathItem::skip);
        return waiting.size();
    }

    /** The open class a code names. Unknown, expired, revoked and archived all read the same. */
    private SchoolClass classFor(String rawCode) {
        String code = normalize(rawCode);
        if (code == null) {
            throw noSuchCode();
        }
        Instant now = Instant.now();
        ClassJoinCode joinCode = codes.findByCode(code).filter(found -> found.isOpenAt(now))
                .orElseThrow(() -> noSuchCode());
        SchoolClass schoolClass = classes.findById(joinCode.getSchoolClassId())
                .filter(found -> found.getStatus() == CatalogStatus.ACTIVE)
                .orElseThrow(() -> noSuchCode());
        schools.findById(schoolClass.getSchoolId()).filter(school -> school.getStatus() == CatalogStatus.ACTIVE)
                .orElseThrow(() -> noSuchCode());
        return schoolClass;
    }

    /** One answer for unknown, expired, revoked and archived (the 404 body is the same for all). */
    private static ResourceNotFoundException noSuchCode() {
        return new ResourceNotFoundException("Class", "for that code");
    }

    /** Upper case without spaces or dashes ("abcd-efgh" works); null unless it could be a code. */
    static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String code = raw.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (code.length() != CODE_LENGTH) {
            return null;
        }
        for (char c : code.toCharArray()) {
            if (CODE_ALPHABET.indexOf(c) < 0) {
                return null;
            }
        }
        return code;
    }

    private String freshCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder code = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
            }
            if (codes.findByCode(code.toString()).isEmpty()) {
                return code.toString();
            }
        }
        throw new IllegalStateException("Couldn't make a new code; please try again");
    }

    private void spendLookup(UUID accountId) {
        Duration wait = lookups.tryAcquire(accountId.toString());
        if (!wait.isZero()) {
            throw new TooManyAttemptsException("Too many class codes tried. Please wait a little and try again.", wait);
        }
    }

    private List<String> assignedCurriculumNames(UUID classId) {
        return classCurricula.findBySchoolClassIdAndStatusOrderByCreatedAtAsc(classId, ClassCurriculumAssignmentStatus.ACTIVE)
                .stream().map(assignment -> curricula.findById(assignment.getCurriculumId()).orElseThrow().getName()).toList();
    }

    private void record(UUID actorId, String action, String resourceType, UUID resourceId, Map<String, Object> metadata) {
        Account actor = accounts.findById(actorId).orElseThrow();
        audit.record(new RecordAuditEvent(null, actorId, AuditActorType.valueOf(actor.getPlatformRole().name()),
                action, resourceType, resourceId, metadata));
    }
}
