package com.platform.content.service;

import com.platform.common.entity.PublicationStatus;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.content.entity.Question;
import com.platform.content.entity.QuestionOption;
import com.platform.content.entity.QuestionSkill;
import com.platform.content.entity.QuestionType;
import com.platform.content.entity.QuestionVersion;
import com.platform.content.exception.IncompleteQuestionException;
import com.platform.content.repository.QuestionOptionRepository;
import com.platform.content.repository.QuestionRepository;
import com.platform.content.repository.QuestionSkillRepository;
import com.platform.content.repository.QuestionVersionRepository;
import com.platform.knowledge.repository.SkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final QuestionVersionRepository questionVersionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final QuestionSkillRepository questionSkillRepository;
    private final SkillRepository skillRepository;

    public QuestionService(QuestionRepository questionRepository, QuestionVersionRepository questionVersionRepository,
                            QuestionOptionRepository questionOptionRepository,
                            QuestionSkillRepository questionSkillRepository, SkillRepository skillRepository) {
        this.questionRepository = questionRepository;
        this.questionVersionRepository = questionVersionRepository;
        this.questionOptionRepository = questionOptionRepository;
        this.questionSkillRepository = questionSkillRepository;
        this.skillRepository = skillRepository;
    }

    @Transactional
    public Question createQuestion(UUID subjectId, QuestionType questionType) {
        return questionRepository.save(Question.create(subjectId, questionType));
    }

    @Transactional
    public Question createOwnedQuestion(UUID subjectId, QuestionType questionType, UUID ownerAccountId) {
        return questionRepository.save(Question.create(subjectId, questionType, ownerAccountId));
    }

    @Transactional(readOnly = true)
    public Question getQuestion(UUID id) {
        return questionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Question", id));
    }

    @Transactional
    public QuestionVersion createDraftVersion(UUID questionId, String prompt, String correctAnswer, Integer difficultyLevel) {
        if (!questionRepository.existsById(questionId)) {
            throw new ResourceNotFoundException("Question", questionId);
        }
        int nextVersionNumber = questionVersionRepository.findTopByQuestionIdOrderByVersionNumberDesc(questionId)
                .map(previous -> previous.getVersionNumber() + 1)
                .orElse(1);
        return questionVersionRepository.save(
                QuestionVersion.draft(questionId, nextVersionNumber, prompt, correctAnswer, difficultyLevel));
    }

    @Transactional(readOnly = true)
    public QuestionVersion getVersion(UUID questionVersionId) {
        return questionVersionRepository.findById(questionVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("QuestionVersion", questionVersionId));
    }

    /**
     * Battle 3 (content read-back integrity): the accessor learner-facing read
     * paths must use instead of {@link #getVersion} - mirrors {@code
     * ActivityService.getLearnerVisibleVersionById} exactly, including deliberately
     * exposing an unpublished/nonexistent id identically (same exception, same
     * message shape): a learner-facing caller has no legitimate reason to learn
     * that an id it holds refers to a real-but-not-yet-published question. {@link
     * ActivityService#publish} now refuses to publish an ActivityVersion containing
     * an unpublished question version, so this should never actually reject a
     * question reached through a properly-published ActivityVersion going forward -
     * this is the belt to that suspenders, for any data that predates that check or
     * any other path that reaches a QuestionVersion id without going through it.
     *
     * <p>Battle 3R: a RETIRED version is served too. It was superseded, not
     * withdrawn, and activity versions keep pinning it: publishing a new version of
     * a question must not make every activity that pins the old one unreadable
     * (L4). Only DRAFT stays hidden - see {@link
     * com.platform.common.entity.PublishableVersion#wasPublished()}.
     */
    @Transactional(readOnly = true)
    public QuestionVersion getLearnerVisibleVersionById(UUID questionVersionId) {
        QuestionVersion version = getVersion(questionVersionId);
        if (!version.wasPublished()) {
            throw new ResourceNotFoundException("Published QuestionVersion", questionVersionId);
        }
        return version;
    }

    @Transactional(readOnly = true)
    public QuestionVersion getPublishedVersion(UUID questionId) {
        return questionVersionRepository
                .findFirstByQuestionIdAndStatusOrderByVersionNumberDesc(questionId, PublicationStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Published QuestionVersion for question", questionId));
    }

    @Transactional
    public QuestionOption addOption(UUID questionVersionId, String label, boolean correct, int displayOrder) {
        if (!questionVersionRepository.existsById(questionVersionId)) {
            throw new ResourceNotFoundException("QuestionVersion", questionVersionId);
        }
        return questionOptionRepository.save(QuestionOption.create(questionVersionId, label, correct, displayOrder));
    }

    @Transactional(readOnly = true)
    public List<QuestionOption> listOptions(UUID questionVersionId) {
        return questionOptionRepository.findByQuestionVersionIdOrderByDisplayOrderAsc(questionVersionId);
    }

    /**
     * Publishes a DRAFT question version, after validating it's actually complete
     * enough to show a learner - this is the "prevent incomplete content from
     * becoming learner-visible" check called for in Section 13 of the master
     * prompt, enforced here rather than left as an aspiration. MULTIPLE_CHOICE
     * needs at least 2 options with exactly one marked correct; MATCHING is refused
     * outright (not scorable yet); TRUE_FALSE and NUMERIC need a correct_answer in
     * the canonical format scoring uses ({@link CanonicalAnswers}); SHORT_ANSWER
     * needs a non-blank correct_answer.
     */
    @Transactional
    public QuestionVersion publish(UUID questionVersionId) {
        QuestionVersion version = getVersion(questionVersionId);
        Question question = getQuestion(version.getQuestionId());

        validateCompleteness(question.getQuestionType(), version);

        questionVersionRepository
                .findFirstByQuestionIdAndStatusOrderByVersionNumberDesc(version.getQuestionId(), PublicationStatus.PUBLISHED)
                .ifPresent(previouslyPublished -> {
                    previouslyPublished.retire();
                    questionVersionRepository.saveAndFlush(previouslyPublished);
                });

        version.publish(Instant.now());
        return questionVersionRepository.saveAndFlush(version);
    }

    private void validateCompleteness(QuestionType questionType, QuestionVersion version) {
        switch (questionType) {
            case MULTIPLE_CHOICE -> {
                List<QuestionOption> options = listOptions(version.getId());
                if (options.size() < 2) {
                    throw new IncompleteQuestionException(
                            "MULTIPLE_CHOICE question version %s needs at least 2 options, has %d"
                                    .formatted(version.getId(), options.size()));
                }
                long correctCount = options.stream().filter(QuestionOption::isCorrect).count();
                if (correctCount != 1) {
                    throw new IncompleteQuestionException(
                            "MULTIPLE_CHOICE question version %s must have exactly one correct option, has %d"
                                    .formatted(version.getId(), correctCount));
                }
            }
            // Battle 5A: MATCHING has no scoring implementation (AnswerScoringService throws
            // UnscorableAnswerException), so a published MATCHING question could never be
            // answered and would leave any activity containing it uncompletable. Refused
            // until pairing is modelled and scored, rather than published broken.
            case MATCHING -> throw new IncompleteQuestionException(
                    "MATCHING question version %s cannot be published: automated scoring for MATCHING "
                            .formatted(version.getId()) + "is not implemented yet");
            // TRUE_FALSE and NUMERIC must use the same canonical formats AnswerScoringService
            // scores against (CanonicalAnswers), so published content is always scorable.
            case TRUE_FALSE -> {
                if (CanonicalAnswers.parseTrueFalse(version.getCorrectAnswer()).isEmpty()) {
                    throw new IncompleteQuestionException(
                            "TRUE_FALSE question version %s needs a correct_answer of \"true\" or \"false\""
                                    .formatted(version.getId()));
                }
            }
            case NUMERIC -> {
                if (CanonicalAnswers.parseNumber(version.getCorrectAnswer()).isEmpty()) {
                    throw new IncompleteQuestionException(
                            "NUMERIC question version %s needs a numeric correct_answer".formatted(version.getId()));
                }
            }
            case SHORT_ANSWER -> {
                if (version.getCorrectAnswer() == null || version.getCorrectAnswer().isBlank()) {
                    throw new IncompleteQuestionException(
                            "%s question version %s needs a correct_answer".formatted(questionType, version.getId()));
                }
            }
        }
    }

    @Transactional
    public QuestionSkill tagSkill(UUID questionId, UUID skillId) {
        if (!questionRepository.existsById(questionId)) {
            throw new ResourceNotFoundException("Question", questionId);
        }
        if (!skillRepository.existsById(skillId)) {
            throw new ResourceNotFoundException("Skill", skillId);
        }
        return questionSkillRepository.save(QuestionSkill.create(questionId, skillId));
    }

    @Transactional(readOnly = true)
    public List<QuestionSkill> listSkills(UUID questionId) {
        return questionSkillRepository.findByQuestionId(questionId);
    }

}
