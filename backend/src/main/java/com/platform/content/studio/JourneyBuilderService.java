package com.platform.content.studio;

import com.platform.common.entity.CatalogStatus;
import com.platform.common.entity.PublicationStatus;
import com.platform.content.entity.ActivityVersion;
import com.platform.content.entity.QuestionVersionSkill;
import com.platform.content.repository.ActivityItemRepository;
import com.platform.content.repository.ActivityRepository;
import com.platform.content.repository.ActivityVersionSkillRepository;
import com.platform.content.repository.ActivityVersionRepository;
import com.platform.content.repository.QuestionVersionSkillRepository;
import com.platform.content.repository.QuestionVersionRepository;
import com.platform.content.service.ContentAccessGuard;
import com.platform.curriculum.entity.Curriculum;
import com.platform.curriculum.entity.CurriculumObjective;
import com.platform.curriculum.entity.CurriculumVersion;
import com.platform.curriculum.entity.LearningObjective;
import com.platform.curriculum.repository.LearningObjectiveRepository;
import com.platform.curriculum.service.CurriculumService;
import com.platform.knowledge.entity.LearningObjectiveSkill;
import com.platform.knowledge.entity.Skill;
import com.platform.knowledge.entity.SkillPrerequisite;
import com.platform.knowledge.repository.LearningObjectiveSkillRepository;
import com.platform.knowledge.repository.SkillPrerequisiteRepository;
import com.platform.knowledge.repository.SkillRepository;
import com.platform.learning.service.CurriculumPathPlanner;
import com.platform.learning.service.CurriculumPathPlanner.PlannedStep;
import com.platform.learning.service.PathContentScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The Studio's journey builder read model (PLATFORM-EXPANSION.md §5): a curriculum version as the
 * journey a child will meet. Chapters are the version's objectives in order; each chapter's steps
 * come from {@link CurriculumPathPlanner} - the same plan enrolment seeds a path from - so they are
 * exactly what a child starts with. For every skill it shows what backs it (published and draft
 * activities, questions that record evidence, difficulty range, prerequisites) and derives the gaps
 * that change what a child experiences, stated in terms of the rules that actually run: enrolment,
 * evidence per question skill, and the Adaptive Engine's reinforcement (first prerequisite, else the
 * same skill, never the activity just played) and challenge (a strictly harder activity). Nothing
 * here writes; publishing is still decided only by ContentValidator.
 */
@Service
public class JourneyBuilderService {

    public enum Severity { WARNING, TIP }

    public record JourneyIssue(String code, Severity severity, String message, UUID skillId) {
    }

    public record StepPlan(UUID activityId, UUID activityVersionId, String title, int versionNumber, Integer difficulty,
                           Integer estimatedSeconds, int questions, String placedBySkill) {
    }

    /** A prerequisite and where the journey teaches it: chapter position, or null when it isn't in this curriculum. */
    public record PrerequisiteRef(UUID skillId, String name, Integer chapter, boolean hasActivity) {
    }

    public record SkillCoverage(UUID skillId, String name, boolean archived, int publishedActivities, int draftOnlyActivities,
                                int evidenceQuestions, Integer minDifficulty, Integer maxDifficulty,
                                Integer firstTaughtInChapter, List<PrerequisiteRef> prerequisites) {
    }

    public record ChapterPlan(int position, UUID objectiveId, String code, String title, String description,
                              boolean otherSubject, int estimatedSeconds, int stepsWithoutDuration, List<StepPlan> steps,
                              List<SkillCoverage> skills, List<JourneyIssue> issues) {
    }

    public record Totals(int chapters, int steps, int skills, int estimatedSeconds, int stepsWithoutDuration,
                         int warnings, int tips) {
    }

    public record JourneyMap(UUID curriculumId, UUID curriculumVersionId, int versionNumber, PublicationStatus status,
                             boolean preview, Totals totals, List<ChapterPlan> chapters) {
    }

    private final ContentAccessGuard guard;
    private final CurriculumService curriculumService;
    private final CurriculumPathPlanner planner;
    private final PathContentScope scopes;
    private final LearningObjectiveRepository objectives;
    private final LearningObjectiveSkillRepository objectiveSkills;
    private final SkillRepository skills;
    private final SkillPrerequisiteRepository prerequisites;
    private final ActivityVersionSkillRepository activitySkills;
    private final ActivityRepository activities;
    private final ActivityVersionRepository activityVersions;
    private final ActivityItemRepository activityItems;
    private final QuestionVersionRepository questionVersions;
    private final QuestionVersionSkillRepository questionSkills;

