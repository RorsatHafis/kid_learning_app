package com.platform.school.web;

import com.platform.school.entity.AcademicTerm;
import com.platform.school.entity.AcademicYear;
import com.platform.school.entity.SchoolClass;
import com.platform.school.service.AcademicYearService;
import com.platform.school.service.SchoolAccessGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * M12 P1: a school's academic years and terms, and the year each class belongs to - for the school's principal (or an
 * administrator), while the school is paid (SchoolAccessGuard#requireSchoolManage), as every other school change.
 */
@RestController
@RequestMapping("/api/v1/schools/{schoolId}")
public class AcademicYearController {

    private final AcademicYearService service;
    private final SchoolAccessGuard guard;

    public AcademicYearController(AcademicYearService service, SchoolAccessGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping("/academic-years")
    public List<YearResponse> list(@PathVariable UUID schoolId, @AuthenticationPrincipal UUID accountId) {
        guard.requireSchoolManage(accountId, schoolId);
        return service.list(schoolId).stream().map(y -> YearResponse.of(y.year(), y.terms())).toList();
    }

    @PostMapping("/academic-years")
    public ResponseEntity<YearResponse> create(@PathVariable UUID schoolId, @Valid @RequestBody YearRequest request,
                                               @AuthenticationPrincipal UUID accountId) {
        guard.requireSchoolManage(accountId, schoolId);
        AcademicYear year = service.create(schoolId, request.name(), request.startsOn(), request.endsOn(), accountId);
        return ResponseEntity.status(HttpStatus.CREATED).body(YearResponse.of(year, List.of()));
    }

    @PutMapping("/academic-years/{yearId}")
    public YearResponse change(@PathVariable UUID schoolId, @PathVariable UUID yearId, @Valid @RequestBody YearRequest request,
                               @AuthenticationPrincipal UUID accountId) {
        guard.requireSchoolManage(accountId, schoolId);
        service.change(schoolId, yearId, request.name(), request.startsOn(), request.endsOn(), accountId);
        return one(schoolId, yearId);
    }

    @PostMapping("/academic-years/{yearId}/current")
    public YearResponse makeCurrent(@PathVariable UUID schoolId, @PathVariable UUID yearId, @AuthenticationPrincipal UUID accountId) {
        guard.requireSchoolManage(accountId, schoolId);
        service.makeCurrent(schoolId, yearId, accountId);
        return one(schoolId, yearId);
    }

    @PostMapping("/academic-years/{yearId}/archive")
    public YearResponse archive(@PathVariable UUID schoolId, @PathVariable UUID yearId, @AuthenticationPrincipal UUID accountId) {
        guard.requireSchoolManage(accountId, schoolId);
        service.archive(schoolId, yearId, accountId);
        return one(schoolId, yearId);
    }

    @PostMapping("/academic-years/{yearId}/restore")
    public YearResponse restore(@PathVariable UUID schoolId, @PathVariable UUID yearId, @AuthenticationPrincipal UUID accountId) {
        guard.requireSchoolManage(accountId, schoolId);
        service.restore(schoolId, yearId, accountId);
        return one(schoolId, yearId);
    }

    @PostMapping("/academic-years/{yearId}/terms")
    public ResponseEntity<TermResponse> addTerm(@PathVariable UUID schoolId, @PathVariable UUID yearId,
                                                @Valid @RequestBody TermRequest request, @AuthenticationPrincipal UUID accountId) {
        guard.requireSchoolManage(accountId, schoolId);
        AcademicTerm term = service.addTerm(schoolId, yearId, request.name(), request.startsOn(), request.endsOn(), accountId);
        return ResponseEntity.status(HttpStatus.CREATED).body(TermResponse.of(term));
    }

    @PutMapping("/academic-years/{yearId}/terms/{termId}")
    public TermResponse changeTerm(@PathVariable UUID schoolId, @PathVariable UUID yearId, @PathVariable UUID termId,
                                   @Valid @RequestBody TermRequest request, @AuthenticationPrincipal UUID accountId) {
        guard.requireSchoolManage(accountId, schoolId);
        return TermResponse.of(service.changeTerm(schoolId, yearId, termId, request.name(), request.startsOn(), request.endsOn(),
                accountId));
    }

    @DeleteMapping("/academic-years/{yearId}/terms/{termId}")
    public ResponseEntity<Void> removeTerm(@PathVariable UUID schoolId, @PathVariable UUID yearId, @PathVariable UUID termId,
                                           @AuthenticationPrincipal UUID accountId) {
        guard.requireSchoolManage(accountId, schoolId);
        service.removeTerm(schoolId, yearId, termId, accountId);
        return ResponseEntity.noContent().build();
    }

    /** Year-end rollover: the earlier year's classes, by name, into this one (children and teachers stay where they are). */
    @PostMapping("/academic-years/{yearId}/copy-classes")
    public CopyResponse copyClasses(@PathVariable UUID schoolId, @PathVariable UUID yearId, @Valid @RequestBody CopyRequest request,
                                    @AuthenticationPrincipal UUID accountId) {
        guard.requireSchoolManage(accountId, schoolId);
        AcademicYearService.CopiedClasses copied = service.copyClasses(schoolId, request.fromYearId(), yearId, accountId);
        return new CopyResponse(copied.created().stream().map(SchoolClass::getName).toList(), copied.alreadyThere());
    }

    /** {@code academicYearId} null takes the class out of any year. */
    @PutMapping("/classes/{classId}/academic-year")
    public ClassYearResponse placeClass(@PathVariable UUID schoolId, @PathVariable UUID classId,
                                        @RequestBody ClassYearRequest request, @AuthenticationPrincipal UUID accountId) {
        guard.requireSchoolManage(accountId, schoolId);
        SchoolClass placed = service.placeClass(schoolId, classId, request.academicYearId(), accountId);
        return new ClassYearResponse(placed.getId(), placed.getAcademicYearId());
    }

    private YearResponse one(UUID schoolId, UUID yearId) {
        return service.list(schoolId).stream().filter(y -> y.year().getId().equals(yearId)).findFirst()
                .map(y -> YearResponse.of(y.year(), y.terms())).orElseThrow();
    }

    public record YearRequest(@NotBlank @Size(max = 80) String name, @NotNull LocalDate startsOn, @NotNull LocalDate endsOn) {}

    public record TermRequest(@NotBlank @Size(max = 80) String name, @NotNull LocalDate startsOn, @NotNull LocalDate endsOn) {}

    public record ClassYearRequest(UUID academicYearId) {}

    public record CopyRequest(@NotNull UUID fromYearId) {}

    public record CopyResponse(List<String> created, List<String> alreadyThere) {}

    public record TermResponse(UUID id, String name, LocalDate startsOn, LocalDate endsOn) {
        static TermResponse of(AcademicTerm t) { return new TermResponse(t.getId(), t.getName(), t.getStartsOn(), t.getEndsOn()); }
    }

    public record YearResponse(UUID id, String name, LocalDate startsOn, LocalDate endsOn, boolean current, boolean archived,
                               List<TermResponse> terms) {
        static YearResponse of(AcademicYear y, List<AcademicTerm> terms) {
            return new YearResponse(y.getId(), y.getName(), y.getStartsOn(), y.getEndsOn(), y.isCurrent(), y.isArchived(),
                    terms.stream().map(TermResponse::of).toList());
        }
    }

    public record ClassYearResponse(UUID classId, UUID academicYearId) {}
}
