package com.platform.school.web;

import com.platform.school.entity.School;
import com.platform.school.entity.SchoolClass;
import com.platform.school.service.SchoolLifecycleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Renaming a school or class, and archiving or restoring a class (X17): the school's principal only. */
@RestController
@RequestMapping("/api/v1/schools")
public class SchoolLifecycleController {

    private final SchoolLifecycleService lifecycle;

    public SchoolLifecycleController(SchoolLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }

    public record RenameRequest(@NotBlank @Size(max = 120) String name) {
    }

    public record SchoolState(UUID id, String name) {
        static SchoolState of(School school) {
            return new SchoolState(school.getId(), school.getName());
        }
    }

    public record ClassState(UUID id, UUID schoolId, String name, boolean archived) {
        static ClassState of(SchoolClass schoolClass) {
            return new ClassState(schoolClass.getId(), schoolClass.getSchoolId(), schoolClass.getName(), schoolClass.isArchived());
        }
    }

    @PutMapping("/{schoolId}")
    public SchoolState renameSchool(@PathVariable UUID schoolId, @Valid @RequestBody RenameRequest request,
                                    @AuthenticationPrincipal UUID accountId) {
        return SchoolState.of(lifecycle.renameSchool(accountId, schoolId, request.name()));
    }

    @PutMapping("/classes/{classId}")
    public ClassState renameClass(@PathVariable UUID classId, @Valid @RequestBody RenameRequest request,
                                  @AuthenticationPrincipal UUID accountId) {
        return ClassState.of(lifecycle.renameClass(accountId, classId, request.name()));
    }

    @PostMapping("/classes/{classId}/archive")
    public ClassState archiveClass(@PathVariable UUID classId, @AuthenticationPrincipal UUID accountId) {
        return ClassState.of(lifecycle.archiveClass(accountId, classId));
    }

    @PostMapping("/classes/{classId}/restore")
    public ClassState restoreClass(@PathVariable UUID classId, @AuthenticationPrincipal UUID accountId) {
        return ClassState.of(lifecycle.restoreClass(accountId, classId));
    }

}
