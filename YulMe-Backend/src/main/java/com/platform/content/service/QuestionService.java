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
     * needs at least 2 options with exactly one marked correct; MATCHING needs at
     * least 2 options (correctness is pairing-specific, not a single-option flag,
     * so not checked further here); SHORT_ANSWER/NUMERIC/TRUE_FALSE rely on
     * correct_answer directly and are checked for its presence instead.
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
            case MATCHING -> {
                if (listOptions(version.getId()).size() < 2) {
                    throw new IncompleteQuestionException(
                            "MATCHING question version %s needs at least 2 options".formatted(version.getId()));
                }
            }
            case TRUE_FALSE, SHORT_ANSWER, NUMERIC -> {
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
