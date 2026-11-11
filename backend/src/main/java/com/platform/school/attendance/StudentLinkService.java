package com.platform.school.attendance;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.family.service.FamilyAccessGuard;
import com.platform.school.entity.SchoolClass;
import com.platform.school.service.SchoolAccessGuard;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * M12 (owner decision 2026-11-11): a school student record is linked to a family's child only with the parent or
 * guardian's explicit consent (V96).
 *
 * <ul>
 *   <li>The class's teacher or the principal asks, for a child already in that class whose family shares them with the
 *       school - nobody else can be named.</li>
 *   <li>A parent of the child connects or declines (who and when is the consent record); either side can disconnect.</li>
 *   <li>A link joins two records: nothing is copied or merged, the school sees no more of the child than the family
 *       already shares, and the link ends when the family withdraws the school's access.</li>
 * </ul>
 */
@Service
public class StudentLinkService {

    private final SchoolAccessGuard schoolGuard;
    private final FamilyAccessGuard familyGuard;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public StudentLinkService(SchoolAccessGuard schoolGuard, FamilyAccessGuard familyGuard, JdbcTemplate jdbc, AuditService audit) {
        this.schoolGuard = schoolGuard;
        this.familyGuard = familyGuard;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    /** A class's records and their links; {@code childName} only for a link, and only as the family shares it. */
    public record ClassLink(UUID studentId, String status, UUID childId, String childName) {
    }

    /** A class's learner whose family shares them with the school and who isn't linked or asked about yet. */
    public record Candidate(UUID childId, String childName) {
    }

    public record ClassLinks(List<ClassLink> links, List<Candidate> candidates) {
    }

    /** A request or link as the family sees it. */
    public record FamilyLink(UUID id, String schoolName, String studentName, String status, Instant requestedAt, Instant decidedAt) {
    }

    // ------------------------------------------------------------------ the school's side

    @Transactional(readOnly = true)
    public ClassLinks forClass(UUID accountId, UUID classId) {
        SchoolClass schoolClass = schoolGuard.requireClassStaff(accountId, classId);
        List<ClassLink> links = jdbc.query("""
                select l.student_id, l.status, l.child_id, c.display_name from school_student_links l
                join children c on c.id = l.child_id
                join class_students cs on cs.student_id = l.student_id and cs.school_class_id = ? and cs.ended_on is null
                where l.status in ('PENDING', 'ACTIVE')
                """, (rs, i) -> new ClassLink(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), rs.getString(4)), classId);
        List<Candidate> candidates = jdbc.query("""
                select c.id, c.display_name from class_enrollments e
                join children c on c.id = e.child_id
                join school_child_access g on g.child_id = c.id and g.school_id = ? and g.status = 'ACTIVE'
                where e.school_class_id = ? and e.status = 'ACTIVE'
                  and not exists (select 1 from school_student_links l where l.child_id = c.id and l.school_id = ?
                                  and l.status in ('PENDING', 'ACTIVE'))
                order by lower(c.display_name), c.id
                """, (rs, i) -> new Candidate(rs.getObject(1, UUID.class), rs.getString(2)),
                schoolClass.getSchoolId(), classId, schoolClass.getSchoolId());
        return new ClassLinks(links, candidates);
    }

