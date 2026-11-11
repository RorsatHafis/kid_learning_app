package com.platform.school.attendance;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.school.entity.SchoolClass;
import com.platform.school.service.SchoolAccessGuard;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * M12, first module (owner decisions 2026-11-11): a class's students - the school's own records, separate from any
 * family's child (no link without the parent's consent; no family learning history merged in) - and their per-day
 * attendance. Only the school's principal and the class's assigned teachers, while the school is paid
 * ({@link SchoolAccessGuard#requireClassStaff}); never a platform administrator, a parent, or another school.
 *
 * <ul>
 *   <li>Students: added to a class (several at once, minimal fields: a name and the school's own number); leaving a
 *       class ends the enrolment (history kept, never deleted).</li>
 *   <li>Attendance: one mark per student per day - present, absent, late or excused - for a day up to today in the
 *       school's time zone. Changing a recorded mark needs a reason; the old and new values, who and why are kept
 *       (append-only) and audited. An archived class is read-only.</li>
 * </ul>
 */
@Service
public class AttendanceService {

    public static final Set<String> STATUSES = Set.of("PRESENT", "ABSENT", "LATE", "EXCUSED");
    static final int MAX_ADD = 200;

    private final SchoolAccessGuard guard;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final ZoneId zone;

    public AttendanceService(SchoolAccessGuard guard, JdbcTemplate jdbc, AuditService audit,
                             @Value("${platform.learning.day-zone:Asia/Phnom_Penh}") String zone) {
        this.guard = guard;
        this.jdbc = jdbc;
        this.audit = audit;
        this.zone = ZoneId.of(zone);
    }

    public record Student(UUID id, String name, String number, LocalDate since) {
    }

    public record NewStudent(String name, String number) {
    }

    /**
     * {@code atSchool}: names held back because a student of that name is already in another of the school's classes -
     * most likely the same student, who is brought over (same record) rather than typed again; added only when the
     * caller confirms they are a different student.
     */
    public record Added(List<Student> added, List<String> alreadyInClass, List<AtSchool> atSchool) {
    }

    public record AtSchool(String name, UUID classId, String className) {
    }

    /** {@code status} null: not marked yet. {@code corrected}: the mark was changed after it was first recorded. */
    public record Row(Student student, String status, String note, Instant recordedAt, boolean corrected) {
    }

    public record Register(UUID classId, LocalDate day, boolean today, boolean editable, List<Row> rows,
                           Map<String, Integer> counts, int unmarked) {
    }

    public record MarkInput(UUID studentId, String status, String note) {
    }

    public record MonthRow(Student student, int present, int absent, int late, int excused, int marked) {
    }

    public record Month(UUID classId, YearMonth month, int daysWithMarks, List<MonthRow> rows) {
    }

    // ------------------------------------------------------------------ students

    @Transactional(readOnly = true)
    public List<Student> students(UUID accountId, UUID classId) {
        guard.requireClassStaff(accountId, classId);
        return studentsOn(classId, today());
    }

    /**
     * Adds students to the class (new school records); a name already in the class is left out and said so. A name
     * already in another of the school's classes is held back unless {@code sameNameIsNew}: a second record for the
     * same student would split their history (records are never deleted).
     */
    @Transactional
    public Added add(UUID accountId, UUID classId, List<NewStudent> students, boolean sameNameIsNew) {
        SchoolClass schoolClass = writable(accountId, classId);
        if (students == null || students.isEmpty()) {
            throw new IllegalArgumentException("Add at least one student's name.");
        }
        if (students.size() > MAX_ADD) {
            throw new IllegalArgumentException("Add at most 200 students at a time.");
        }
        Set<String> present = new java.util.HashSet<>();
        studentsOn(classId, today()).forEach(s -> present.add(s.name().toLowerCase(Locale.ROOT)));
        List<Student> added = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<AtSchool> atSchool = new ArrayList<>();
        LocalDate today = today();
        for (NewStudent student : students) {
            String name = student.name() == null ? "" : student.name().strip().replaceAll("\\s+", " ");
            String number = student.number() == null || student.number().isBlank() ? null : student.number().strip();
            if (name.isEmpty()) continue;
            if (name.length() > 120) throw new IllegalArgumentException("A student's name can be at most 120 characters.");
            if (number != null && number.length() > 40) throw new IllegalArgumentException("A student number can be at most 40 characters.");
            if (!present.add(name.toLowerCase(Locale.ROOT))) {
                skipped.add(name);
                continue;
            }
            if (!sameNameIsNew) {
                List<AtSchool> elsewhere = jdbc.query("""
                        select c.id, c.name from class_students cs
                        join school_students s on s.id = cs.student_id
                        join school_classes c on c.id = cs.school_class_id
                        where s.school_id = ? and lower(s.full_name) = lower(?) and cs.ended_on is null and c.id <> ?
                        order by (c.status = 'ARCHIVED'), lower(c.name), c.id limit 1
                        """, (rs, i) -> new AtSchool(name, rs.getObject(1, UUID.class), rs.getString(2)), schoolClass.getSchoolId(), name, classId);
                if (!elsewhere.isEmpty()) {
                    atSchool.add(elsewhere.getFirst());
                    continue;
                }
            }
            if (number != null && Boolean.TRUE.equals(jdbc.queryForObject(
                    "select exists (select 1 from school_students where school_id = ? and student_number = ?)", Boolean.class,
                    schoolClass.getSchoolId(), number))) {
                throw new IllegalStateException("Another student at the school already has the number " + number + ".");
            }
            UUID id = UUID.randomUUID();
            jdbc.update("insert into school_students (id, school_id, full_name, student_number, created_by) values (?, ?, ?, ?, ?)",
                    id, schoolClass.getSchoolId(), name, number, accountId);
            jdbc.update("insert into class_students (id, school_class_id, student_id, started_on) values (?, ?, ?, ?)",
                    UUID.randomUUID(), classId, id, Date.valueOf(today));
            added.add(new Student(id, name, number, today));
        }
        if (added.isEmpty() && skipped.isEmpty() && atSchool.isEmpty()) {
            throw new IllegalArgumentException("Add at least one student's name.");
        }
        if (!added.isEmpty()) {
            record(accountId, "SCHOOL_STUDENTS_ADDED", "SCHOOL_CLASS", classId, Map.of("count", added.size()));
        }
        return new Added(added, skipped, atSchool);
    }

    /**
     * Brings students from another class of the same school into this one - a new year's class or a transfer - as the
     * same school records, so their number, attendance history and any parent connection go with them (re-adding by
     * name would make a second record). From a class still in use, they leave it today; an archived class is left
     * as it was, its history frozen. Needs the staff of both classes (a principal, or a teacher of both).
     */
    @Transactional
    public Added move(UUID accountId, UUID classId, UUID fromClassId, List<UUID> studentIds) {
        SchoolClass target = writable(accountId, classId);
        SchoolClass source = guard.requireClassStaff(accountId, fromClassId);
        if (!source.getSchoolId().equals(target.getSchoolId())) {
            throw new com.platform.common.web.ResourceNotFoundException("SchoolClass", fromClassId);
        }
        if (fromClassId.equals(classId)) {
            throw new IllegalArgumentException("Choose another class to bring students from.");
        }
        if (studentIds == null || studentIds.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one student.");
        }
        if (studentIds.size() > MAX_ADD) {
            throw new IllegalArgumentException("Add at most 200 students at a time.");
        }
        // One change at a time per class, as the register (two people moving the same students can't both succeed).
        jdbc.queryForList("select id from school_classes where id in (?, ?) order by id for update", UUID.class, classId, fromClassId);
        LocalDate today = today();
        Set<UUID> here = new java.util.HashSet<>();
        studentsOn(classId, today).forEach(st -> here.add(st.id()));
        List<Student> moved = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (UUID studentId : new java.util.LinkedHashSet<>(studentIds)) {
            requireInClass(fromClassId, studentId);
            String name = jdbc.queryForObject("select full_name from school_students where id = ?", String.class, studentId);
            if (here.contains(studentId)) {
                skipped.add(name);
                continue;
            }
            if (!source.isArchived()) {
                jdbc.update("""
                        update class_students set ended_on = greatest(started_on, ?) where school_class_id = ? and student_id = ? and ended_on is null
                        """, Date.valueOf(today), fromClassId, studentId);
            }
            jdbc.update("insert into class_students (id, school_class_id, student_id, started_on) values (?, ?, ?, ?)",
                    UUID.randomUUID(), classId, studentId, Date.valueOf(today));
            moved.add(studentsOn(classId, today).stream().filter(st -> st.id().equals(studentId)).findFirst().orElseThrow());
        }
        if (moved.isEmpty() && skipped.isEmpty()) {
            throw new IllegalArgumentException("Choose at least one student.");
        }
        if (!moved.isEmpty()) {
            record(accountId, "SCHOOL_STUDENTS_MOVED", "SCHOOL_CLASS", classId,
                    Map.of("count", moved.size(), "fromClassId", fromClassId.toString(), "fromArchived", source.isArchived()));
        }
        return new Added(moved, skipped, List.of());
    }

    /** Corrects a student's name or number (the school's own record). */
    @Transactional
    public Student update(UUID accountId, UUID classId, UUID studentId, String name, String number) {
        SchoolClass schoolClass = writable(accountId, classId);
        requireInClass(classId, studentId);
        String cleanName = name == null ? "" : name.strip().replaceAll("\\s+", " ");
        String cleanNumber = number == null || number.isBlank() ? null : number.strip();
        if (cleanName.isEmpty() || cleanName.length() > 120) {
            throw new IllegalArgumentException("Enter the student's name (at most 120 characters).");
        }
        if (cleanNumber != null && Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from school_students where school_id = ? and student_number = ? and id <> ?)", Boolean.class,
                schoolClass.getSchoolId(), cleanNumber, studentId))) {
            throw new IllegalStateException("Another student at the school already has the number " + cleanNumber + ".");
        }
        jdbc.update("update school_students set full_name = ?, student_number = ?, updated_at = now() where id = ?", cleanName, cleanNumber,
                studentId);
        record(accountId, "SCHOOL_STUDENT_UPDATED", "SCHOOL_STUDENT", studentId, Map.of("classId", classId.toString()));
        return studentsOn(classId, today()).stream().filter(s -> s.id().equals(studentId)).findFirst().orElseThrow();
    }

    /** The student leaves the class from today: the enrolment ends; the record and past attendance stay. */
    @Transactional
    public void leave(UUID accountId, UUID classId, UUID studentId) {
        writable(accountId, classId);
        requireInClass(classId, studentId);
        LocalDate today = today();
        jdbc.update("""
                update class_students set ended_on = greatest(started_on, ?) where school_class_id = ? and student_id = ? and ended_on is null
                """, Date.valueOf(today), classId, studentId);
        record(accountId, "SCHOOL_STUDENT_LEFT_CLASS", "SCHOOL_STUDENT", studentId, Map.of("classId", classId.toString()));
    }

    // ------------------------------------------------------------------ attendance

    @Transactional(readOnly = true)
    public Register register(UUID accountId, UUID classId, LocalDate day) {
        SchoolClass schoolClass = guard.requireClassStaff(accountId, classId);
        LocalDate on = requireDay(day);
        Map<UUID, Map<String, Object>> marks = new HashMap<>();
        jdbc.queryForList("""
                select m.student_id, m.status, m.note, m.recorded_at,
                       exists (select 1 from attendance_corrections c where c.mark_id = m.id) as corrected
                from attendance_marks m where m.school_class_id = ? and m.day = ?
                """, classId, Date.valueOf(on)).forEach(row -> marks.put((UUID) row.get("student_id"), row));
        List<Row> rows = studentsOn(classId, on).stream().map(student -> {
            Map<String, Object> mark = marks.get(student.id());
            return mark == null ? new Row(student, null, null, null, false)
                    : new Row(student, (String) mark.get("status"), (String) mark.get("note"),
                    ((Timestamp) mark.get("recorded_at")).toInstant(), Boolean.TRUE.equals(mark.get("corrected")));
        }).toList();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String status : List.of("PRESENT", "ABSENT", "LATE", "EXCUSED")) {
            counts.put(status, (int) rows.stream().filter(r -> status.equals(r.status())).count());
        }
        return new Register(classId, on, on.equals(today()), !schoolClass.isArchived(), rows, counts,
                (int) rows.stream().filter(r -> r.status() == null).count());
    }

    /**
     * Records the day's marks. A new mark is recorded as given; changing one already recorded needs {@code reason},
     * and keeps the old and new values with who and why. Students not in the class that day are refused.
     */
    @Transactional
    public Register record(UUID accountId, UUID classId, LocalDate day, List<MarkInput> marks, String reason) {
        SchoolClass schoolClass = writable(accountId, classId);
        LocalDate on = requireDay(day);
        if (marks == null || marks.isEmpty()) {
            throw new IllegalArgumentException("Mark at least one student.");
        }
        // One register at a time per class: two teachers saving together can't both think they were first.
        jdbc.queryForList("select id from school_classes where id = ? for update", UUID.class, classId);
        Set<UUID> inClass = new java.util.HashSet<>();
        studentsOn(classId, on).forEach(s -> inClass.add(s.id()));
        String why = reason == null ? null : reason.strip();
        Instant now = Instant.now();
        int changed = 0;
        for (MarkInput mark : marks) {
            String status = mark.status() == null ? "" : mark.status().strip().toUpperCase(Locale.ROOT);
            String note = mark.note() == null || mark.note().isBlank() ? null : mark.note().strip();
            if (!STATUSES.contains(status)) {
                throw new IllegalArgumentException("Mark each student present, absent, late or excused.");
            }
            if (note != null && note.length() > 300) {
                throw new IllegalArgumentException("A note can be at most 300 characters.");
            }
            if (!inClass.contains(mark.studentId())) {
                throw new IllegalArgumentException("That student isn't in this class on that day.");
            }
            List<Map<String, Object>> existing = jdbc.queryForList(
                    "select id, school_class_id, status, note from attendance_marks where student_id = ? and day = ? for update", mark.studentId(), Date.valueOf(on));
            if (existing.isEmpty()) {
                jdbc.update("""
                        insert into attendance_marks (id, school_id, school_class_id, student_id, day, status, note, recorded_by, recorded_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, UUID.randomUUID(), schoolClass.getSchoolId(), classId, mark.studentId(), Date.valueOf(on), status, note,
                        accountId, Timestamp.from(now));
                changed++;
                continue;
            }
            Map<String, Object> before = existing.getFirst();
            if (!classId.equals(before.get("school_class_id"))) {
                throw new IllegalStateException("This student's attendance for that day was already taken in another class.");
            }
            if (status.equals(before.get("status")) && java.util.Objects.equals(note, before.get("note"))) {
                continue;
            }
            if (why == null || why.isEmpty()) {
                throw new IllegalArgumentException("Say why the attendance is being changed.");
            }
            if (why.length() > 300) {
                throw new IllegalArgumentException("A reason can be at most 300 characters.");
            }
            UUID markId = (UUID) before.get("id");
            jdbc.update("""
                    insert into attendance_corrections (id, mark_id, old_status, new_status, old_note, new_note, reason, corrected_by)
                    values (?, ?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), markId, before.get("status"), status, before.get("note"), note, why, accountId);
            jdbc.update("update attendance_marks set status = ?, note = ?, recorded_by = ?, recorded_at = ? where id = ?", status, note,
                    accountId, Timestamp.from(now), markId);
            record(accountId, "ATTENDANCE_CORRECTED", "SCHOOL_STUDENT", mark.studentId(),
                    Map.of("day", on.toString(), "from", before.get("status"), "to", status, "reason", why));
            changed++;
        }
        if (changed > 0) {
            record(accountId, "ATTENDANCE_RECORDED", "SCHOOL_CLASS", classId, Map.of("day", on.toString(), "marks", changed));
        }
        return register(accountId, classId, on);
    }

    /** A month's attendance, per student: how many days each mark was given. */
    @Transactional(readOnly = true)
    public Month month(UUID accountId, UUID classId, YearMonth month) {
        guard.requireClassStaff(accountId, classId);
        YearMonth which = month == null ? YearMonth.from(today()) : month;
        LocalDate first = which.atDay(1);
        LocalDate last = which.atEndOfMonth();
        Map<UUID, int[]> tally = new HashMap<>();
        jdbc.queryForList("""
                select student_id, status, count(*) as n from attendance_marks
                where school_class_id = ? and day between ? and ? group by student_id, status
                """, classId, Date.valueOf(first), Date.valueOf(last)).forEach(row -> {
            int[] t = tally.computeIfAbsent((UUID) row.get("student_id"), id -> new int[4]);
            int n = ((Number) row.get("n")).intValue();
            switch ((String) row.get("status")) {
                case "PRESENT" -> t[0] += n;
                case "ABSENT" -> t[1] += n;
                case "LATE" -> t[2] += n;
                default -> t[3] += n;
            }
        });
        Integer days = jdbc.queryForObject("select count(distinct day) from attendance_marks where school_class_id = ? and day between ? and ?",
                Integer.class, classId, Date.valueOf(first), Date.valueOf(last));
        // Everyone in the class at any point that month (a student who left is still in its history).
        List<Student> students = jdbc.query("""
                select distinct s.id, s.full_name, s.student_number, min(cs.started_on) over (partition by s.id) as since
                from class_students cs join school_students s on s.id = cs.student_id
                where cs.school_class_id = ? and cs.started_on <= ? and (cs.ended_on is null or cs.ended_on > ?)
                order by s.full_name
                """, (rs, i) -> new Student(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getDate(4).toLocalDate()),
                classId, Date.valueOf(last), Date.valueOf(first));
        List<MonthRow> rows = students.stream().map(s -> {
            int[] t = tally.getOrDefault(s.id(), new int[4]);
            return new MonthRow(s, t[0], t[1], t[2], t[3], t[0] + t[1] + t[2] + t[3]);
        }).toList();
        return new Month(classId, which, days == null ? 0 : days, rows);
    }

    // ------------------------------------------------------------------ helpers

    private List<Student> studentsOn(UUID classId, LocalDate day) {
        return jdbc.query("""
                select s.id, s.full_name, s.student_number, cs.started_on
                from class_students cs join school_students s on s.id = cs.student_id
                where cs.school_class_id = ? and cs.started_on <= ? and (cs.ended_on is null or cs.ended_on > ?)
                order by lower(s.full_name), s.id
                """, (rs, i) -> new Student(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getDate(4).toLocalDate()),
                classId, Date.valueOf(day), Date.valueOf(day));
    }

    private void requireInClass(UUID classId, UUID studentId) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists (select 1 from class_students where school_class_id = ? and student_id = ? and ended_on is null)",
                Boolean.class, classId, studentId))) {
            throw new com.platform.common.web.ResourceNotFoundException("SchoolStudent", studentId);
        }
    }

    private SchoolClass writable(UUID accountId, UUID classId) {
        SchoolClass schoolClass = guard.requireClassStaff(accountId, classId);
        if (schoolClass.isArchived()) {
            throw new IllegalStateException("This class is archived: its students and attendance can be read, not changed.");
        }
        return schoolClass;
    }

    private LocalDate requireDay(LocalDate day) {
        LocalDate on = day == null ? today() : day;
        if (on.isAfter(today())) {
            throw new IllegalArgumentException("Attendance can't be taken for a day that hasn't come yet.");
        }
        return on;
    }

    private LocalDate today() {
        return LocalDate.now(zone);
    }

    private void record(UUID accountId, String action, String resource, UUID resourceId, Map<String, Object> details) {
        String role = jdbc.queryForList("select platform_role from accounts where id = ?", String.class, accountId).stream().findFirst().orElse("");
        audit.record(new RecordAuditEvent(null, accountId, "PRINCIPAL".equals(role) ? AuditActorType.PRINCIPAL : AuditActorType.TEACHER, action,
                resource, resourceId, details));
    }
}
