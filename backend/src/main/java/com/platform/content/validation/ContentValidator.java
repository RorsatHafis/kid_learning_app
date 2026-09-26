package com.platform.content.validation;

import com.platform.common.entity.CatalogStatus;
import com.platform.common.entity.PublicationStatus;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.content.entity.Activity;
import com.platform.content.entity.ActivityItem;
import com.platform.content.entity.ActivitySkill;
import com.platform.content.entity.ActivityVersion;
import com.platform.content.entity.Question;
import com.platform.content.entity.QuestionOption;
import com.platform.content.entity.QuestionSkill;
import com.platform.content.entity.QuestionType;
import com.platform.content.entity.QuestionVersion;
import com.platform.content.repository.ActivityItemRepository;
import com.platform.content.repository.ActivityRepository;
import com.platform.content.repository.ActivitySkillRepository;
import com.platform.content.repository.ActivityVersionRepository;
import com.platform.content.repository.QuestionOptionRepository;
import com.platform.content.repository.QuestionRepository;
import com.platform.content.repository.QuestionSkillRepository;
import com.platform.content.repository.QuestionVersionRepository;
import com.platform.content.service.ContentOwnership;
import com.platform.content.service.QuestionCompleteness;
import com.platform.content.validation.ValidationReport.Issue;
import com.platform.curriculum.entity.Curriculum;
import com.platform.curriculum.entity.CurriculumObjective;
import com.platform.curriculum.entity.CurriculumVersion;
import com.platform.curriculum.entity.LearningObjective;
import com.platform.curriculum.repository.CurriculumObjectiveRepository;
import com.platform.curriculum.repository.CurriculumRepository;
import com.platform.curriculum.repository.CurriculumVersionRepository;
import com.platform.curriculum.repository.LearningObjectiveRepository;
import com.platform.knowledge.entity.LearningObjectiveSkill;
import com.platform.knowledge.entity.Skill;
import com.platform.knowledge.repository.LearningObjectiveSkillRepository;
import com.platform.knowledge.repository.SkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Directive §19: validates a draft before it can reach learners - "do not allow
 * PUBLISH SUCCESS → learner cannot play". Each publish path ({@code QuestionService},
 * {@code ActivityService}, {@code CurriculumService}) refuses a version with errors;
 * the Content Studio shows the same report while the author works.
 *
 * <p>The rules follow how content is actually consumed:
 * <ul>
 *   <li>Evidence is recorded per skill tagged on the <em>question</em>
 *       ({@code AnswerSubmissionWriter}); a question with no skill is scored but teaches
 *       the platform nothing.</li>
 *   <li>Placement (enrollment seeding, the Adaptive Engine, Smart Review) selects
 *       activities by the skills tagged on the <em>activity</em>; an activity placed for
 *       a skill that none of its questions evidences can never move that skill's
 *       mastery, so the engine would keep choosing it.</li>
 *   <li>A path draws from platform content plus its own curriculum owner's (N7); an
 *       activity may only contain questions from that same scope.</li>
 *   <li>A curriculum whose skills have no published in-scope activity seeds an empty
 *       path: a family could choose it and their child would have nothing to do.</li>
 * </ul>
 *
 * <p>Reads repositories only, so every publishing service can depend on it.
 */
@Service
public class ContentValidator {

    private final QuestionRepository questions;
    private final QuestionVersionRepository questionVersions;
    private final QuestionOptionRepository questionOptions;
    private final QuestionSkillRepository questionSkills;
    private final ActivityRepository activities;
    private final ActivityVersionRepository activityVersions;
    private final ActivityItemRepository activityItems;
    private final ActivitySkillRepository activitySkills;
    private final SkillRepository skills;
    private final CurriculumRepository curricula;
    private final CurriculumVersionRepository curriculumVersions;
    private final CurriculumObjectiveRepository curriculumObjectives;
    private final LearningObjectiveRepository learningObjectives;
    private final LearningObjectiveSkillRepository objectiveSkills;
    private final ContentOwnership ownership;

