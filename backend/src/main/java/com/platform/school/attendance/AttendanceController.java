package com.platform.school.attendance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** M12 attendance: a class's students and their daily register (see {@link AttendanceService}). */
@RestController
public class AttendanceController {

    private final AttendanceService attendance;

    public AttendanceController(AttendanceService attendance) {
        this.attendance = attendance;
    }

    public record NewStudentRequest(@Size(max = 120) String name, @Size(max = 40) String number) {
    }

    /** {@code sameNameIsNew}: the caller confirms these are new students although the name is in another class. */
    public record AddStudentsRequest(@NotNull @Size(max = 200) List<@Valid NewStudentRequest> students, Boolean sameNameIsNew) {
    }

    public record MoveStudentsRequest(@NotNull UUID fromClassId, @NotNull @Size(max = 200) List<@NotNull UUID> studentIds) {
    }

    public record ReturnStudentsRequest(@NotNull @Size(max = 200) List<@NotNull UUID> studentIds) {
    }

    public record UpdateStudentRequest(@Size(max = 120) String name, @Size(max = 40) String number) {
    }

    public record MarkRequest(@NotNull UUID studentId, @Size(max = 16) String status, @Size(max = 300) String note) {
    }

    public record RecordRequest(@NotNull @Size(max = 500) List<@Valid MarkRequest> marks, @Size(max = 300) String reason) {
    }

    @GetMapping("/api/v1/schools/classes/{classId}/students")
    public List<AttendanceService.Student> students(@AuthenticationPrincipal UUID accountId, @PathVariable UUID classId) {
        return attendance.students(accountId, classId);
    }

    @PostMapping("/api/v1/schools/classes/{classId}/students")
    @ResponseStatus(HttpStatus.CREATED)
    public AttendanceService.Added add(@AuthenticationPrincipal UUID accountId, @PathVariable UUID classId,
                                       @Valid @RequestBody AddStudentsRequest request) {
        return attendance.add(accountId, classId, request.students().stream()
                .map(s -> new AttendanceService.NewStudent(s.name(), s.number())).toList(), Boolean.TRUE.equals(request.sameNameIsNew()));
    }

    @PostMapping("/api/v1/schools/classes/{classId}/students/move")
    public AttendanceService.Added move(@AuthenticationPrincipal UUID accountId, @PathVariable UUID classId,
                                        @Valid @RequestBody MoveStudentsRequest request) {
        return attendance.move(accountId, classId, request.fromClassId(), request.studentIds());
    }

    @GetMapping("/api/v1/schools/{schoolId}/students")
    public com.platform.common.web.PageResponse<AttendanceService.SchoolStudentRow> schoolStudents(
            @AuthenticationPrincipal UUID accountId, @PathVariable UUID schoolId, @RequestParam(required = false) String q,
            @RequestParam(required = false) String which, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return attendance.schoolStudents(accountId, schoolId, q, which, page, size);
    }

    @PostMapping("/api/v1/schools/classes/{classId}/students/return")
    public AttendanceService.Added returnToClass(@AuthenticationPrincipal UUID accountId, @PathVariable UUID classId,
                                                 @Valid @RequestBody ReturnStudentsRequest request) {
        return attendance.returnToClass(accountId, classId, request.studentIds());
    }

    @PutMapping("/api/v1/schools/classes/{classId}/students/{studentId}")
    public AttendanceService.Student update(@AuthenticationPrincipal UUID accountId, @PathVariable UUID classId, @PathVariable UUID studentId,
                                            @Valid @RequestBody UpdateStudentRequest request) {
        return attendance.update(accountId, classId, studentId, request.name(), request.number());
    }

    @PostMapping("/api/v1/schools/classes/{classId}/students/{studentId}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@AuthenticationPrincipal UUID accountId, @PathVariable UUID classId, @PathVariable UUID studentId) {
        attendance.leave(accountId, classId, studentId);
    }

    @GetMapping("/api/v1/schools/classes/{classId}/attendance")
    public AttendanceService.Register register(@AuthenticationPrincipal UUID accountId, @PathVariable UUID classId,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        return attendance.register(accountId, classId, day);
    }

    @PutMapping("/api/v1/schools/classes/{classId}/attendance")
    public AttendanceService.Register record(@AuthenticationPrincipal UUID accountId, @PathVariable UUID classId,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
                                             @Valid @RequestBody RecordRequest request) {
        return attendance.record(accountId, classId, day, request.marks().stream()
                .map(m -> new AttendanceService.MarkInput(m.studentId(), m.status(), m.note())).toList(), request.reason());
    }

    @GetMapping("/api/v1/schools/classes/{classId}/attendance/month")
    public AttendanceService.Month month(@AuthenticationPrincipal UUID accountId, @PathVariable UUID classId,
                                         @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return attendance.month(accountId, classId, month);
    }
}
