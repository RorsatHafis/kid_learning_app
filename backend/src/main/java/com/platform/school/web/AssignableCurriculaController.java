package com.platform.school.web;

import com.platform.common.entity.CatalogStatus;
import com.platform.curriculum.entity.AgeHub;
import com.platform.curriculum.entity.Curriculum;
import com.platform.curriculum.entity.Subject;
import com.platform.curriculum.repository.AgeHubRepository;
import com.platform.curriculum.repository.CurriculumRepository;
import com.platform.curriculum.repository.SubjectRepository;
import com.platform.curriculum.service.CurriculumService;
import com.platform.school.service.SchoolAccessGuard;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What a member of staff may assign to a class (X22): the platform catalog, plus the curricula they
 * built themselves and published - exactly what {@code SchoolService#assignCurriculum} accepts (TD-12),
 * so a teacher's own curriculum from the Studio can reach their class. Never another author's.
 */
@RestController
public class AssignableCurriculaController {

    public record AssignableCurriculum(UUID id, String name, String subjectCode, String subjectName, String ageHubCode,
                                       String ageHubName, boolean own) {
    }

    private final SchoolAccessGuard guard;
    private final CurriculumService curriculumService;
    private final CurriculumRepository curricula;
    private final SubjectRepository subjects;
    private final AgeHubRepository ageHubs;
    private final com.platform.content.service.CatalogNames catalogNames;

    public AssignableCurriculaController(SchoolAccessGuard guard, CurriculumService curriculumService, CurriculumRepository curricula,
                                         SubjectRepository subjects, AgeHubRepository ageHubs, com.platform.content.service.CatalogNames catalogNames) {
        this.catalogNames = catalogNames;
        this.guard = guard;
        this.curriculumService = curriculumService;
        this.curricula = curricula;
        this.subjects = subjects;
        this.ageHubs = ageHubs;
    }

    @GetMapping("/api/v1/schools/curricula/assignable")
    @Transactional(readOnly = true)
    public List<AssignableCurriculum> assignable(@AuthenticationPrincipal UUID accountId) {
        guard.requireStaff(accountId);
        Map<UUID, Subject> subjectById = subjects.findAll().stream().collect(Collectors.toMap(Subject::getId, Function.identity()));
        Map<UUID, AgeHub> hubById = ageHubs.findAll().stream().collect(Collectors.toMap(AgeHub::getId, Function.identity()));
        List<AssignableCurriculum> list = new ArrayList<>();
        curricula.findByOwnerAccountId(accountId).stream()
                .filter(curriculum -> curriculum.getStatus() == CatalogStatus.ACTIVE)
                .filter(curriculum -> curriculumService.hasPublishedVersion(curriculum.getId()))
                .sorted(Comparator.comparing(Curriculum::getName, String.CASE_INSENSITIVE_ORDER))
                .forEach(curriculum -> list.add(of(curriculum, subjectById, hubById, true)));
        curriculumService.listFamilyCatalog().forEach(curriculum -> list.add(of(curriculum, subjectById, hubById, false)));
        return list;
    }

    /** Names in the reader's language where their Khmer is written (LOCALIZATION.md, catalog names). */
    private AssignableCurriculum of(Curriculum curriculum, Map<UUID, Subject> subjects, Map<UUID, AgeHub> hubs, boolean own) {
        Subject subject = subjects.get(curriculum.getSubjectId());
        AgeHub hub = hubs.get(curriculum.getAgeHubId());
        return new AssignableCurriculum(curriculum.getId(),
                catalogNames.name(com.platform.content.entity.LocalizableContentType.CURRICULUM, curriculum.getId(), curriculum.getName()), subject == null ? null : subject.getCode(),
                subject == null ? null : catalogNames.name(com.platform.content.entity.LocalizableContentType.SUBJECT, subject.getId(), subject.getName()), hub == null ? null : hub.getCode(), hub == null ? null : hub.getName(), own);
    }

}