    public ContentValidator(QuestionRepository questions, QuestionVersionRepository questionVersions,
                            QuestionOptionRepository questionOptions, QuestionSkillRepository questionSkills,
                            ActivityRepository activities, ActivityVersionRepository activityVersions,
                            ActivityItemRepository activityItems, ActivitySkillRepository activitySkills,
                            SkillRepository skills, CurriculumRepository curricula,
                            CurriculumVersionRepository curriculumVersions,
                            CurriculumObjectiveRepository curriculumObjectives,
                            LearningObjectiveRepository learningObjectives,
                            LearningObjectiveSkillRepository objectiveSkills, ContentOwnership ownership) {
        this.questions = questions;
        this.questionVersions = questionVersions;
        this.questionOptions = questionOptions;
        this.questionSkills = questionSkills;
        this.activities = activities;
        this.activityVersions = activityVersions;
        this.activityItems = activityItems;
        this.activitySkills = activitySkills;
        this.skills = skills;
        this.curricula = curricula;
        this.curriculumVersions = curriculumVersions;
        this.curriculumObjectives = curriculumObjectives;
        this.learningObjectives = learningObjectives;
        this.objectiveSkills = objectiveSkills;
        this.ownership = ownership;
    }

    // ------------------------------------------------------------------ questions

    @Transactional(readOnly = true)
    public ValidationReport questionVersion(UUID questionVersionId) {
        QuestionVersion version = questionVersions.findById(questionVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("QuestionVersion", questionVersionId));
        Question question = questions.findById(version.getQuestionId())
                .orElseThrow(() -> new ResourceNotFoundException("Question", version.getQuestionId()));
        List<Issue> errors = new ArrayList<>();
        List<Issue> warnings = new ArrayList<>();

        List<QuestionOption> options = questionOptions.findByQuestionVersionIdOrderByDisplayOrderAsc(questionVersionId);
        QuestionCompleteness.problem(question.getQuestionType(), version, options).ifPresent(errors::add);
        if (question.getQuestionType() == QuestionType.MULTIPLE_CHOICE) {
            Set<String> seen = new HashSet<>();
            for (QuestionOption option : options) {
                if (!seen.add(option.getLabel().strip().toLowerCase(Locale.ROOT))) {
                    errors.add(new Issue("CHOICES_DUPLICATE",
                            "Two answer choices read \"%s\"; a child couldn't tell them apart.".formatted(option.getLabel().strip())));
                    break;
                }
            }
        }

        List<Skill> tagged = skillsOf(questionSkills.findByQuestionId(question.getId()).stream()
                .map(QuestionSkill::getSkillId).toList());
        if (tagged.isEmpty()) {
            errors.add(new Issue("QUESTION_NO_SKILL",
                    "Tag at least one skill: answers are recorded as evidence for the question's skills, so without one "
                            + "the child's answers teach the platform nothing."));
        }
        checkSkills(tagged, question.getSubjectId(), errors);

        if (version.getDifficultyLevel() == null) {
            warnings.add(new Issue("NO_DIFFICULTY", "No difficulty is set."));
        }
        return new ValidationReport(errors, warnings);
    }

    // ------------------------------------------------------------------ activities

