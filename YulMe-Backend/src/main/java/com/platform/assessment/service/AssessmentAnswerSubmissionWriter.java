package com.platform.assessment.service;

import com.platform.assessment.entity.AssessmentAnswer;
import com.platform.assessment.entity.AssessmentItem;
import com.platform.assessment.repository.AssessmentAnswerRepository;
import com.platform.assessment.repository.AssessmentItemRepository;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.common.idempotency.service.IdempotencyService;
import com.platform.content.entity.Question;
import com.platform.content.entity.QuestionVersion;
import com.platform.content.repository.QuestionRepository;
import com.platform.content.repository.QuestionVersionRepository;
import com.platform.learning.service.AnswerScoringService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Separate bean from {@link AssessmentAttemptService} for the same
 * {@code @Transactional} self-invocation reason as {@code AnswerSubmissionWriter}.
 * Reuses {@link AnswerScoringService} rather than duplicating scoring logic -
 * scoring a question is scoring a question, regardless of which domain the attempt
 * belongs to.
 */
@Service
class AssessmentAnswerSubmissionWriter {

    private final AssessmentItemRepository assessmentItemRepository;
    private final QuestionVersionRepository questionVersionRepository;
    private final QuestionRepository questionRepository;
    private final AssessmentAnswerRepository assessmentAnswerRepository;
    private final AnswerScoringService scoringService;
    private final IdempotencyService idempotencyService;

    AssessmentAnswerSubmissionWriter(AssessmentItemRepository assessmentItemRepository,
                                     QuestionVersionRepository questionVersionRepository,
                                     QuestionRepository questionRepository,
                                     AssessmentAnswerRepository assessmentAnswerRepository,
                                     AnswerScoringService scoringService, IdempotencyService idempotencyService) {
        this.assessmentItemRepository = assessmentItemRepository;
        this.questionVersionRepository = questionVersionRepository;
        this.questionRepository = questionRepository;
        this.assessmentAnswerRepository = assessmentAnswerRepository;
        this.scoringService = scoringService;
        this.idempotencyService = idempotencyService;
    }

    @Transactional
    AssessmentAnswer write(UUID assessmentAttemptId, UUID assessmentItemId, String submittedAnswer,
                           Integer timeSpentSeconds, UUID guardId) {
        AssessmentItem item = assessmentItemRepository.findById(assessmentItemId)
                .orElseThrow(() -> new ResourceNotFoundException("AssessmentItem", assessmentItemId));
        QuestionVersion version = questionVersionRepository.findById(item.getQuestionVersionId())
                .orElseThrow(() -> new ResourceNotFoundException("QuestionVersion", item.getQuestionVersionId()));
        Question question = questionRepository.findById(version.getQuestionId())
                .orElseThrow(() -> new ResourceNotFoundException("Question", version.getQuestionId()));

        boolean correct = scoringService.score(question.getQuestionType(), version, submittedAnswer);
        BigDecimal pointsAwarded = correct ? item.getPoints() : BigDecimal.ZERO;

        AssessmentAnswer answer = assessmentAnswerRepository.save(AssessmentAnswer.record(
                assessmentAttemptId, assessmentItemId, submittedAnswer, correct, pointsAwarded, timeSpentSeconds));

        idempotencyService.complete(guardId);

        return answer;
    }

}