    public JourneyBuilderService(ContentAccessGuard guard, CurriculumService curriculumService, CurriculumPathPlanner planner,
                                 PathContentScope scopes, LearningObjectiveRepository objectives,
                                 LearningObjectiveSkillRepository objectiveSkills, SkillRepository skills,
                                 SkillPrerequisiteRepository prerequisites, ActivityVersionSkillRepository activitySkills,
                                 ActivityRepository activities, ActivityVersionRepository activityVersions,
                                 ActivityItemRepository activityItems, QuestionVersionRepository questionVersions,
                                 QuestionVersionSkillRepository questionSkills) {
        this.guard = guard;
        this.curriculumService = curriculumService;
        this.planner = planner;
        this.scopes = scopes;
        this.objectives = objectives;
        this.objectiveSkills = objectiveSkills;
        this.skills = skills;
        this.prerequisites = prerequisites;
        this.activitySkills = activitySkills;
        this.activities = activities;
        this.activityVersions = activityVersions;
        this.activityItems = activityItems;
        this.questionVersions = questionVersions;
        this.questionSkills = questionSkills;
    }

    /** The journey of a saved version (draft or published). */
    @Transactional(readOnly = true)
    public JourneyMap journey(UUID accountId, UUID curriculumVersionId) {
        CurriculumVersion version = curriculumService.getVersion(curriculumVersionId);
        Curriculum curriculum = guard.requireCurriculumWrite(accountId, version.getCurriculumId());
        List<UUID> order = curriculumService.listObjectives(curriculumVersionId).stream()
                .map(CurriculumObjective::getLearningObjectiveId).toList();
        return build(curriculum, version, order, false);
    }

    /** The journey this version would have with the chapters in another order: nothing is saved. */
    @Transactional(readOnly = true)
    public JourneyMap preview(UUID accountId, UUID curriculumVersionId, List<UUID> objectiveIds) {
        CurriculumVersion version = curriculumService.getVersion(curriculumVersionId);
        Curriculum curriculum = guard.requireCurriculumWrite(accountId, version.getCurriculumId());
        if (objectiveIds.stream().distinct().count() != objectiveIds.size()) {
            throw new IllegalArgumentException("A chapter can appear only once");
        }
        for (UUID objectiveId : objectiveIds) {
            objectives.findById(objectiveId).orElseThrow(() -> new IllegalArgumentException("Unknown learning objective"));
        }
        return build(curriculum, version, objectiveIds, true);
    }

    // ------------------------------------------------------------------ assembly