    /** Asks the child's family to connect this record with their child (a learner in this class shared with the school). */
    @Transactional
    public ClassLinks ask(UUID accountId, UUID classId, UUID studentId, UUID childId) {
        SchoolClass schoolClass = schoolGuard.requireClassStaff(accountId, classId);
        if (schoolClass.isArchived()) {
            throw new IllegalStateException("This class is archived: its students and attendance can be read, not changed.");
        }
        requireRecordInClass(classId, studentId);
        boolean shared = Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (select 1 from class_enrollments e
                    join school_child_access g on g.child_id = e.child_id and g.school_id = ? and g.status = 'ACTIVE'
                    where e.school_class_id = ? and e.child_id = ? and e.status = 'ACTIVE')
                """, Boolean.class, schoolClass.getSchoolId(), classId, childId));
        if (!shared) {
            throw new IllegalArgumentException("Only a child in this class, whose family shares them with the school, can be asked about.");
        }
        try {
            jdbc.update("""
                    insert into school_student_links (id, school_id, student_id, child_id, status, requested_by) values (?, ?, ?, ?, 'PENDING', ?)
                    """, UUID.randomUUID(), schoolClass.getSchoolId(), studentId, childId, accountId);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new IllegalStateException("This student or this child already has a request or a connection.");
        }
        record(accountId, staffActor(accountId), "SCHOOL_STUDENT_LINK_REQUESTED", studentId, Map.of("childId", childId.toString()));
        return forClass(accountId, classId);
    }

    /** The school withdraws its request, or disconnects. */
    @Transactional
    public ClassLinks end(UUID accountId, UUID classId, UUID studentId) {
        schoolGuard.requireClassStaff(accountId, classId);
        requireRecordInClass(classId, studentId);
        int ended = jdbc.update("""
                update school_student_links set status = 'ENDED', ended_by = ?, ended_at = now()
                where student_id = ? and status in ('PENDING', 'ACTIVE')
                """, accountId, studentId);
        if (ended > 0) record(accountId, staffActor(accountId), "SCHOOL_STUDENT_LINK_ENDED", studentId, Map.of("by", "SCHOOL"));
        return forClass(accountId, classId);
    }

    // ------------------------------------------------------------------ the family's side

    @Transactional(readOnly = true)
    public List<FamilyLink> forChild(UUID accountId, UUID childId) {
        familyGuard.requireChildAccess(accountId, childId);
        return jdbc.query("""
                select l.id, s.name, st.full_name, l.status, l.requested_at, l.decided_at from school_student_links l
                join schools s on s.id = l.school_id join school_students st on st.id = l.student_id
                where l.child_id = ? and l.status in ('PENDING', 'ACTIVE') order by l.requested_at
                """, (rs, i) -> new FamilyLink(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getTimestamp(5).toInstant(), rs.getTimestamp(6) == null ? null : rs.getTimestamp(6).toInstant()), childId);
    }

    /** A parent's explicit consent: connect, or decline. */
    @Transactional
    public List<FamilyLink> decide(UUID accountId, UUID childId, UUID linkId, boolean connect) {
        familyGuard.requireChildAccess(accountId, childId);
        int changed = jdbc.update("""
                update school_student_links set status = ?, decided_by = ?, decided_at = ?
                where id = ? and child_id = ? and status = 'PENDING'
                  and exists (select 1 from school_child_access g where g.school_id = school_student_links.school_id
                              and g.child_id = school_student_links.child_id and g.status = 'ACTIVE')
                """, connect ? "ACTIVE" : "DECLINED", accountId, Timestamp.from(Instant.now()), linkId, childId);
        if (changed == 0) {
            throw new ResourceNotFoundException("School link request", linkId);
        }
        record(accountId, AuditActorType.PARENT, connect ? "SCHOOL_STUDENT_LINK_CONSENTED" : "SCHOOL_STUDENT_LINK_DECLINED", linkId,
                Map.of("childId", childId.toString()));
        return forChild(accountId, childId);
    }

    /** The family disconnects (or withdraws from a request it hasn't answered). */
    @Transactional
    public List<FamilyLink> disconnect(UUID accountId, UUID childId, UUID linkId) {
        familyGuard.requireChildAccess(accountId, childId);
        int ended = jdbc.update("""
                update school_student_links set status = 'ENDED', ended_by = ?, ended_at = now()
                where id = ? and child_id = ? and status in ('PENDING', 'ACTIVE')
                """, accountId, linkId, childId);
        if (ended == 0) {
            throw new ResourceNotFoundException("School link", linkId);
        }
        record(accountId, AuditActorType.PARENT, "SCHOOL_STUDENT_LINK_ENDED", linkId, Map.of("by", "FAMILY"));
        return forChild(accountId, childId);
    }

    /** The family withdrew the school's access: every link and request with that school ends with it. */
    @Transactional
    public void endForSchoolAccess(UUID accountId, UUID childId, UUID schoolId) {
        jdbc.update("""
                update school_student_links set status = 'ENDED', ended_by = ?, ended_at = now()
                where child_id = ? and school_id = ? and status in ('PENDING', 'ACTIVE')
                """, accountId, childId, schoolId);
    }

    private void requireRecordInClass(UUID classId, UUID studentId) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from class_students where school_class_id = ? and student_id = ? and ended_on is null)",
                Boolean.class, classId, studentId))) {
            throw new ResourceNotFoundException("SchoolStudent", studentId);
        }
    }

    private AuditActorType staffActor(UUID accountId) {
        String role = jdbc.queryForList("select platform_role from accounts where id = ?", String.class, accountId).stream().findFirst().orElse("");
        return "PRINCIPAL".equals(role) ? AuditActorType.PRINCIPAL : AuditActorType.TEACHER;
    }

    private void record(UUID accountId, AuditActorType actor, String action, UUID resourceId, Map<String, Object> details) {
        audit.record(new RecordAuditEvent(null, accountId, actor, action, "SCHOOL_STUDENT_LINK", resourceId, details));
    }
}
