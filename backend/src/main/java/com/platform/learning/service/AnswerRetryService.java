package com.platform.learning.service;

import com.platform.common.web.ResourceNotFoundException;
import com.platform.content.entity.MatchSide;
import com.platform.content.entity.Question;
import com.platform.content.entity.QuestionVersion;
import com.platform.content.entity.QuestionVersionSkill;
import com.platform.content.repository.QuestionOptionRepository;
import com.platform.content.repository.QuestionRepository;
import com.platform.content.repository.QuestionVersionRepository;
import com.platform.content.repository.QuestionVersionSkillRepository;
import com.platform.learner.RetryProperties;
import com.platform.learner.service.MasteryEngine;
import com.platform.learning.entity.ActivityAttempt;
import com.platform.learning.entity.ActivityAttemptStatus;
import com.platform.learning.entity.AnswerRecord;
import com.platform.learning.entity.AnswerRetry;
import com.platform.learning.repository.ActivityAttemptRepository;
import com.platform.learning.repository.AnswerRecordRepository;
import com.platform.learning.repository.AnswerRetryRepository;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * D-20 (owner decision 2026-11-11): one retry, with feedback, after a wrong answer - and a right second try is weaker
 * mastery evidence than a right first one.
 *
 * <ul>
 *   <li>Offered (decided here, by the server) when a guess can't simply be right the second time: typed answers,
 *       sorting, and choice / matching / ordering questions with three or more items. Never for true-or-false or a
 *       two-item question, nor for a drawing (never scored).</li>
 *   <li>While a retry is open the question has no evidence yet, and the right answer and explanation are held back.
 *       It closes - and the question's single piece of evidence is recorded - when the child answers again (right:
 *       {@link RetryProperties#secondTryCredit()}; wrong: a miss), asks to see the answer, or moves on, finishes or
 *       stops (a miss). So a question still counts once, and a miss is never lost.</li>
 *   <li>D-16 holds throughout: a first answer given with the hint moves no mastery whatever follows; a second answer
 *       given with the hint counts the first miss only.</li>
 *   <li>The first answer stays the record (answer_records is append-only): the activity's score counts right first
 *       answers; the retry is kept beside it ({@link AnswerRetry}).</li>
 * </ul>
 */
@Service
public class AnswerRetryService {

    private final ActivityAttemptRepository attempts;
    private final AnswerRecordRepository answers;
    private final AnswerRetryRepository retries;
    private final QuestionVersionRepository versions;
    private final QuestionRepository questions;
    private final QuestionOptionRepository options;
    private final QuestionVersionSkillRepository skills;
    private final AnswerScoringService scoring;
    private final MasteryEngine mastery;
    private final RetryProperties properties;
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    public AnswerRetryService(ActivityAttemptRepository attempts, AnswerRecordRepository answers, AnswerRetryRepository retries,
                              QuestionVersionRepository versions, QuestionRepository questions, QuestionOptionRepository options,
                              QuestionVersionSkillRepository skills, AnswerScoringService scoring, MasteryEngine mastery,
                              RetryProperties properties) {
        this.attempts = attempts;
        this.answers = answers;
        this.retries = retries;
        this.versions = versions;
        this.questions = questions;
        this.options = options;
        this.skills = skills;
        this.scoring = scoring;
        this.mastery = mastery;
        this.properties = properties;
    }

    /** Whether a wrong first answer to this question gets one retry. */
    public boolean allows(Question question, QuestionVersion version) {
        return switch (question.getQuestionType()) {
            case SHORT_ANSWER, NUMERIC, SORTING -> true;
            case MULTIPLE_CHOICE, ORDERING -> options.findByQuestionVersionIdOrderByDisplayOrderAsc(version.getId()).size() >= 3;
            case MATCHING -> options.findByQuestionVersionIdOrderByDisplayOrderAsc(version.getId()).stream()
                    .filter(option -> option.getMatchSide() == MatchSide.LEFT).count() >= 3;
            case TRUE_FALSE, DRAWING -> false;
        };
    }

    /** The retries of these answers, by answer. */
    @Transactional(readOnly = true)
    public Map<UUID, AnswerRetry> retriesOf(Collection<UUID> answerIds) {
        if (answerIds.isEmpty()) return Map.of();
        return retries.findByAnswerRecordIdIn(answerIds).stream()
                .collect(Collectors.toMap(AnswerRetry::getAnswerRecordId, Function.identity()));
    }

    /** Whether this answer's retry is still open (offered, nothing after it yet). */
    public boolean isOpen(AnswerRecord answer, AnswerRetry retry) {
        return answer.isRetryOffered() && retry == null;
    }

    /**
     * The child's second answer. Scored by the server; the question's evidence is recorded now. Sending the same
     * second answer again (a lost reply) returns it unchanged; anything else once the retry is closed is refused (409).
     */
    @Transactional
    public AnswerRecord retry(UUID attemptId, UUID answerId, String submittedAnswer, Integer timeSpentSeconds, boolean hintUsed) {
        ActivityAttempt attempt = lockedInProgress(attemptId);
        AnswerRecord answer = answerOf(attempt, answerId);
        var existing = retries.findByAnswerRecordId(answerId);
        if (existing.isPresent()) {
            if (existing.get().isRetried() && submittedAnswer.equals(existing.get().getSubmittedAnswer())) return answer;
            throw new IllegalStateException("This question's second try is already over.");
        }
        if (!answer.isRetryOffered()) {
            throw new IllegalStateException("This question has no second try.");
        }
        QuestionVersion version = versions.findById(answer.getQuestionVersionId())
                .orElseThrow(() -> new ResourceNotFoundException("QuestionVersion", answer.getQuestionVersionId()));
        Question question = questions.findById(version.getQuestionId())
                .orElseThrow(() -> new ResourceNotFoundException("Question", version.getQuestionId()));
        boolean correct = scoring.score(question.getQuestionType(), version, submittedAnswer);
        lockChild(attempt.getChildId());
        retries.save(AnswerRetry.retried(answerId, submittedAnswer, correct, hintUsed, timeSpentSeconds));
        BigDecimal outcome = correct && !hintUsed ? properties.secondTryCredit() : BigDecimal.ZERO;
        recordEvidence(attempt, answer, outcome);
        return answer;
    }

    /** The child asks to see the answer instead: the retry closes as a miss. Repeating it changes nothing. */
    @Transactional
    public AnswerRecord reveal(UUID attemptId, UUID answerId) {
        ActivityAttempt attempt = lockedInProgress(attemptId);
        AnswerRecord answer = answerOf(attempt, answerId);
        if (!answer.isRetryOffered() || retries.findByAnswerRecordId(answerId).isPresent()) return answer;
        lockChild(attempt.getChildId());
        retries.save(AnswerRetry.declined(answerId));
        recordEvidence(attempt, answer, BigDecimal.ZERO);
        return answer;
    }

    /**
     * Closes every open retry in the attempt as a miss - when the child answers another question, finishes or stops -
     * in the caller's transaction (which holds the attempt), so no miss is ever left without its evidence.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void settleOpen(ActivityAttempt attempt) {
        lockChild(attempt.getChildId());
        List<AnswerRecord> offered = answers.findByActivityAttemptId(attempt.getId()).stream()
                .filter(AnswerRecord::isRetryOffered).toList();
        if (offered.isEmpty()) return;
        Map<UUID, AnswerRetry> closed = retriesOf(offered.stream().map(AnswerRecord::getId).toList());
        for (AnswerRecord answer : offered) {
            if (closed.containsKey(answer.getId())) continue;
            retries.save(AnswerRetry.declined(answer.getId()));
            recordEvidence(attempt, answer, BigDecimal.ZERO);
        }
    }

    /** The question's one piece of evidence, for each skill it is tagged with - none for a first answer given with the hint (D-16). */
    private void recordEvidence(ActivityAttempt attempt, AnswerRecord answer, BigDecimal outcome) {
        if (answer.isHintUsed()) return;
        List<QuestionVersionSkill> tagged = skills.findByQuestionVersionIdOrderByCreatedAtAscIdAsc(answer.getQuestionVersionId());
        for (QuestionVersionSkill skill : tagged) {
            mastery.recordEvidence(attempt.getChildId(), skill.getSkillId(), outcome, attempt.getId());
        }
    }

    /**
     * Evidence from a closing retry can be written by two requests at once (two activities finishing together, on the
     * same skill): they take the child's row lock first, so they run one after the other and each reads the other's
     * mastery - never a lost update.
     */
    private void lockChild(UUID childId) {
        jdbc.queryForList("select id from children where id = ? for update", UUID.class, childId);
    }

    private ActivityAttempt lockedInProgress(UUID attemptId) {
        ActivityAttempt attempt = attempts.findByIdForUpdate(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("ActivityAttempt", attemptId));
        if (attempt.getStatus() != ActivityAttemptStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot submit an answer for attempt %s, which is %s".formatted(attemptId, attempt.getStatus()));
        }
        return attempt;
    }

    private AnswerRecord answerOf(ActivityAttempt attempt, UUID answerId) {
        return answers.findById(answerId)
                .filter(answer -> answer.getActivityAttemptId().equals(attempt.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("AnswerRecord", answerId));
    }
}