    private JourneyMap build(Curriculum curriculum, CurriculumVersion version, List<UUID> order, boolean preview) {
        PathContentScope.Scope scope = new PathContentScope.Scope(curriculum.getOwnerAccountId());
        List<PlannedStep> plan = planner.plan(scope, order);
        Map<UUID, List<PlannedStep>> stepsByObjective = new LinkedHashMap<>();
        plan.forEach(step -> stepsByObjective.computeIfAbsent(step.objectiveId(), id -> new ArrayList<>()).add(step));

        // Where each skill is first taught: the first chapter (in order) that lists it.
        Map<UUID, List<UUID>> skillIdsByObjective = new LinkedHashMap<>();
        Map<UUID, Integer> firstChapterOfSkill = new HashMap<>();
        for (int i = 0; i < order.size(); i++) {
            List<UUID> skillIds = objectiveSkills.findByLearningObjectiveId(order.get(i)).stream()
                    .map(LearningObjectiveSkill::getSkillId).toList();
            skillIdsByObjective.put(order.get(i), skillIds);
            for (UUID skillId : skillIds) {
                firstChapterOfSkill.putIfAbsent(skillId, i + 1);
            }
        }

        Map<UUID, Skill> skillById = new HashMap<>();
        Map<UUID, Coverage> coverage = new HashMap<>();
        List<ChapterPlan> chapters = new ArrayList<>();
        int warnings = 0;
        int tips = 0;
        int totalSeconds = 0;
        int totalWithoutDuration = 0;

        for (int i = 0; i < order.size(); i++) {
            int position = i + 1;
            UUID objectiveId = order.get(i);
            LearningObjective objective = objectives.findById(objectiveId).orElseThrow();
            boolean otherSubject = !Objects.equals(objective.getSubjectId(), curriculum.getSubjectId());
            List<JourneyIssue> issues = new ArrayList<>();
            List<SkillCoverage> skillViews = new ArrayList<>();
            List<UUID> chapterSkillIds = skillIdsByObjective.get(objectiveId);

            if (otherSubject) {
                issues.add(new JourneyIssue("OBJECTIVE_OTHER_SUBJECT", Severity.WARNING,
                        "This objective belongs to another subject; the curriculum can't be published with it.", null));
            }
            if (chapterSkillIds.isEmpty()) {
                issues.add(new JourneyIssue("CHAPTER_NO_SKILLS", Severity.WARNING,
                        "No skills are linked to this objective, so it adds nothing to a child's journey. Link skills to it in Skills & objectives.",
                        null));
            }

            for (UUID skillId : chapterSkillIds) {
                Skill skill = skillById.computeIfAbsent(skillId, id -> skills.findById(id).orElseThrow());
                Coverage c = coverage.computeIfAbsent(skillId, id -> coverageOf(id, scope));
                Integer firstTaught = firstChapterOfSkill.get(skillId);
                List<PrerequisiteRef> prereqs = new ArrayList<>();
                for (SkillPrerequisite link : prerequisites.findBySkillId(skillId)) {
                    UUID prereqId = link.getPrerequisiteSkillId();
                    Skill prereq = skillById.computeIfAbsent(prereqId, id -> skills.findById(id).orElseThrow());
                    Coverage pc = coverage.computeIfAbsent(prereqId, id -> coverageOf(id, scope));
                    prereqs.add(new PrerequisiteRef(prereqId, prereq.getName(), firstChapterOfSkill.get(prereqId), pc.published > 0));
                }
                skillViews.add(new SkillCoverage(skillId, skill.getName(), skill.getStatus() != CatalogStatus.ACTIVE, c.published,
                        c.draftOnly, c.evidenceQuestions, c.minDifficulty, c.maxDifficulty, firstTaught, prereqs));
                if (firstTaught != null && firstTaught < position) {
                    // Its activities were placed where it was first taught; nothing else to say about it here.
                    issues.add(new JourneyIssue("SKILL_REPEATED", Severity.TIP,
                            "\"%s\" is already taught in chapter %d, where its activities were placed.".formatted(skill.getName(), firstTaught),
                            skillId));
                    continue;
                }
                issues.addAll(skillIssues(skill, c, position, prereqs));
            }

            List<PlannedStep> planned = stepsByObjective.getOrDefault(objectiveId, List.of());
            // An empty chapter whose skills have no activity is already explained skill by skill; one whose
            // skills' activities were all placed earlier isn't, and looks fine skill by skill.
            if (!chapterSkillIds.isEmpty() && planned.isEmpty()
                    && chapterSkillIds.stream().anyMatch(id -> coverage.get(id).published > 0)) {
                issues.add(0, new JourneyIssue("CHAPTER_NO_STEPS", Severity.WARNING,
                        "Every published activity for this chapter's skills is already a step in an earlier chapter, so it has nothing new for children.",
                        null));
            }
            List<StepPlan> steps = new ArrayList<>();
            int seconds = 0;
            int withoutDuration = 0;
            for (PlannedStep step : planned) {
                ActivityVersion v = step.version();
                Integer duration = v.getEstimatedDurationSeconds();
                if (duration == null) {
                    withoutDuration++;
                } else {
                    seconds += duration;
                }
                steps.add(new StepPlan(step.activityId(), v.getId(), activities.findById(step.activityId()).orElseThrow().getTitle(),
                        v.getVersionNumber(), v.getDifficultyLevel(), duration,
                        activityItems.findByActivityVersionIdOrderBySequenceOrderAsc(v.getId()).size(),
                        skillById.computeIfAbsent(step.skillId(), id -> skills.findById(id).orElseThrow()).getName()));
            }
            warnings += (int) issues.stream().filter(issue -> issue.severity() == Severity.WARNING).count();
            tips += (int) issues.stream().filter(issue -> issue.severity() == Severity.TIP).count();
            totalSeconds += seconds;
            totalWithoutDuration += withoutDuration;
            chapters.add(new ChapterPlan(position, objectiveId, objective.getCode(), objective.getTitle(), objective.getDescription(),
                    otherSubject, seconds, withoutDuration, steps, skillViews, issues));
        }

        Totals totals = new Totals(chapters.size(), plan.size(), firstChapterOfSkill.size(), totalSeconds, totalWithoutDuration,
                warnings, tips);
        return new JourneyMap(curriculum.getId(), version.getId(), version.getVersionNumber(), version.getStatus(), preview,
                totals, chapters);
    }

