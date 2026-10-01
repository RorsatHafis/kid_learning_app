package com.platform.curriculum.service;

import com.platform.common.entity.CatalogStatus;
import com.platform.common.entity.PublicationStatus;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.content.validation.ContentValidationException;
import com.platform.content.validation.ContentValidator;
import com.platform.content.validation.ValidationReport;
import com.platform.curriculum.entity.Curriculum;
import com.platform.curriculum.entity.CurriculumObjective;
import com.platform.curriculum.entity.CurriculumVersion;
import com.platform.curriculum.repository.CurriculumObjectiveRepository;
import com.platform.curriculum.repository.CurriculumRepository;
import com.platform.curriculum.repository.CurriculumVersionRepository;
import com.platform.curriculum.repository.LearningObjectiveRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The curriculum authoring/publishing workflow. This is the first place actually
 * enforcing the "a *_versions row may only be edited while DRAFT" rule that V10's
 * migration comment flagged as an application-level (not DB-level) responsibility -
 * {@link #addObjective} checks it explicitly.
 */
@Service
public class CurriculumService {

    private final CurriculumRepository curriculumRepository;
    private final CurriculumVersionRepository curriculumVersionRepository;
    private final CurriculumObjectiveRepository curriculumObjectiveRepository;
    private final LearningObjectiveRepository learningObjectiveRepository;
    private final ContentValidator contentValidator;

    public CurriculumService(CurriculumRepository curriculumRepository,
                              CurriculumVersionRepository curriculumVersionRepository,
                              CurriculumObjectiveRepository curriculumObjectiveRepository,
                              LearningObjectiveRepository learningObjectiveRepository,
                              ContentValidator contentValidator) {
        this.contentValidator = contentValidator;
        this.curriculumRepository = curriculumRepository;
        this.curriculumVersionRepository = curriculumVersionRepository;
        this.curriculumObjectiveRepository = curriculumObjectiveRepository;
        this.learningObjectiveRepository = learningObjectiveRepository;
    }

    @Transactional
    public Curriculum createCurriculum(UUID subjectId, UUID ageHubId, String name) {
        return curriculumRepository.save(Curriculum.create(subjectId, ageHubId, name));
    }

    @Transactional
    public Curriculum createOwnedCurriculum(UUID subjectId, UUID ageHubId, String name, UUID ownerAccountId) {
        return curriculumRepository.save(Curriculum.createOwned(subjectId, ageHubId, name, ownerAccountId));
    }

    @Transactional(readOnly = true)
    public Curriculum getCurriculum(UUID id) {
        return curriculumRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Curriculum", id));
    }

    /** Every curriculum currently open for enrollment browsing (mission golden journey: how the frontend discovers what to enroll a child in without a client-supplied id). */
    @Transactional(readOnly = true)
    public List<Curriculum> listActive() {
        return curriculumRepository.findByStatus(CatalogStatus.ACTIVE);
    }

    /** Every ACTIVE curriculum for a given age hub, across all subjects - used by EnrollmentService#autoEnrollIfUnambiguous to find candidate(s) a newly created child could be started in without a client-supplied curriculum choice. */
    @Transactional(readOnly = true)
    public List<Curriculum> listActiveForAgeHub(UUID ageHubId) {
        return curriculumRepository.findByAgeHubIdAndStatus(ageHubId, CatalogStatus.ACTIVE);
    }

    /**
     * The family catalog (N7): ACTIVE platform curricula with a published version. One query for
     * what is published, whatever the size of the catalog.
     */
    @Transactional(readOnly = true)
    public List<Curriculum> listFamilyCatalog() {
        java.util.Set<UUID> published = curriculumVersionRepository.findCurriculumIdsWithStatus(PublicationStatus.PUBLISHED);
        return listActive().stream()
                .filter(curriculum -> curriculum.getOwnerAccountId() == null)
                .filter(curriculum -> published.contains(curriculum.getId()))
                .toList();
    }

    /** Non-throwing counterpart to {@link #getPublishedVersion}, for callers that need to check readiness across several candidates without exception-driven control flow. */
    @Transactional(readOnly = true)
    public boolean hasPublishedVersion(UUID curriculumId) {
        return curriculumVersionRepository
                .findFirstByCurriculumIdAndStatusOrderByVersionNumberDesc(curriculumId, PublicationStatus.PUBLISHED)
                .isPresent();
    }

    @Transactional
    public CurriculumVersion createDraftVersion(UUID curriculumId) {
        if (!curriculumRepository.existsById(curriculumId)) {
            throw new ResourceNotFoundException("Curriculum", curriculumId);
        }
        int nextVersionNumber = curriculumVersionRepository.findTopByCurriculumIdOrderByVersionNumberDesc(curriculumId)
                .map(previous -> previous.getVersionNumber() + 1)
                .orElse(1);
        return curriculumVersionRepository.save(CurriculumVersion.draft(curriculumId, nextVersionNumber));
    }

    @Transactional(readOnly = true)
    public CurriculumVersion getVersion(UUID curriculumVersionId) {
        return curriculumVersionRepository.findById(curriculumVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("CurriculumVersion", curriculumVersionId));
    }

    /** The version a new Enrollment should actually use - null if nothing has been published yet. */
    @Transactional(readOnly = true)
    public CurriculumVersion getPublishedVersion(UUID curriculumId) {
        return curriculumVersionRepository
                .findFirstByCurriculumIdAndStatusOrderByVersionNumberDesc(curriculumId, PublicationStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Published CurriculumVersion for curriculum", curriculumId));
    }

    @Transactional
    public CurriculumObjective addObjective(UUID curriculumVersionId, UUID learningObjectiveId, int displayOrder) {
        CurriculumVersion version = getVersion(curriculumVersionId);
        if (version.getStatus() != PublicationStatus.DRAFT) {
            throw new IllegalStateException(
                    "Objectives can only be added to a DRAFT version, %s is %s".formatted(curriculumVersionId, version.getStatus()));
        }
        if (!learningObjectiveRepository.existsById(learningObjectiveId)) {
            throw new ResourceNotFoundException("LearningObjective", learningObjectiveId);
        }
        return curriculumObjectiveRepository.save(
                CurriculumObjective.create(curriculumVersionId, learningObjectiveId, displayOrder));
    }

    /** Replaces a draft's learning objectives, in the given order. */
    @Transactional
    public List<CurriculumObjective> replaceObjectives(UUID curriculumVersionId, List<UUID> learningObjectiveIds) {
        CurriculumVersion version = getVersion(curriculumVersionId);
        if (version.getStatus() != PublicationStatus.DRAFT) {
            throw new IllegalStateException(
                    "Objectives can only be changed on a DRAFT version, %s is %s".formatted(curriculumVersionId, version.getStatus()));
        }
        if (learningObjectiveIds.stream().distinct().count() != learningObjectiveIds.size()) {
            throw new IllegalArgumentException("An objective can appear only once in a curriculum");
        }
        for (UUID objectiveId : learningObjectiveIds) {
            if (!learningObjectiveRepository.existsById(objectiveId)) {
                throw new ResourceNotFoundException("LearningObjective", objectiveId);
            }
        }
        curriculumObjectiveRepository.deleteByCurriculumVersionId(curriculumVersionId);
        curriculumObjectiveRepository.flush();
        for (int i = 0; i < learningObjectiveIds.size(); i++) {
            curriculumObjectiveRepository.save(CurriculumObjective.create(curriculumVersionId, learningObjectiveIds.get(i), i));
        }
        return listObjectives(curriculumVersionId);
    }

    /**
     * The draft to edit next: the open draft if there is one, otherwise a new draft
     * copying the latest version's objectives. Enrolled children keep the version they
     * were enrolled in; publishing the new one only changes what new enrollments get.
     */
    @Transactional
    public CurriculumVersion openDraft(UUID curriculumId) {
        java.util.Optional<CurriculumVersion> latest = curriculumVersionRepository.findTopByCurriculumIdOrderByVersionNumberDesc(curriculumId);
        if (latest.isPresent() && latest.get().getStatus() == PublicationStatus.DRAFT) {
            return latest.get();
        }
        CurriculumVersion draft = createDraftVersion(curriculumId);
        if (latest.isPresent()) {
            for (CurriculumObjective objective : listObjectives(latest.get().getId())) {
                curriculumObjectiveRepository.save(CurriculumObjective.create(draft.getId(),
                        objective.getLearningObjectiveId(), objective.getDisplayOrder()));
            }
        }
        return draft;
    }

    @Transactional(readOnly = true)
    public List<CurriculumVersion> listVersions(UUID curriculumId) {
        return curriculumVersionRepository.findByCurriculumIdOrderByVersionNumberAsc(curriculumId);
    }

    @Transactional(readOnly = true)
    public List<CurriculumObjective> listObjectives(UUID curriculumVersionId) {
        return curriculumObjectiveRepository.findByCurriculumVersionIdOrderByDisplayOrderAsc(curriculumVersionId);
    }

    /**
     * Publishes a DRAFT version. If another version of the same curriculum is
     * currently PUBLISHED, it's retired first (and that retirement is flushed
     * before the new publish() is applied) so the partial unique index added in
     * V20 (at most one PUBLISHED row per curriculum) is never momentarily violated
     * regardless of Hibernate's default statement-flush ordering.
     */
    @Transactional
    public CurriculumVersion publish(UUID curriculumVersionId) {
        CurriculumVersion version = getVersion(curriculumVersionId);
        // Includes "at least one learning objective" (NO_OBJECTIVES) - see ContentValidator.
        ValidationReport report = contentValidator.curriculumVersion(curriculumVersionId);
        if (!report.publishable()) {
            throw new ContentValidationException("Curriculum version " + curriculumVersionId, report);
        }

        curriculumVersionRepository
                .findFirstByCurriculumIdAndStatusOrderByVersionNumberDesc(version.getCurriculumId(), PublicationStatus.PUBLISHED)
                .ifPresent(previouslyPublished -> {
                    previouslyPublished.retire();
                    curriculumVersionRepository.saveAndFlush(previouslyPublished);
                });

        version.publish(Instant.now());
        return curriculumVersionRepository.saveAndFlush(version);
    }

}