    @Transactional(readOnly = true)
    public ValidationReport activityVersion(UUID activityVersionId) {
        ActivityVersion version = activityVersions.findById(activityVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("ActivityVersion", activityVersionId));
        Activity activity = activities.findById(version.getActivityId())
                .orElseThrow(() -> new ResourceNotFoundException("Activity", version.getActivityId()));
        UUID owner = ownership.activityOwner(activity.getId()).orElse(null);
        List<Issue> errors = new ArrayList<>();
        List<Issue> warnings = new ArrayList<>();

        List<Skill> placedFor = skillsOf(activitySkills.findByActivityId(activity.getId()).stream()
                .map(ActivitySkill::getSkillId).toList());
        if (placedFor.isEmpty()) {
            errors.add(new Issue("ACTIVITY_NO_SKILL",
                    "Tag at least one skill: learning paths choose activities by skill, so without one it is never placed."));
        }
        checkSkills(placedFor, activity.getSubjectId(), errors);

        List<ActivityItem> items = activityItems.findByActivityVersionIdOrderBySequenceOrderAsc(activityVersionId);
        if (items.isEmpty()) {
            errors.add(new Issue("NO_QUESTIONS", "Add at least one question."));
        }
        Set<UUID> evidenced = new LinkedHashSet<>();
        for (ActivityItem item : items) {
            QuestionVersion questionVersion = questionVersions.findById(item.getQuestionVersionId()).orElse(null);
            Question question = questionVersion == null ? null : questions.findById(questionVersion.getQuestionId()).orElse(null);
            if (questionVersion == null || question == null) {
                errors.add(new Issue("QUESTION_MISSING", "Question %d no longer exists.".formatted(position(items, item))));
                continue;
            }
            String label = "Question %d".formatted(position(items, item));
            if (questionVersion.getStatus() != PublicationStatus.PUBLISHED) {
                errors.add(new Issue("QUESTION_NOT_PUBLISHED", label + (questionVersion.getStatus() == PublicationStatus.DRAFT
                        ? " is still a draft; publish it first."
                        : " uses a superseded version; use the question's current published version.")));
            }
            if (!ContentOwnership.usableBy(question.getOwnerAccountId(), owner)) {
                errors.add(new Issue("QUESTION_OUT_OF_SCOPE", label + " belongs to another author and can't be used here."));
            }
            List<UUID> questionSkillIds = questionSkills.findByQuestionId(question.getId()).stream()
                    .map(QuestionSkill::getSkillId).toList();
            if (questionSkillIds.isEmpty()) {
                errors.add(new Issue("QUESTION_NO_SKILL", label + " has no skill, so answering it records no evidence."));
            }
            evidenced.addAll(questionSkillIds);
        }

        for (Skill skill : placedFor) {
            if (!items.isEmpty() && !evidenced.contains(skill.getId())) {
                errors.add(new Issue("SKILL_WITHOUT_EVIDENCE",
                        "The activity is placed for \"%s\", but none of its questions records evidence for it, so playing it "
                                .formatted(skill.getName()) + "could never change that skill's progress."));
            }
        }
        Set<UUID> placedIds = new HashSet<>(placedFor.stream().map(Skill::getId).toList());
        for (Skill skill : skillsOf(List.copyOf(evidenced))) {
            if (!placedIds.contains(skill.getId())) {
                warnings.add(new Issue("EVIDENCE_OUTSIDE_ACTIVITY",
                        "A question records evidence for \"%s\", which this activity isn't tagged with; learning paths won't "
                                .formatted(skill.getName()) + "choose this activity to practise it."));
            }
        }
        if (!placedFor.isEmpty() && placedFor.stream().noneMatch(skill -> onAPublishedPath(skill.getId(), owner))) {
            warnings.add(new Issue("NOT_ON_ANY_PATH",
                    "None of the activity's skills is in a published curriculum it can be used by yet, so no child will be "
                            + "given it until one is."));
        }
        if (version.getDifficultyLevel() == null) {
            warnings.add(new Issue("NO_DIFFICULTY", "No difficulty is set; challenge and reinforcement choices use it."));
        }
        if (version.getEstimatedDurationSeconds() == null) {
            warnings.add(new Issue("NO_DURATION", "No estimated duration is set."));
        }
        return new ValidationReport(errors, warnings);
    }

    // ------------------------------------------------------------------ curricula

