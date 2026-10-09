package com.platform.school.service;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.identity.entity.Account;
import com.platform.identity.repository.AccountRepository;
import com.platform.school.entity.School;
import com.platform.school.entity.SchoolClass;
import com.platform.school.repository.ClassJoinCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Renaming a school or a class, and archiving or restoring a class (X17) - the principal's.
 * Archive follows PD-25: <b>no new uses</b>. An archived class takes no new children (its open code
 * is revoked and no new one can be issued), no new curricula, teachers or practice; its children keep
 * everything they have - their journeys, practice, evidence and the family's grant to the school - and
 * its staff can still read it. Restoring lets it be used again (with a fresh code).
 */
@Service
public class SchoolLifecycleService {

    private static final int MAX_NAME = 120;

    private final SchoolAccessGuard guard;
    private final ClassJoinCodeRepository codes;
    private final AccountRepository accounts;
    private final AuditService audit;
    private final com.platform.school.repository.SchoolClassRepository classes;
    private final com.platform.school.repository.SchoolRepository schools;

    public SchoolLifecycleService(SchoolAccessGuard guard, ClassJoinCodeRepository codes, AccountRepository accounts, AuditService audit,
                                  com.platform.school.repository.SchoolClassRepository classes,
                                  com.platform.school.repository.SchoolRepository schools) {
        this.classes = classes;
        this.schools = schools;
        this.guard = guard;
        this.codes = codes;
        this.accounts = accounts;
        this.audit = audit;
    }

    @Transactional
    public School renameSchool(UUID accountId, UUID schoolId, String name) {
        School school = guard.requireSchoolManage(accountId, schoolId);
        String from = school.getName();
        school.rename(clean(name));
        record(accountId, "SCHOOL_UPDATED", "SCHOOL", schoolId, Map.of("from", from, "to", school.getName()));
        return school;
    }

    @Transactional
    public SchoolClass renameClass(UUID accountId, UUID classId, String name) {
        SchoolClass schoolClass = requireManagedClass(accountId, classId);
        String from = schoolClass.getName();
        schoolClass.rename(clean(name));
        record(accountId, "CLASS_UPDATED", "SCHOOL_CLASS", classId, Map.of("from", from, "to", schoolClass.getName()));
        return schoolClass;
    }

    @Transactional
    public SchoolClass archiveClass(UUID accountId, UUID classId) {
        SchoolClass schoolClass = requireManagedClass(accountId, classId);
        if (schoolClass.isArchived()) {
            throw new IllegalStateException("This class is already archived.");
        }
        schoolClass.archive();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("joinCodeRevoked", codes.findBySchoolClassIdAndRevokedAtIsNull(classId).map(open -> {
            open.revoke(Instant.now());
            return true;
        }).orElse(false));
        record(accountId, "CLASS_ARCHIVED", "SCHOOL_CLASS", classId, metadata);
        return schoolClass;
    }

    /**
     * PD-26 as adopted (Y17 - please confirm): a school that stops using Bookeyond Kid is archived
     * like its classes - nothing new (no classes, codes, joining, staff invitations, curricula or
     * practice: every class is archived and its code revoked), and nothing existing changes:
     * families' grants stay (each family can still end access) and children keep everything.
     * Restoring the school lets it start again; its classes are restored one by one.
     */
    @Transactional
    public School archiveSchool(UUID accountId, UUID schoolId) {
        School school = guard.requireSchoolManage(accountId, schoolId);
        school.archive();
        int archived = 0;
        int codesRevoked = 0;
        for (SchoolClass schoolClass : classes.findBySchoolId(schoolId)) {
            if (schoolClass.isArchived()) {
                continue;
            }
            schoolClass.archive();
            archived++;
            codesRevoked += codes.findBySchoolClassIdAndRevokedAtIsNull(schoolClass.getId()).map(open -> {
                open.revoke(Instant.now());
                return 1;
            }).orElse(0);
        }
        record(accountId, "SCHOOL_ARCHIVED", "SCHOOL", schoolId, Map.of("classesArchived", archived, "joinCodesRevoked", codesRevoked));
        return school;
    }

    @Transactional
    public School restoreSchool(UUID accountId, UUID schoolId) {
        School school = guard.requireSchoolManage(accountId, schoolId);
        school.restore();
        record(accountId, "SCHOOL_RESTORED", "SCHOOL", schoolId, Map.of());
        return school;
    }

    @Transactional
    public SchoolClass restoreClass(UUID accountId, UUID classId) {
        SchoolClass schoolClass = requireManagedClass(accountId, classId);
        if (!schoolClass.isArchived()) {
            throw new IllegalStateException("This class is not archived.");
        }
        if (schools.findById(schoolClass.getSchoolId()).map(School::isArchived).orElse(false)) {
            throw new IllegalStateException("Restore the school first.");
        }
        schoolClass.restore();
        record(accountId, "CLASS_RESTORED", "SCHOOL_CLASS", classId, Map.of());
        return schoolClass;
    }

    /** Only the school's principal shapes its classes (a teacher works in them). */
    private SchoolClass requireManagedClass(UUID accountId, UUID classId) {
        SchoolClass schoolClass = guard.requireClassAccess(accountId, classId);
        guard.requireSchoolManage(accountId, schoolClass.getSchoolId());
        return schoolClass;
    }

    private static String clean(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME) {
            throw new IllegalArgumentException("A name needs 1 to " + MAX_NAME + " characters");
        }
        return trimmed;
    }

    private void record(UUID actorId, String action, String resourceType, UUID resourceId, Map<String, Object> metadata) {
        Account actor = accounts.findById(actorId).orElseThrow();
        audit.record(new RecordAuditEvent(null, actorId, AuditActorType.valueOf(actor.getPlatformRole().name()), action, resourceType,
                resourceId, metadata));
    }

}
