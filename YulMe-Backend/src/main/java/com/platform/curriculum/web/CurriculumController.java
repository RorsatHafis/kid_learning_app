package com.platform.curriculum.web;

import com.platform.common.web.ResourceNotFoundException;
import com.platform.curriculum.entity.AgeHub;
import com.platform.curriculum.entity.Curriculum;
import com.platform.curriculum.entity.Subject;
import com.platform.curriculum.repository.AgeHubRepository;
import com.platform.curriculum.repository.SubjectRepository;
import com.platform.curriculum.service.CurriculumService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.platform.curriculum.web.CurriculumDtos.CurriculumSummaryResponse;

/**
 * Read-only curriculum catalog browsing - another missing "step 2.5" of the golden
 * journey found alongside the Child gap: {@code POST /children/{childId}/enrollments}
 * needs a {@code curriculumId} in its body, but nothing exposed which curricula
 * exist. Curriculum content is not family/child-scoped data (it's shared catalog
 * content, same as every other unauthenticated-within-the-app content lookup in
 * this codebase - see {@code ActivityContentController}), so this only requires an
 * authenticated caller, not a {@code FamilyAccessGuard} check.
 */
@RestController
public class CurriculumController {

    private final CurriculumService curriculumService;
    private final SubjectRepository subjectRepository;
    private final AgeHubRepository ageHubRepository;

    public CurriculumController(CurriculumService curriculumService, SubjectRepository subjectRepository,
                                 AgeHubRepository ageHubRepository) {
        this.curriculumService = curriculumService;
        this.subjectRepository = subjectRepository;
        this.ageHubRepository = ageHubRepository;
    }

    @GetMapping("/api/v1/curricula")
    public ResponseEntity<List<CurriculumSummaryResponse>> listCurricula() {
        List<CurriculumSummaryResponse> curricula = curriculumService.listActive().stream()
                .map(this::toSummary)
                .toList();

        return ResponseEntity.ok(curricula);
    }

    private CurriculumSummaryResponse toSummary(Curriculum curriculum) {
        Subject subject = subjectRepository.findById(curriculum.getSubjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Subject", curriculum.getSubjectId()));
        AgeHub ageHub = ageHubRepository.findById(curriculum.getAgeHubId())
                .orElseThrow(() -> new ResourceNotFoundException("AgeHub", curriculum.getAgeHubId()));

        return new CurriculumSummaryResponse(curriculum.getId(), curriculum.getName(),
                subject.getCode(), subject.getName(), ageHub.getCode(), ageHub.getName());
    }

}
