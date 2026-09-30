package com.platform.school.service;

import com.platform.learner.service.EvidenceStandard.EvidenceLevel;
import com.platform.learner.service.EvidenceStandard.SkillStanding;
import com.platform.learning.service.LearningJourneyService.CurriculumRef;
import com.platform.learning.service.SkillProgressService.CurriculumProgress;
import com.platform.learning.service.SkillProgressService.ObjectiveProgress;
import com.platform.learning.service.SkillProgressService.ObjectiveStanding;
import com.platform.learning.service.SkillProgressService.SkillProgress;
import com.platform.school.service.SchoolLearningService.ClassCurriculum;
import com.platform.school.service.SchoolLearningService.ClassSnapshot;
import com.platform.school.service.SchoolLearningService.JourneyView;
import com.platform.school.service.SchoolLearningService.MemberRow;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** What the staff overview flags in a class: only standings the evidence supports, counted per child and per skill. */
class SchoolOverviewAttentionTests {

    private static final UUID CURRICULUM = UUID.randomUUID();
    private static final UUID ADD = UUID.randomUUID();
    private static final UUID COUNT = UUID.randomUUID();

    @Test
    void countsChildrenAndDistinctSkillsNeedingPracticeAndChildrenNotStarted() {
        UUID ana = UUID.randomUUID();
        UUID bo = UUID.randomUUID();
        UUID chan = UUID.randomUUID();
        Map<UUID, List<CurriculumProgress>> progress = new LinkedHashMap<>();
        progress.put(ana, List.of(progress(skill(ADD, SkillStanding.NEEDS_PRACTICE), skill(COUNT, SkillStanding.NEEDS_PRACTICE))));
        progress.put(bo, List.of(progress(skill(ADD, SkillStanding.NEEDS_PRACTICE), skill(COUNT, SkillStanding.SECURE))));
        // Too early to tell is not "needs practice": weak evidence is never presented as a need.
        progress.put(chan, List.of(progress(skill(ADD, SkillStanding.TOO_EARLY_TO_TELL))));
        ClassSnapshot snapshot = new ClassSnapshot(List.of(new ClassCurriculum(CURRICULUM, "Maths", "Maths")),
                List.of(member(ana, 3), member(bo, 1), member(chan, 0)), progress);

        SchoolOverviewService.Attention attention = SchoolOverviewService.attention(snapshot);

        assertThat(attention.childrenNeedingPractice()).isEqualTo(2);
        assertThat(attention.skillsNeedingPractice()).isEqualTo(2);
        assertThat(attention.childrenNotStarted()).isEqualTo(1);
    }

    @Test
    void aClassWithNothingAssignedHasNobodyNotStarted() {
        UUID ana = UUID.randomUUID();
        ClassSnapshot snapshot = new ClassSnapshot(List.of(), List.of(new MemberRow(ana, "Ana", Instant.now(), List.of())),
                Map.of(ana, List.of()));

        assertThat(SchoolOverviewService.attention(snapshot)).isEqualTo(new SchoolOverviewService.Attention(0, 0, 0));
    }

    private static MemberRow member(UUID childId, int completedSteps) {
        return new MemberRow(childId, "Child", Instant.now(),
                List.of(new JourneyView(CURRICULUM, "Maths", true, completedSteps, 6, null, 1, List.of())));
    }

    private static CurriculumProgress progress(SkillProgress... skills) {
        return new CurriculumProgress(UUID.randomUUID(), new CurriculumRef(CURRICULUM, "Maths", "MATHS", "Maths", "Junior"),
                List.of(new ObjectiveProgress(UUID.randomUUID(), "Add", null, ObjectiveStanding.IN_PROGRESS, skills.length,
                        skills.length, 0, List.of(skills))));
    }

    private static SkillProgress skill(UUID id, SkillStanding standing) {
        return new SkillProgress(id, "Skill", null, EvidenceLevel.SUFFICIENT, standing, 4, 1, Instant.now(), false, null);
    }
}