    @Transactional(readOnly = true)
    public ValidationReport curriculumVersion(UUID curriculumVersionId) {
        CurriculumVersion version = curriculumVersions.findById(curriculumVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("CurriculumVersion", curriculumVersionId));
        Curriculum curriculum = curricula.findById(version.getCurriculumId())
                .orElseThrow(() -> new ResourceNotFoundException("Curriculum", version.getCurriculumId()));
        List<Issue> errors = new ArrayList<>();
        List<Issue> warnings = new ArrayList<>();

        List<CurriculumObjective> entries = curriculumObjectives.findByCurriculumVersionIdOrderByDisplayOrderAsc(curriculumVersionId);
        if (entries.isEmpty()) {
            errors.add(new Issue("NO_OBJECTIVES", "Add at least one learning objective."));
        }
        Set<UUID> curriculumSkillIds = new LinkedHashSet<>();
        for (CurriculumObjective entry : entries) {
            Optional<LearningObjective> objective = learningObjectives.findById(entry.getLearningObjectiveId());
            if (objective.isEmpty()) {
                errors.add(new Issue("OBJECTIVE_MISSING", "An objective in this curriculum no longer exists."));
                continue;
            }
            String title = objective.get().getTitle();
            if (!Objects.equals(objective.get().getSubjectId(), curriculum.getSubjectId())) {
                errors.add(new Issue("OBJECTIVE_OTHER_SUBJECT", "\"%s\" belongs to a different subject.".formatted(title)));
            }
            List<UUID> skillIds = objectiveSkills.findByLearningObjectiveId(objective.get().getId()).stream()
                    .map(LearningObjectiveSkill::getSkillId).toList();
            if (skillIds.isEmpty()) {
                errors.add(new Issue("OBJECTIVE_NO_SKILL",
                        "\"%s\" has no skills, so it adds nothing to a child's path.".formatted(title)));
            }
            curriculumSkillIds.addAll(skillIds);
        }

        List<Skill> withoutActivity = new ArrayList<>();
        for (Skill skill : skillsOf(List.copyOf(curriculumSkillIds))) {
            if (!hasPublishedActivityInScope(skill.getId(), curriculum.getOwnerAccountId())) {
                withoutActivity.add(skill);
            }
        }
        if (!curriculumSkillIds.isEmpty() && withoutActivity.size() == curriculumSkillIds.size()) {
            errors.add(new Issue("NO_PLAYABLE_ACTIVITY",
                    "None of this curriculum's skills has a published activity yet, so an enrolled child would have "
                            + "nothing to do."));
        } else {
            for (Skill skill : withoutActivity) {
                warnings.add(new Issue("SKILL_NO_ACTIVITY",
                        "\"%s\" has no published activity yet; children won't practise it.".formatted(skill.getName())));
            }
        }
        return new ValidationReport(errors, warnings);
    }

    // ------------------------------------------------------------------ helpers

    private void checkSkills(List<Skill> tagged, UUID subjectId, List<Issue> errors) {
        for (Skill skill : tagged) {
            if (skill.getStatus() != CatalogStatus.ACTIVE) {
                errors.add(new Issue("SKILL_ARCHIVED", "\"%s\" is archived.".formatted(skill.getName())));
            }
            if (!Objects.equals(skill.getSubjectId(), subjectId)) {
                errors.add(new Issue("SKILL_OTHER_SUBJECT", "\"%s\" belongs to a different subject.".formatted(skill.getName())));
            }
        }
    }

    private List<Skill> skillsOf(List<UUID> skillIds) {
        return skills.findAllById(skillIds).stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .toList();
    }

    /** Mirrors enrollment seeding: an in-scope activity for the skill with a PUBLISHED version. */
    private boolean hasPublishedActivityInScope(UUID skillId, UUID curriculumOwner) {
        return activitySkills.findBySkillId(skillId).stream()
                .map(ActivitySkill::getActivityId)
                .filter(activityId -> ContentOwnership.usableBy(ownership.activityOwner(activityId).orElse(null), curriculumOwner))
                .anyMatch(activityId -> activityVersions
                        .findFirstByActivityIdAndStatusOrderByVersionNumberDesc(activityId, PublicationStatus.PUBLISHED)
                        .isPresent());
    }

    /** Whether a published curriculum that may use this owner's content includes the skill. */
    private boolean onAPublishedPath(UUID skillId, UUID activityOwner) {
        return objectiveSkills.findBySkillId(skillId).stream()
                .flatMap(link -> curriculumObjectives.findByLearningObjectiveId(link.getLearningObjectiveId()).stream())
                .map(entry -> curriculumVersions.findById(entry.getCurriculumVersionId()).orElse(null))
                .filter(Objects::nonNull)
                .filter(curriculumVersion -> curriculumVersion.getStatus() == PublicationStatus.PUBLISHED)
                .map(curriculumVersion -> curricula.findById(curriculumVersion.getCurriculumId()).orElse(null))
                .filter(Objects::nonNull)
                .anyMatch(curriculum -> ContentOwnership.usableBy(activityOwner, curriculum.getOwnerAccountId()));
    }

    private static int position(List<ActivityItem> items, ActivityItem item) {
        return items.indexOf(item) + 1;
    }

}
