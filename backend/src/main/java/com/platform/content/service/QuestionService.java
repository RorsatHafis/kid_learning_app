package com.platform.content.service;

import com.platform.common.entity.PublicationStatus;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.content.entity.Question;
import com.platform.content.entity.MatchSide;
import com.platform.content.entity.QuestionOption;
import com.platform.content.entity.QuestionVersionSkill;
import com.platform.content.entity.QuestionType;
import com.platform.content.entity.QuestionVersion;
import com.platform.content.exception.IncompleteQuestionException;
import com.platform.content.repository.QuestionOptionRepository;
import com.platform.content.repository.QuestionRepository;
import com.platform.content.repository.QuestionVersionSkillRepository;
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

/**
 * Questions and their versions: creating, revising drafts and their options, media, hint and explanation, tagging
 * skills, and publishing after ContentValidator's checks. A published version is never edited.
 */
@Service
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final QuestionVersionRepository questionVersionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final QuestionVersionSkillRepository versionSkillRepository;
    private final SkillRepository skillRepository;
    private final ContentValidator contentValidator;

    private final QuestionTranslations translations;

    public QuestionService(QuestionRepository questionRepository, QuestionVersionRepository questionVersionRepository,
                            QuestionOptionRepository questionOptionRepository,
                            QuestionVersionSkillRepository versionSkillRepository, SkillRepository skillRepository,
                            ContentValidator contentValidator, QuestionTranslations translations) {
        this.translations = translations;
        this.questionRepository = questionRepository;
        this.questionVersionRepository = questionVersionRepository;
        this.questionOptionRepository = questionOptionRepository;
        this.versionSkillRepository = versionSkillRepository;
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
        java.util.Optional<QuestionVersion> previous = questionVersionRepository.findTopByQuestionIdOrderByVersionNumberDesc(questionId);
        int nextVersionNumber = previous.map(v -> v.getVersionNumber() + 1).orElse(1);
        QuestionVersion draft = questionVersionRepository.save(
                QuestionVersion.draft(questionId, nextVersionNumber, prompt, correctAnswer, difficultyLevel));
        // A new version starts out evidencing what the last one did; the draft is where that can change (PD-08).
        previous.ifPresent(source -> {
            for (QuestionVersionSkill tag : versionSkillRepository.findByQuestionVersionIdOrderByCreatedAtAscIdAsc(source.getId())) {
                versionSkillRepository.save(QuestionVersionSkill.create(draft.getId(), tag.getSkillId()));
            }
        });
        return draft;
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

    /** The options of many versions in one query, each version's in display order. */
    @Transactional(readOnly = true)
    public List<QuestionOption> listOptions(java.util.Collection<UUID> questionVersionIds) {
        return questionOptionRepository.findByQuestionVersionIdInOrderByDisplayOrderAsc(questionVersionIds);
    }

    /**
     * Publishes a DRAFT question version, after validating it's actually complete
     * enough to show a learner - this is the "prevent incomplete content from
     * becoming learner-visible" check called for in Section 13 of the master
     * prompt, enforced here rather than left as an aspiration. MULTIPLE_CHOICE
     * needs at least 2 options with exactly one marked correct; MATCHING needs 2-6
     * complete pairs with distinct labels on each side; TRUE_FALSE and NUMERIC need a correct_answer in
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

    /**
     * PD-08: the skills a version's answers are evidence for are part of the version. Tags change only
     * on a DRAFT; an answer to a published version always counts for the skills it was published with
     * (V44 also enforces it). Idempotent.
     */
    @Transactional
    public QuestionVersionSkill tagSkill(UUID questionVersionId, UUID skillId) {
        QuestionVersion version = getVersion(questionVersionId);
        requireDraft(version);
        if (!skillRepository.existsById(skillId)) {
            throw new ResourceNotFoundException("Skill", skillId);
        }
        return versionSkillRepository.findByQuestionVersionIdAndSkillId(questionVersionId, skillId)
                .orElseGet(() -> versionSkillRepository.save(QuestionVersionSkill.create(questionVersionId, skillId)));
    }

    @Transactional
    public void untagSkill(UUID questionVersionId, UUID skillId) {
        requireDraft(getVersion(questionVersionId));
        versionSkillRepository.findByQuestionVersionIdAndSkillId(questionVersionId, skillId).ifPresent(versionSkillRepository::delete);
    }

    @Transactional
    public QuestionVersion reviseDraft(UUID questionVersionId, String prompt, String correctAnswer, Integer difficultyLevel) {
        QuestionVersion version = getVersion(questionVersionId);
        version.reviseDraft(prompt, correctAnswer, difficultyLevel);
        return questionVersionRepository.saveAndFlush(version);
    }

    /** An answer choice, with an optional picture and recording (X11). */
    public record OptionDraft(String label, boolean correct, UUID imageAssetId, UUID audioAssetId) {
        public OptionDraft(String label, boolean correct) {
            this(label, correct, null, null);
        }
    }

    public record MatchPairDraft(String left, String right, UUID leftImageAssetId, UUID rightImageAssetId) {
        public MatchPairDraft(String left, String right) {
            this(left, right, null, null);
        }
    }

    /** SORTING: a group the child sorts into, with an optional picture. */
    public record SortGroupDraft(String label, UUID imageAssetId) {
    }

    /** SORTING: an item and the index (in the draft's groups) of the group it belongs to. */
    public record SortItemDraft(String label, int group, UUID imageAssetId, UUID audioAssetId) {
    }

    /** Replaces a SORTING draft's groups and items: group i is number i; every option of the draft is replaced. */
    @Transactional
    public List<QuestionOption> replaceSorting(UUID questionVersionId, List<SortGroupDraft> groups, List<SortItemDraft> items) {
        requireDraft(getVersion(questionVersionId));
        questionOptionRepository.deleteByQuestionVersionId(questionVersionId);
        questionOptionRepository.flush();
        int order = 0;
        for (int i = 0; i < groups.size(); i++) {
            questionOptionRepository.save(QuestionOption.sortOption(questionVersionId, groups.get(i).label().strip(),
                    com.platform.content.entity.SortRole.GROUP, i, order++).withMedia(groups.get(i).imageAssetId(), null));
        }
        for (SortItemDraft item : items) {
            questionOptionRepository.save(QuestionOption.sortOption(questionVersionId, item.label().strip(),
                    com.platform.content.entity.SortRole.ITEM, item.group(), order++).withMedia(item.imageAssetId(), item.audioAssetId()));
        }
        return listOptions(questionVersionId);
    }

    /** ORDERING: one of the things to put in order; drafts are given in the correct order. */
    public record OrderItemDraft(String label, UUID imageAssetId, UUID audioAssetId) {
    }

    /** Replaces an ORDERING draft's items: their order here is the answer. Replaces every option of the draft. */
    @Transactional
    public List<QuestionOption> replaceOrderItems(UUID questionVersionId, List<OrderItemDraft> items) {
        requireDraft(getVersion(questionVersionId));
        questionOptionRepository.deleteByQuestionVersionId(questionVersionId);
        questionOptionRepository.flush();
        for (int i = 0; i < items.size(); i++) {
            questionOptionRepository.save(QuestionOption.create(questionVersionId, items.get(i).label().strip(), false, i)
                    .withMedia(items.get(i).imageAssetId(), items.get(i).audioAssetId()));
        }
        return listOptions(questionVersionId);
    }

    /** A draft's prompt picture and reading (X11); null removes one. */
    @Transactional
    public QuestionVersion setPromptMedia(UUID questionVersionId, UUID imageAssetId, String imageDescription, UUID audioAssetId) {
        QuestionVersion version = getVersion(questionVersionId);
        version.setPromptMedia(imageAssetId, imageDescription, audioAssetId);
        return questionVersionRepository.saveAndFlush(version);
    }

    /** A draft's hint and explanation (Y41); blank removes one. */
    @Transactional
    public QuestionVersion setGuidance(UUID questionVersionId, String hint, String explanation) {
        QuestionVersion version = getVersion(questionVersionId);
        version.setGuidance(hint, explanation);
        return questionVersionRepository.saveAndFlush(version);
    }

    /**
     * Replaces a MATCHING draft's pairs: pair i is the left item {@code left} and its match
     * {@code right}. Replaces every option of the draft.
     */
    @Transactional
    public List<QuestionOption> replaceMatchPairs(UUID questionVersionId, List<MatchPairDraft> pairs) {
        requireDraft(getVersion(questionVersionId));
        questionOptionRepository.deleteByQuestionVersionId(questionVersionId);
        questionOptionRepository.flush();
        for (int i = 0; i < pairs.size(); i++) {
            questionOptionRepository.save(QuestionOption.matchItem(questionVersionId, pairs.get(i).left().strip(), MatchSide.LEFT, i, i)
                    .withMedia(pairs.get(i).leftImageAssetId(), null));
            questionOptionRepository.save(QuestionOption.matchItem(questionVersionId, pairs.get(i).right().strip(), MatchSide.RIGHT, i, i)
                    .withMedia(pairs.get(i).rightImageAssetId(), null));
        }
        return listOptions(questionVersionId);
    }

    /** Replaces a draft's answer choices, in the given order. */
    @Transactional
    public List<QuestionOption> replaceOptions(UUID questionVersionId, List<OptionDraft> options) {
        requireDraft(getVersion(questionVersionId));
        questionOptionRepository.deleteByQuestionVersionId(questionVersionId);
        questionOptionRepository.flush();
        for (int i = 0; i < options.size(); i++) {
            questionOptionRepository.save(QuestionOption.create(questionVersionId, options.get(i).label().strip(),
                    options.get(i).correct(), i).withMedia(options.get(i).imageAssetId(), options.get(i).audioAssetId()));
        }
        return listOptions(questionVersionId);
    }

    /**
     * The draft to edit next: the open draft if there is one, otherwise a new draft
     * copied from the latest version (prompt, its media, answer, difficulty, hint, explanation and choices), so
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
        if (latest.getPromptImageAssetId() != null || latest.getPromptAudioAssetId() != null) {
            draft.setPromptMedia(latest.getPromptImageAssetId(), latest.getPromptImageDescription(), latest.getPromptAudioAssetId());
            questionVersionRepository.saveAndFlush(draft);
        }
        if (latest.getHint() != null || latest.getExplanation() != null) {
            draft.setGuidance(latest.getHint(), latest.getExplanation());
            questionVersionRepository.saveAndFlush(draft);
        }
        List<QuestionOption> latestOptions = listOptions(latest.getId());
        for (QuestionOption option : latestOptions) {
            questionOptionRepository.save(option.copyFor(draft.getId()));
        }
        questionOptionRepository.flush();
        // Its Khmer comes along, each option's label to its copy.
        translations.copy(latest.getId(), latestOptions, draft.getId(), listOptions(draft.getId()));
        return draft;
    }

    /**
     * M5b: the first draft of {@code targetQuestionId} (a new question) made from {@code source} - prompt, answer,
     * difficulty, media, hint, explanation, choices and skills - with each media id passed through {@code media}
     * (the copier's own asset for the same file).
     */
    @Transactional
    public QuestionVersion copyAsDraft(QuestionVersion source, UUID targetQuestionId, java.util.function.UnaryOperator<UUID> media) {
        QuestionVersion draft = createDraftVersion(targetQuestionId, source.getPrompt(), source.getCorrectAnswer(),
                source.getDifficultyLevel());
        if (source.getPromptImageAssetId() != null || source.getPromptAudioAssetId() != null) {
            draft.setPromptMedia(media.apply(source.getPromptImageAssetId()), source.getPromptImageDescription(),
                    media.apply(source.getPromptAudioAssetId()));
        }
        if (source.getHint() != null || source.getExplanation() != null) {
            draft.setGuidance(source.getHint(), source.getExplanation());
        }
        questionVersionRepository.saveAndFlush(draft);
        List<QuestionOption> sourceOptions = listOptions(source.getId());
        for (QuestionOption option : sourceOptions) {
            questionOptionRepository.save(option.copyFor(draft.getId())
                    .withMedia(media.apply(option.getImageAssetId()), media.apply(option.getAudioAssetId())));
        }
        questionOptionRepository.flush();
        translations.copy(source.getId(), sourceOptions, draft.getId(), listOptions(draft.getId()));
        for (UUID skillId : skillsOf(source.getId())) {
            tagSkill(draft.getId(), skillId);
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

    /** The skills answers to a version are evidence for, in the order they were tagged. */
    @Transactional(readOnly = true)
    public List<UUID> skillsOf(UUID questionVersionId) {
        return versionSkillRepository.findByQuestionVersionIdOrderByCreatedAtAscIdAsc(questionVersionId).stream()
                .map(QuestionVersionSkill::getSkillId).toList();
    }

}
