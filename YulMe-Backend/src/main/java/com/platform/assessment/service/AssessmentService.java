package com.platform.assessment.service;

import com.platform.assessment.entity.Assessment;
import com.platform.assessment.entity.AssessmentItem;
import com.platform.assessment.entity.AssessmentType;
import com.platform.assessment.entity.AssessmentVersion;
import com.platform.assessment.repository.AssessmentItemRepository;
import com.platform.assessment.repository.AssessmentRepository;
import com.platform.assessment.repository.AssessmentVersionRepository;
import com.platform.common.entity.PublicationStatus;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.content.repository.QuestionVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Assessment authoring/publishing. Mirrors CurriculumService's pattern - see that class for the retire-before-publish rationale. */
@Service
public class AssessmentService {

    private final AssessmentRepository assessmentRepository;
    private final AssessmentVersionRepository assessmentVersionRepository;
    private final AssessmentItemRepository assessmentItemRepository;
    private final QuestionVersionRepository questionVersionRepository;

    public AssessmentService(AssessmentRepository assessmentRepository, AssessmentVersionRepository assessmentVersionRepository,
                              AssessmentItemRepository assessmentItemRepository, QuestionVersionRepository questionVersionRepository) {
        this.assessmentRepository = assessmentRepository;
        this.assessmentVersionRepository = assessmentVersionRepository;
        this.assessmentItemRepository = assessmentItemRepository;
        this.questionVersionRepository = questionVersionRepository;
    }

    @Transactional
    public Assessment createAssessment(UUID subjectId, String title, AssessmentType type) {
        return assessmentRepository.save(Assessment.create(subjectId, title, type));
    }

    @Transactional(readOnly = true)
    public Assessment getAssessment(UUID id) {
        return assessmentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Assessment", id));
    }

    @Transactional
    public AssessmentVersion createDraftVersion(UUID assessmentId, String instructions) {
        if (!assessmentRepository.existsById(assessmentId)) {
            throw new ResourceNotFoundException("Assessment", assessmentId);
        }
        int nextVersionNumber = assessmentVersionRepository.findTopByAssessmentIdOrderByVersionNumberDesc(assessmentId)
                .map(previous -> previous.getVersionNumber() + 1)
                .orElse(1);
        return assessmentVersionRepository.save(AssessmentVersion.draft(assessmentId, nextVersionNumber, instructions));
    }

    @Transactional(readOnly = true)
    public AssessmentVersion getVersion(UUID assessmentVersionId) {
        return assessmentVersionRepository.findById(assessmentVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("AssessmentVersion", assessmentVersionId));
    }

    @Transactional(readOnly = true)
    public AssessmentVersion getPublishedVersion(UUID assessmentId) {
        return assessmentVersionRepository
                .findFirstByAssessmentIdAndStatusOrderByVersionNumberDesc(assessmentId, PublicationStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Published AssessmentVersion for assessment", assessmentId));
    }

    @Transactional
    public AssessmentItem addQuestion(UUID assessmentVersionId, UUID questionVersionId, int sequenceOrder, BigDecimal points) {
        if (!assessmentVersionRepository.existsById(assessmentVersionId)) {
            throw new ResourceNotFoundException("AssessmentVersion", assessmentVersionId);
        }
        if (!questionVersionRepository.existsById(questionVersionId)) {
            throw new ResourceNotFoundException("QuestionVersion", questionVersionId);
        }
        return assessmentItemRepository.save(AssessmentItem.create(assessmentVersionId, questionVersionId, sequenceOrder, points));
    }

    @Transactional(readOnly = true)
    public List<AssessmentItem> listItems(UUID assessmentVersionId) {
        return assessmentItemRepository.findByAssessmentVersionIdOrderBySequenceOrderAsc(assessmentVersionId);
    }

    /** See CurriculumService.publish() for the retire-before-publish ordering rationale (V20 partial unique index). */
    @Transactional
    public AssessmentVersion publish(UUID assessmentVersionId) {
        AssessmentVersion version = getVersion(assessmentVersionId);

        if (assessmentItemRepository.findByAssessmentVersionIdOrderBySequenceOrderAsc(assessmentVersionId).isEmpty()) {
            throw new IllegalStateException("Cannot publish assessment version %s with no items".formatted(assessmentVersionId));
        }

        assessmentVersionRepository
                .findFirstByAssessmentIdAndStatusOrderByVersionNumberDesc(version.getAssessmentId(), PublicationStatus.PUBLISHED)
                .ifPresent(previouslyPublished -> {
                    previouslyPublished.retire();
                    assessmentVersionRepository.saveAndFlush(previouslyPublished);
                });

        version.publish(Instant.now());
        return assessmentVersionRepository.saveAndFlush(version);
    }

}