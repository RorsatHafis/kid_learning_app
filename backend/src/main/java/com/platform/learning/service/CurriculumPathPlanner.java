package com.platform.learning.service;

import com.platform.common.entity.PublicationStatus;
import com.platform.content.entity.ActivityVersion;
import com.platform.content.repository.ActivityVersionSkillRepository;
import com.platform.content.repository.ActivityVersionRepository;
import com.platform.curriculum.entity.CurriculumObjective;
import com.platform.curriculum.service.CurriculumService;
import com.platform.knowledge.entity.LearningObjectiveSkill;
import com.platform.knowledge.service.LearningObjectiveSkillService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The curriculum steps a child starts a journey with, in order: for each objective in the version's
 * order, for each of its skills, each published activity version that practises that skill (by its
 * own tags, PD-08) and that the curriculum's scope admits (N7), once per activity. The one definition of it: enrolment seeds a
 * path from it, and the Studio's journey builder shows it, so what an author sees is what a child gets.
 */
@Service
public class CurriculumPathPlanner {

    private final CurriculumService curriculumService;
    private final LearningObjectiveSkillService objectiveSkills;
    private final ActivityVersionSkillRepository activitySkills;
    private final ActivityVersionRepository activityVersions;
    private final PathContentScope pathContentScope;

    public CurriculumPathPlanner(CurriculumService curriculumService, LearningObjectiveSkillService objectiveSkills,
                                 ActivityVersionSkillRepository activitySkills, ActivityVersionRepository activityVersions,
                                 PathContentScope pathContentScope) {
        this.curriculumService = curriculumService;
        this.objectiveSkills = objectiveSkills;
        this.activitySkills = activitySkills;
        this.activityVersions = activityVersions;
        this.pathContentScope = pathContentScope;
    }

    /** One planned step: the activity's published version, and the objective and skill that placed it. */
    public record PlannedStep(UUID activityId, ActivityVersion version, UUID objectiveId, UUID skillId) {
    }

    /** The plan for a curriculum version as saved. */
    @Transactional(readOnly = true)
    public List<PlannedStep> plan(UUID curriculumVersionId) {
        List<UUID> objectiveIds = curriculumService.listObjectives(curriculumVersionId).stream()
                .map(CurriculumObjective::getLearningObjectiveId).toList();
        return plan(pathContentScope.forCurriculumVersion(curriculumVersionId), objectiveIds);
    }

    /** The plan for objectives in a given order (a saved version's, or an author's unsaved one), within a scope. */
    @Transactional(readOnly = true)
    public List<PlannedStep> plan(PathContentScope.Scope scope, List<UUID> objectiveIds) {
        List<PlannedStep> steps = new ArrayList<>();
        Set<UUID> placed = new LinkedHashSet<>();
        for (UUID objectiveId : objectiveIds) {
            for (LearningObjectiveSkill mapping : objectiveSkills.listSkillsForObjective(objectiveId)) {
                for (ActivityVersion version : activitySkills.findPublishedPractising(mapping.getSkillId())) {
                    UUID activityId = version.getActivityId();
                    if (!pathContentScope.admitsActivity(scope, activityId) || !placed.add(activityId)) {
                        continue;
                    }
                    steps.add(new PlannedStep(activityId, version, objectiveId, mapping.getSkillId()));
                }
            }
        }
        return steps;
    }
}
