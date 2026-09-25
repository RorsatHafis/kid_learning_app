package com.platform.learning.service;

import com.platform.common.web.ResourceNotFoundException;
import com.platform.common.idempotency.service.IdempotencyService;
import com.platform.content.entity.Question;
import com.platform.content.entity.QuestionSkill;
import com.platform.content.entity.QuestionVersion;
import com.platform.content.repository.QuestionRepository;
import com.platform.content.repository.QuestionSkillRepository;
import com.platform.content.repository.QuestionVersionRepository;
import com.platform.learner.service.MasteryEngine;
import com.platform.learning.entity.AnswerRecord;
import com.platform.learning.repository.AnswerRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Performs the actual scored-answer write inside one transaction. Deliberately a
 * separate Spring bean from {@link ActivityAttemptService}, not a private method on
 * it - see {@code AccountRegistrationWriter}'s javadoc for why: {@code @Transactional}
 * is proxy-based, and a same-bean method call bypasses the proxy entirely.
 *
 * This is also the one place a scored answer becomes mastery evidence: every skill
 * tagged on the question (via {@link QuestionSkill}) gets exactly one
 * {@link MasteryEngine#recordEvidence} call using this same {@code correct} outcome,
 * in this same transaction - so an AnswerRecord is never persisted without its
 * mastery effect landing atomically alongside it. Previously this call was missing
 * entirely, which left MasteryEngine fully implemented but never invoked from the
 * answer-submission path (Section 13: "Do not leave mastery as isolated backend
 * code").
 */
@Service
class AnswerSubmissionWriter {

    private final QuestionVersionRepository questionVersionRepository;
    private final QuestionRepository questionRepository;
    private final QuestionSkillRepository questionSkillRepository;
    private final AnswerRecordRepository answerRecordRepository;
    private final AnswerScoringService scoringService;
    private final IdempotencyService idempotencyService;
    private final MasteryEngine masteryEngine;

    AnswerSubmissionWriter(QuestionVersionRepository questionVersionRepository, QuestionRepository questionRepository,
                           QuestionSkillRepository questionSkillRepository, AnswerRecordRepository answerRecordRepository,
                           AnswerScoringService scoringService, IdempotencyService idempotencyService,
                           MasteryEngine masteryEngine) {
        this.questionVersionRepository = questionVersionRepository;
        this.questionRepository = questionRepository;
        this.questionSkillRepository = questionSkillRepository;
        this.answerRecordRepository = answerRecordRepository;
        this.scoringService = scoringService;
        this.idempotencyService = idempotencyService;
        this.masteryEngine = masteryEngine;
    }

    @Transactional
    AnswerRecord write(UUID childId, UUID activityAttemptId, UUID questionVersionId, String submittedAnswer,
                       Integer timeSpentSeconds, UUID guardId) {
        // Known Backend Fix B (duplicate answer integrity): a request reusing the exact
        // same idempotency key never reaches this method at all - ActivityAttemptService
        // .submitAnswer() already returns the existing AnswerRecord for that case via
        // IdempotencyOutcome.AlreadyCompleted. Anything that does reach here with an
        // AnswerRecord already on file for this (activityAttemptId, questionVersionId)
        // pair is therefore a genuinely different submission (a different idempotency
        // key, e.g. from a buggy or duplicate client request) attempting to answer a
        // question this attempt has already answered - rejected, not accepted as a
        // second row. See ActivityAttemptService#submitAnswer for the catch-and-fail
        // that closes out the idempotency guard on this path, and V30 for the database
        // constraint backing this up independently of application code.
        if (answerRecordRepository.existsByActivityAttemptIdAndQuestionVersionId(activityAttemptId, questionVersionId)) {
            throw new IllegalStateException(
                    "ActivityAttempt %s already has an answer recorded for question version %s"
                            .formatted(activityAttemptId, questionVersionId));
        }

        QuestionVersion version = questionVersionRepository.findById(questionVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("QuestionVersion", questionVersionId));
        Question question = questionRepository.findById(version.getQuestionId())
                .orElseThrow(() -> new ResourceNotFoundException("Question", version.getQuestionId()));

        boolean correct = scoringService.score(question.getQuestionType(), version, submittedAnswer);

        AnswerRecord record = answerRecordRepository.save(
                AnswerRecord.record(activityAttemptId, questionVersionId, submittedAnswer, correct, timeSpentSeconds));

        List<QuestionSkill> taggedSkills = questionSkillRepository.findByQuestionId(question.getId());
        for (QuestionSkill taggedSkill : taggedSkills) {
            masteryEngine.recordEvidence(childId, taggedSkill.getSkillId(), correct, activityAttemptId);
        }

        // Called last, inside this same transaction: the evidence row, the mastery
        // updates above, and the guard's COMPLETED status all commit together
        // atomically - same reasoning as AccountRegistrationWriter's call to
        // idempotencyService.complete().
        idempotencyService.complete(guardId);

        return record;
    }

}