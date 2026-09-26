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
import com.platform.content.validation.ContentValidationException;
import com.platform.content.validation.ContentValidator;
import com.platform.content.validation.ValidationReport;
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
    private final ContentValidator contentValidator;

    public QuestionService(QuestionRepository questionRepository, QuestionVersionRepository questionVersionRepository,
                            QuestionOptionRepository questionOptionRepository,
                            QuestionSkillRepository questionSkillRepository, SkillRepository skillRepository,
                            ContentValidator contentValidator) {
        this.questionRepository = questionRepository;
        this.questionVersionRepository = questionVersionRepository;
        this.questionOptionRepository = questionOptionRepository;
        this.questionSkillRepository = questionSkillRepository;
        this.skillRepository = skillRepository;
        this.contentValidator = contentValidator;
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
        ValidationReport report = contentValidator.questionVersion(questionVersionId);
        if (!report.publishable()) {
            throw new ContentValidationException("Question version " + questionVersionId, report);
        }

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
        QuestionCompleteness.problem(questionType, version, listOptions(version.getId())).ifPresent(issue -> {
            throw new IncompleteQuestionException("Question version %s: %s".formatted(version.getId(), issue.message()));
        });
    }

    /** Idempotent: tagging a skill the question already has returns the existing tag. */
    @Transactional
    public QuestionSkill tagSkill(UUID questionId, UUID skillId) {
        if (!questionRepository.existsById(questionId)) {
            throw new ResourceNotFoundException("Question", questionId);
        }
        if (!skillRepository.existsById(skillId)) {
            throw new ResourceNotFoundException("Skill", skillId);
        }
        return questionSkillRepository.findByQuestionIdAndSkillId(questionId, skillId)
                .orElseGet(() -> questionSkillRepository.save(QuestionSkill.create(questionId, skillId)));
    }

    /**
     * Skill tags belong to the question, not a version (PD-08): removing one also
     * changes which skill answers to already-published versions count towards from
     * now on. Evidence already recorded keeps its skill.
     */
    @Transactional
    public void untagSkill(UUID questionId, UUID skillId) {
        questionSkillRepository.findByQuestionIdAndSkillId(questionId, skillId).ifPresent(questionSkillRepository::delete);
    }

    @Transactional
    public QuestionVersion reviseDraft(UUID questionVersionId, String prompt, String correctAnswer, Integer difficultyLevel) {
        QuestionVersion version = getVersion(questionVersionId);
        version.reviseDraft(prompt, correctAnswer, difficultyLevel);
        return questionVersionRepository.saveAndFlush(version);
    }

    public record OptionDraft(String label, boolean correct) {
    }

    /** Replaces a draft's answer choices, in the given order. */
    @Transactional
    public List<QuestionOption> replaceOptions(UUID questionVersionId, List<OptionDraft> options) {
        requireDraft(getVersion(questionVersionId));
        questionOptionRepository.deleteByQuestionVersionId(questionVersionId);
        questionOptionRepository.flush();
        for (int i = 0; i < options.size(); i++) {
            questionOptionRepository.save(QuestionOption.create(questionVersionId, options.get(i).label().strip(),
                    options.get(i).correct(), i));
        }
        return listOptions(questionVersionId);
    }

    /**
     * The draft to edit next: the open draft if there is one, otherwise a new draft
     * copied from the latest version (prompt, answer, difficulty and choices), so
     * changing published content never means retyping it.
     */
    @Transactional
    public QuestionVersion openDraft(UUID questionId) {
        QuestionVersion latest = questionVersionRepository.findTopByQuestionIdOrderByVersionNumberDesc(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("QuestionVersion for question", questionId));
        if (latest.getStatus() == PublicationStatus.DRAFT) {
            return latest;
        }
        QuestionVersion draft = createDraftVersion(questionId, latest.getPrompt(), latest.getCorrectAnswer(),
                latest.getDifficultyLevel());
        for (QuestionOption option : listOptions(latest.getId())) {
            questionOptionRepository.save(QuestionOption.create(draft.getId(), option.getLabel(), option.isCorrect(),
                    option.getDisplayOrder()));
        }
        return draft;
    }

    @Transactional(readOnly = true)
    public List<QuestionVersion> listVersions(UUID questionId) {
        return questionVersionRepository.findByQuestionIdOrderByVersionNumberAsc(questionId);
    }

    private static void requireDraft(QuestionVersion version) {
        if (version.getStatus() != PublicationStatus.DRAFT) {
            throw new IllegalStateException("Only a DRAFT question version can be edited, %s is %s"
                    .formatted(version.getId(), version.getStatus()));
        }
    }

    @Transactional(readOnly = true)
    public List<QuestionSkill> listSkills(UUID questionId) {
        return questionSkillRepository.findByQuestionId(questionId);
    }

}
