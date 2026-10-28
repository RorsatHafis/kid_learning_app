package com.platform.school.service;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.identity.entity.Account;
import com.platform.identity.repository.AccountRepository;
import com.platform.school.entity.AcademicTerm;
import com.platform.school.entity.AcademicYear;
import com.platform.school.entity.School;
import com.platform.school.entity.SchoolClass;
import com.platform.school.repository.AcademicTermRepository;
import com.platform.school.repository.AcademicYearRepository;
import com.platform.school.repository.SchoolClassRepository;
import com.platform.school.repository.SchoolRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M12 P1: a school's academic years and terms, and which year a class belongs to. The school sets its own dates;
 * the rules are only those every calendar shares: a year or term ends after it starts, a school's years don't overlap,
 * a year's terms don't overlap and lie inside it, at most one year is current. Every change takes the school's year
 * lock first (so two staff can't race an overlap in) and is audited. The caller has checked the school is the
 * actor's to manage (SchoolAccessGuard#requireSchoolManage). A refusal that comes from the school's existing calendar
 * (overlap, duplicate name, term outside its year) is a conflict (409); a malformed input is a 400.
 */
@Service
public class AcademicYearService {

    static final int MAX_NAME = 80;
    /** A sanity bound, not a policy: no real school year runs longer. */
    static final long MAX_YEAR_DAYS = 731;

    private final AcademicYearRepository years;
    private final AcademicTermRepository terms;
    private final SchoolRepository schools;
    private final SchoolClassRepository classes;
    private final AccountRepository accounts;
    private final AuditService audit;
    private final SchoolService schoolService;

    public AcademicYearService(AcademicYearRepository years, AcademicTermRepository terms, SchoolRepository schools,
                               SchoolClassRepository classes, AccountRepository accounts, AuditService audit,
                               SchoolService schoolService) {
        this.schoolService = schoolService;
        this.years = years;
        this.terms = terms;
        this.schools = schools;
        this.classes = classes;
        this.accounts = accounts;
        this.audit = audit;
    }

    public record YearWithTerms(AcademicYear year, List<AcademicTerm> terms) {}

    @Transactional(readOnly = true)
    public List<YearWithTerms> list(UUID schoolId) {
        List<AcademicYear> all = years.findBySchoolIdOrderByStartsOnDesc(schoolId);
        Map<UUID, List<AcademicTerm>> byYear = new LinkedHashMap<>();
        all.forEach(y -> byYear.put(y.getId(), new java.util.ArrayList<>()));
        if (!all.isEmpty()) {
            terms.findByAcademicYearIdInOrderByStartsOn(byYear.keySet()).forEach(t -> byYear.get(t.getAcademicYearId()).add(t));
        }
        return all.stream().map(y -> new YearWithTerms(y, byYear.get(y.getId()))).toList();
    }

    /** A new year; the school's first active year becomes its current one. */
    @Transactional
    public AcademicYear create(UUID schoolId, String name, LocalDate startsOn, LocalDate endsOn, UUID actorId) {
        School school = activeSchool(schoolId);
        String clean = cleanName(name);
        requireYearDates(startsOn, endsOn);
        List<AcademicYear> existing = years.lockBySchoolId(school.getId());
        requireNoOverlap(existing, null, clean, startsOn, endsOn);
        AcademicYear year = AcademicYear.create(schoolId, clean, startsOn, endsOn);
        if (existing.stream().noneMatch(AcademicYear::isCurrent)) {
            year.makeCurrent();
        }
        year = years.save(year);
        record(actorId, "ACADEMIC_YEAR_CREATED", year.getId(), Map.of("schoolId", schoolId, "name", clean,
                "startsOn", startsOn.toString(), "endsOn", endsOn.toString(), "current", year.isCurrent()));
        return year;
    }

    @Transactional
    public AcademicYear change(UUID schoolId, UUID yearId, String name, LocalDate startsOn, LocalDate endsOn, UUID actorId) {
        activeSchool(schoolId);
        String clean = cleanName(name);
        requireYearDates(startsOn, endsOn);
        List<AcademicYear> existing = years.lockBySchoolId(schoolId);
        AcademicYear year = activeYearOf(existing, yearId);
        requireNoOverlap(existing, yearId, clean, startsOn, endsOn);
        for (AcademicTerm term : terms.findByAcademicYearIdOrderByStartsOn(yearId)) {
            if (term.getStartsOn().isBefore(startsOn) || term.getEndsOn().isAfter(endsOn)) {
                throw new IllegalStateException("The year's dates must still contain all of its terms.");
            }
        }
        Map<String, Object> before = Map.of("name", year.getName(), "startsOn", year.getStartsOn().toString(),
                "endsOn", year.getEndsOn().toString());
        year.change(clean, startsOn, endsOn);
        record(actorId, "ACADEMIC_YEAR_CHANGED", yearId, Map.of("schoolId", schoolId, "before", before, "after",
                Map.of("name", clean, "startsOn", startsOn.toString(), "endsOn", endsOn.toString())));
        return year;
    }

    @Transactional
    public AcademicYear makeCurrent(UUID schoolId, UUID yearId, UUID actorId) {
        activeSchool(schoolId);
        List<AcademicYear> existing = years.lockBySchoolId(schoolId);
        AcademicYear year = activeYearOf(existing, yearId);
        if (year.isCurrent()) {
            return year;
        }
        UUID previous = null;
        for (AcademicYear other : existing) {
            if (other.isCurrent()) {
                previous = other.getId();
                other.clearCurrent();
            }
        }
        years.flush(); // the partial unique index allows one current year: clear the old before setting the new
        year.makeCurrent();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("schoolId", schoolId);
        metadata.put("previousYearId", previous);
        record(actorId, "ACADEMIC_YEAR_MADE_CURRENT", yearId, metadata);
        return year;
    }

    @Transactional
    public AcademicYear archive(UUID schoolId, UUID yearId, UUID actorId) {
        activeSchool(schoolId);
        AcademicYear year = activeYearOf(years.lockBySchoolId(schoolId), yearId);
        if (year.isCurrent()) {
            throw new IllegalStateException("The current academic year can't be archived: make another year current first.");
        }
        year.archive();
        record(actorId, "ACADEMIC_YEAR_ARCHIVED", yearId, Map.of("schoolId", schoolId));
        return year;
    }

    @Transactional
    public AcademicYear restore(UUID schoolId, UUID yearId, UUID actorId) {
        activeSchool(schoolId);
        AcademicYear year = yearOf(years.lockBySchoolId(schoolId), yearId);
        if (year.isArchived()) {
            year.restore();
            record(actorId, "ACADEMIC_YEAR_RESTORED", yearId, Map.of("schoolId", schoolId));
        }
        return year;
    }

    @Transactional
    public AcademicTerm addTerm(UUID schoolId, UUID yearId, String name, LocalDate startsOn, LocalDate endsOn, UUID actorId) {
        activeSchool(schoolId);
        String clean = cleanName(name);
        requireDates(startsOn, endsOn);
        AcademicYear year = activeYearOf(years.lockBySchoolId(schoolId), yearId);
        requireTermFits(year, terms.findByAcademicYearIdOrderByStartsOn(yearId), null, clean, startsOn, endsOn);
        AcademicTerm term = terms.save(AcademicTerm.create(yearId, clean, startsOn, endsOn));
        record(actorId, "ACADEMIC_TERM_CREATED", term.getId(), Map.of("schoolId", schoolId, "academicYearId", yearId,
                "name", clean, "startsOn", startsOn.toString(), "endsOn", endsOn.toString()));
        return term;
    }

    @Transactional
    public AcademicTerm changeTerm(UUID schoolId, UUID yearId, UUID termId, String name, LocalDate startsOn, LocalDate endsOn,
                                   UUID actorId) {
        activeSchool(schoolId);
        String clean = cleanName(name);
        requireDates(startsOn, endsOn);
        AcademicYear year = activeYearOf(years.lockBySchoolId(schoolId), yearId);
        List<AcademicTerm> siblings = terms.findByAcademicYearIdOrderByStartsOn(yearId);
        AcademicTerm term = siblings.stream().filter(t -> t.getId().equals(termId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Academic term", termId));
        requireTermFits(year, siblings, termId, clean, startsOn, endsOn);
        term.change(clean, startsOn, endsOn);
        record(actorId, "ACADEMIC_TERM_CHANGED", termId, Map.of("schoolId", schoolId, "academicYearId", yearId,
                "name", clean, "startsOn", startsOn.toString(), "endsOn", endsOn.toString()));
        return term;
    }

    /** Nothing refers to a term yet (attendance and results will): until then a mistaken term can be removed. */
    @Transactional
    public void removeTerm(UUID schoolId, UUID yearId, UUID termId, UUID actorId) {
        activeSchool(schoolId);
        activeYearOf(years.lockBySchoolId(schoolId), yearId);
        AcademicTerm term = terms.findById(termId).filter(t -> t.getAcademicYearId().equals(yearId))
                .orElseThrow(() -> new ResourceNotFoundException("Academic term", termId));
        terms.delete(term);
        record(actorId, "ACADEMIC_TERM_REMOVED", termId, Map.of("schoolId", schoolId, "academicYearId", yearId,
                "name", term.getName()));
    }

    /** Puts a class in one of its school's active years, or takes it out of any year (null). */
    @Transactional
    public SchoolClass placeClass(UUID schoolId, UUID classId, UUID yearId, UUID actorId) {
        activeSchool(schoolId);
        SchoolClass schoolClass = classes.findById(classId).filter(c -> c.getSchoolId().equals(schoolId))
                .orElseThrow(() -> new ResourceNotFoundException("Class", classId));
        if (yearId != null) {
            activeYearOf(years.lockBySchoolId(schoolId), yearId);
        }
        UUID before = schoolClass.getAcademicYearId();
        schoolClass.placeInYear(yearId);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("schoolId", schoolId);
        metadata.put("fromYearId", before);
        metadata.put("toYearId", yearId);
        record(actorId, "CLASS_ACADEMIC_YEAR_SET", classId, metadata);
        return schoolClass;
    }

    public record CopiedClasses(List<SchoolClass> created, List<String> alreadyThere) {}

    /**
     * Year-end rollover: the active classes of {@code fromYearId}, recreated by name in {@code toYearId} (an active
     * year). A name the target year already has is skipped, so running it again adds nothing twice. Only the class
     * structure moves: no children (a family's consent, S1), no teachers, codes or curricula (the principal's choices
     * for the new year). Each class is made as any other (plan limit, archived school), all or nothing.
     */
    @Transactional
    public CopiedClasses copyClasses(UUID schoolId, UUID fromYearId, UUID toYearId, UUID actorId) {
        activeSchool(schoolId);
        if (fromYearId.equals(toYearId)) {
            throw new IllegalArgumentException("Choose a different year to copy from.");
        }
        List<AcademicYear> existing = years.lockBySchoolId(schoolId);
        yearOf(existing, fromYearId);
        activeYearOf(existing, toYearId);
        List<SchoolClass> all = classes.findBySchoolId(schoolId);
        java.util.Set<String> inTarget = new java.util.HashSet<>();
        all.stream().filter(c -> toYearId.equals(c.getAcademicYearId()) && !c.isArchived())
                .forEach(c -> inTarget.add(c.getName().toLowerCase(java.util.Locale.ROOT)));
        List<SchoolClass> created = new java.util.ArrayList<>();
        List<String> skipped = new java.util.ArrayList<>();
        for (SchoolClass source : all.stream().filter(c -> fromYearId.equals(c.getAcademicYearId()) && !c.isArchived())
                .sorted(java.util.Comparator.comparing(SchoolClass::getName)).toList()) {
            if (!inTarget.add(source.getName().toLowerCase(java.util.Locale.ROOT))) {
                skipped.add(source.getName());
                continue;
            }
            SchoolClass copy = schoolService.createClass(schoolId, source.getName());
            copy.placeInYear(toYearId);
            created.add(copy);
        }
        record(actorId, "ACADEMIC_YEAR_CLASSES_COPIED", toYearId, Map.of("schoolId", schoolId, "fromYearId", fromYearId,
                "created", created.size(), "alreadyThere", skipped.size()));
        return new CopiedClasses(created, skipped);
    }

    private School activeSchool(UUID schoolId) {
        School school = schools.findById(schoolId).orElseThrow(() -> new ResourceNotFoundException("School", schoolId));
        if (school.isArchived()) {
            throw new IllegalStateException("This school is archived: restore it to change its academic years.");
        }
        return school;
    }

    private static AcademicYear yearOf(List<AcademicYear> years, UUID yearId) {
        return years.stream().filter(y -> y.getId().equals(yearId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Academic year", yearId));
    }

    private static AcademicYear activeYearOf(List<AcademicYear> years, UUID yearId) {
        AcademicYear year = yearOf(years, yearId);
        if (year.isArchived()) {
            throw new IllegalStateException("This academic year is archived.");
        }
        return year;
    }

    private static String cleanName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME) {
            throw new IllegalArgumentException("A name needs 1 to 80 characters.");
        }
        return trimmed;
    }

    private static void requireDates(LocalDate startsOn, LocalDate endsOn) {
        if (startsOn == null || endsOn == null || !endsOn.isAfter(startsOn)) {
            throw new IllegalArgumentException("The end date must be after the start date.");
        }
    }

    private static void requireYearDates(LocalDate startsOn, LocalDate endsOn) {
        requireDates(startsOn, endsOn);
        if (ChronoUnit.DAYS.between(startsOn, endsOn) > MAX_YEAR_DAYS) {
            throw new IllegalArgumentException("An academic year can last at most two years.");
        }
    }

    private static void requireNoOverlap(List<AcademicYear> existing, UUID except, String name, LocalDate startsOn,
                                         LocalDate endsOn) {
        for (AcademicYear other : existing) {
            if (other.getId().equals(except)) {
                continue;
            }
            if (other.getName().equalsIgnoreCase(name)) {
                throw new IllegalStateException("This school already has an academic year with that name.");
            }
            if (!other.isArchived() && AcademicYear.overlaps(startsOn, endsOn, other.getStartsOn(), other.getEndsOn())) {
                throw new IllegalStateException("These dates overlap another academic year of this school.");
            }
        }
    }

    private static void requireTermFits(AcademicYear year, List<AcademicTerm> siblings, UUID except, String name,
                                        LocalDate startsOn, LocalDate endsOn) {
        if (startsOn.isBefore(year.getStartsOn()) || endsOn.isAfter(year.getEndsOn())) {
            throw new IllegalStateException("A term must fall within its academic year.");
        }
        for (AcademicTerm other : siblings) {
            if (other.getId().equals(except)) {
                continue;
            }
            if (other.getName().equalsIgnoreCase(name)) {
                throw new IllegalStateException("This year already has a term with that name.");
            }
            if (AcademicYear.overlaps(startsOn, endsOn, other.getStartsOn(), other.getEndsOn())) {
                throw new IllegalStateException("These dates overlap another term of this year.");
            }
        }
    }

    private void record(UUID actorId, String action, UUID resourceId, Map<String, Object> details) {
        // The audit event copies its metadata immutably (no null values): a value that is absent is left out.
        Map<String, Object> metadata = new LinkedHashMap<>();
        details.forEach((key, value) -> {
            if (value != null) {
                metadata.put(key, value);
            }
        });
        Account actor = accounts.findById(actorId).orElseThrow();
        audit.record(new RecordAuditEvent(null, actorId, AuditActorType.valueOf(actor.getPlatformRole().name()), action,
                action.startsWith("CLASS_") ? "SCHOOL_CLASS" : action.startsWith("ACADEMIC_TERM") ? "ACADEMIC_TERM" : "ACADEMIC_YEAR",
                resourceId, metadata));
    }
}