    private List<JourneyIssue> skillIssues(Skill skill, Coverage c, int position, List<PrerequisiteRef> prereqs) {
        List<JourneyIssue> issues = new ArrayList<>();
        String name = skill.getName();
        if (skill.getStatus() != CatalogStatus.ACTIVE) {
            issues.add(new JourneyIssue("SKILL_ARCHIVED", Severity.WARNING, "\"%s\" is archived.".formatted(name), skill.getId()));
        }
        if (c.published == 0) {
            issues.add(new JourneyIssue("SKILL_NO_ACTIVITY", Severity.WARNING,
                    "No published activity practises \"%s\" yet%s, so children won't practise it.".formatted(name,
                            c.draftOnly > 0 ? " (%d in draft)".formatted(c.draftOnly) : ""), skill.getId()));
        } else {
            if (c.evidenceQuestions == 0) {
                issues.add(new JourneyIssue("SKILL_NO_EVIDENCE", Severity.WARNING,
                        "Children practise \"%s\", but no question in its activities is tagged with it, so their answers never count as evidence for it and its standing can't change."
                                .formatted(name), skill.getId()));
            }
            boolean harderExists = c.minDifficulty != null && c.maxDifficulty != null && c.maxDifficulty > c.minDifficulty;
            if (c.published == 1) {
                issues.add(new JourneyIssue("SKILL_ONE_ACTIVITY", Severity.TIP, (prereqs.isEmpty()
                        ? "Only one activity practises \"%s\": a child who struggles can't be given different practice, and a child doing well can't be moved on to something harder."
                        : "Only one activity practises \"%s\": a child doing well can't be moved on to something harder.").formatted(name),
                        skill.getId()));
            } else if (!harderExists) {
                issues.add(new JourneyIssue("SKILL_NO_HARDER_STEP", Severity.TIP,
                        "\"%s\"'s activities all have the same difficulty (or none set), so a child doing well can't be moved on to something harder."
                                .formatted(name), skill.getId()));
            }
        }
        for (int i = 0; i < prereqs.size(); i++) {
            PrerequisiteRef prereq = prereqs.get(i);
            if (prereq.chapter() != null && prereq.chapter() > position) {
                issues.add(new JourneyIssue("PREREQUISITE_LATER", Severity.WARNING,
                        "\"%s\" builds on \"%s\", which comes later, in chapter %d.".formatted(name, prereq.name(), prereq.chapter()),
                        skill.getId()));
            }
            // Reinforcement goes to the first prerequisite recorded (AdaptiveEngine).
            if (i == 0 && !prereq.hasActivity()) {
                issues.add(new JourneyIssue("PREREQUISITE_NO_ACTIVITY", Severity.WARNING,
                        "When a child struggles with \"%s\", the platform reinforces \"%s\" first, which has no published activity, so no practice can be placed."
                                .formatted(name, prereq.name()), skill.getId()));
            }
        }
        return issues;
    }

    /** What backs a skill within the curriculum's scope. */
    private static final class Coverage {
        int published;
        int draftOnly;
        int evidenceQuestions;
        Integer minDifficulty;
        Integer maxDifficulty;
    }

    /**
     * PD-08: coverage by the versions' own tags. "Published" counts activities whose published version
     * practises the skill; "draft only" counts activities whose open draft practises it but whose
     * published version (if any) doesn't - a new activity, or a retag waiting to be published.
     */
    private Coverage coverageOf(UUID skillId, PathContentScope.Scope scope) {
        Coverage c = new Coverage();
        java.util.Set<UUID> evidenceQuestions = new java.util.HashSet<>();
        java.util.Set<UUID> publishedActivities = new java.util.HashSet<>();
        java.util.Set<UUID> draftedActivities = new java.util.HashSet<>();
        for (ActivityVersion version : activitySkills.findAllPractising(skillId)) {
            if (!scopes.admitsActivity(scope, version.getActivityId())) {
                continue;
            }
            if (version.getStatus() == PublicationStatus.DRAFT) {
                draftedActivities.add(version.getActivityId());
                continue;
            }
            if (version.getStatus() != PublicationStatus.PUBLISHED || !publishedActivities.add(version.getActivityId())) {
                continue;
            }
            Integer difficulty = version.getDifficultyLevel();
            if (difficulty != null) {
                c.minDifficulty = c.minDifficulty == null ? difficulty : Math.min(c.minDifficulty, difficulty);
                c.maxDifficulty = c.maxDifficulty == null ? difficulty : Math.max(c.maxDifficulty, difficulty);
            }
            for (var item : activityItems.findByActivityVersionIdOrderBySequenceOrderAsc(version.getId())) {
                boolean tagged = questionSkills.findByQuestionVersionIdOrderByCreatedAtAscIdAsc(item.getQuestionVersionId()).stream()
                        .map(QuestionVersionSkill::getSkillId).anyMatch(skillId::equals);
                if (tagged) {
                    questionVersions.findById(item.getQuestionVersionId()).ifPresent(qv -> evidenceQuestions.add(qv.getQuestionId()));
                }
            }
        }
        draftedActivities.removeAll(publishedActivities);
        c.published = publishedActivities.size();
        c.draftOnly = draftedActivities.size();
        c.evidenceQuestions = evidenceQuestions.size();
        return c;
    }
}
