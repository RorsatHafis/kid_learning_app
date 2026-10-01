package com.platform.curriculum.web;

import com.platform.child.entity.Child;
import com.platform.curriculum.entity.AgeHub;
import com.platform.curriculum.entity.Curriculum;
import com.platform.curriculum.entity.Subject;
import com.platform.curriculum.repository.AgeHubRepository;
import com.platform.curriculum.repository.SubjectRepository;
import com.platform.curriculum.service.CurriculumService;
import com.platform.family.service.FamilyAccessGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.platform.curriculum.web.CurriculumDtos.CurriculumChoiceResponse;
import static com.platform.curriculum.web.CurriculumDtos.CurriculumSummaryResponse;

/**
 * Read-only curriculum catalog browsing - another missing "step 2.5" of the golden
 * journey found alongside the Child gap: {@code POST /children/{childId}/enrollments}
 * needs a {@code curriculumId} in its body, but nothing exposed which curricula
 * exist. Curriculum content is not family/child-scoped data (it's shared catalog
 * content, same as every other unauthenticated-within-the-app content lookup in
 * this codebase - see {@code ActivityContentController}), so this only requires an
 * authenticated caller, not a {@code FamilyAccessGuard} check. The child-scoped
 * listing reads the child's age, so it does check family access.
 *
 * <p>N7: the catalog lists platform curricula only. A teacher-owned curriculum is
 * that teacher's (or school's) content and reaches children through class
 * assignment, not through the family catalog.
 *
 * <p>Both listings read subjects and age groups once and what is published in one query, so
 * the number of queries does not grow with the catalog.
 */
@RestController
public class CurriculumController {

    private final CurriculumService curriculumService;
    private final SubjectRepository subjectRepository;
    private final AgeHubRepository ageHubRepository;
    private final FamilyAccessGuard familyAccessGuard;

    public CurriculumController(CurriculumService curriculumService, SubjectRepository subjectRepository,
                                AgeHubRepository ageHubRepository, FamilyAccessGuard familyAccessGuard) {
        this.curriculumService = curriculumService;
        this.subjectRepository = subjectRepository;
        this.ageHubRepository = ageHubRepository;
        this.familyAccessGuard = familyAccessGuard;
    }

    @GetMapping("/api/v1/curricula")
    @Transactional(readOnly = true)
    public ResponseEntity<List<CurriculumSummaryResponse>> listCurricula() {
        Lookups lookups = lookups();
        return ResponseEntity.ok(curriculumService.listFamilyCatalog().stream()
                .map(curriculum -> toSummary(curriculum, lookups))
                .toList());
    }

    /**
     * The family catalog for one child: every curriculum, marked with whether it is for the child's
     * age group (the same rule automatic enrolment uses), those first, then by subject and age.
     * Other ages stay available - choosing is the family's.
     */
    @GetMapping("/api/v1/children/{childId}/curricula")
    @Transactional(readOnly = true)
    public List<CurriculumChoiceResponse> listForChild(@PathVariable UUID childId, @AuthenticationPrincipal UUID accountId) {
        Child child = familyAccessGuard.requireChildAccess(accountId, childId);
        Optional<UUID> childAgeHub = ageHubRepository.findForAge(child.ageInYears(LocalDate.now())).map(AgeHub::getId);
        Lookups lookups = lookups();
        return curriculumService.listFamilyCatalog().stream()
                .map(curriculum -> {
                    AgeHub hub = lookups.ageHubs().get(curriculum.getAgeHubId());
                    CurriculumSummaryResponse summary = toSummary(curriculum, lookups);
                    return new CurriculumChoiceResponse(summary.id(), summary.name(), summary.subjectCode(), summary.subjectName(),
                            summary.ageHubCode(), summary.ageHubName(), hub.getMinAge(), hub.getMaxAge(),
                            childAgeHub.map(id -> id.equals(hub.getId())).orElse(false), hub.getDisplayOrder());
                })
                .sorted(Comparator.comparing((CurriculumChoiceResponse choice) -> !choice.forChildAge())
                        .thenComparing(CurriculumChoiceResponse::subjectName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparingInt(CurriculumChoiceResponse::ageOrder)
                        .thenComparing(CurriculumChoiceResponse::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private record Lookups(Map<UUID, Subject> subjects, Map<UUID, AgeHub> ageHubs) {
    }

    /** Both are small reference tables: read whole, once per request. */
    private Lookups lookups() {
        return new Lookups(subjectRepository.findAll().stream().collect(Collectors.toMap(Subject::getId, Function.identity())),
                ageHubRepository.findAll().stream().collect(Collectors.toMap(AgeHub::getId, Function.identity())));
    }

    private static CurriculumSummaryResponse toSummary(Curriculum curriculum, Lookups lookups) {
        Subject subject = lookups.subjects().get(curriculum.getSubjectId());
        AgeHub ageHub = lookups.ageHubs().get(curriculum.getAgeHubId());
        return new CurriculumSummaryResponse(curriculum.getId(), curriculum.getName(),
                subject.getCode(), subject.getName(), ageHub.getCode(), ageHub.getName());
    }

}
