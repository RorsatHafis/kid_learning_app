package com.platform.parent.service;

import com.platform.curriculum.entity.LearningObjective;
import com.platform.curriculum.service.LearningObjectiveService;
import com.platform.knowledge.entity.LearningObjectiveSkill;
import com.platform.knowledge.repository.LearningObjectiveSkillRepository;
import com.platform.knowledge.service.SkillService;
import com.platform.learner.MasteryProperties;
import com.platform.learner.entity.LearnerObjectiveState;
import com.platform.learner.entity.LearnerObjectiveStatus;
import com.platform.learner.entity.LearnerSkillState;
import com.platform.learner.repository.LearnerObjectiveStateRepository;
import com.platform.learner.repository.LearnerSkillHistoryRepository;
import com.platform.learner.repository.LearnerSkillStateRepository;
import com.platform.learner.service.MasteryEngine;
import com.platform.parent.entity.ParentInsight;
import com.platform.parent.entity.ParentInsightType;
import com.platform.parent.repository.ParentInsightRepository;
import com.platform.review.service.SmartReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Battle 5C: a parent-facing claim about an objective must be supported by the
 * evidence the child actually produced. One objective with 4 mapped skills (the
 * seeded shape); the REAL MasteryEngine builds the learner state from answers and
 * the REAL ParentInsightService interprets it - only persistence is in memory.
 *
 * "Not assessed" must never read as "failed", "failed" must never read as "not
 * started", and strong performance on some skills must never read as mastery of
 * the whole objective.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ObjectiveInsightTruthfulnessTests {

    private static final UUID CHILD = UUID.randomUUID();
    private static final UUID OBJECTIVE = UUID.randomUUID();
    private static final String OBJECTIVE_TITLE = "Addition Within 10";
    private static final List<UUID> SKILLS = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    @Mock private LearnerSkillStateRepository skillStateRepository;
    // D-17's "more than one day" rule is covered by the integration tests; here every skill's evidence spans two days.
    @Mock private com.platform.learner.service.LearningDays learningDays;
    @Mock private LearnerSkillHistoryRepository skillHistoryRepository;
    @Mock private LearnerObjectiveStateRepository objectiveStateRepository;
    @Mock private LearningObjectiveSkillRepository learningObjectiveSkillRepository;
    @Mock private LearningObjectiveService learningObjectiveService;
    @Mock private SmartReviewService smartReviewService;
    @Mock private SkillService skillService;
    @Mock private ParentInsightRepository parentInsightRepository;

    private final Map<UUID, LearnerSkillState> skillStates = new HashMap<>();
    private final AtomicReference<LearnerObjectiveState> objectiveState = new AtomicReference<>();
    private MasteryEngine masteryEngine;
    private ParentInsightService insights;

    @BeforeEach
    void setUp() {
        when(skillStateRepository.findByChildIdAndSkillId(eq(CHILD), any(UUID.class)))
                .thenAnswer(i -> Optional.ofNullable(skillStates.get(i.<UUID>getArgument(1))));
        when(skillStateRepository.save(any(LearnerSkillState.class))).thenAnswer(i -> {
            LearnerSkillState state = i.getArgument(0);
            skillStates.put(state.getSkillId(), state);
            return state;
        });
        when(objectiveStateRepository.findByChildIdAndLearningObjectiveId(CHILD, OBJECTIVE))
                .thenAnswer(i -> Optional.ofNullable(objectiveState.get()));
        when(objectiveStateRepository.save(any(LearnerObjectiveState.class))).thenAnswer(i -> {
            objectiveState.set(i.getArgument(0));
            return i.getArgument(0);
        });
        when(objectiveStateRepository.findByChildId(CHILD))
                .thenAnswer(i -> objectiveState.get() == null ? List.of() : List.of(objectiveState.get()));

        List<LearningObjectiveSkill> mappings = SKILLS.stream()
                .map(skill -> LearningObjectiveSkill.create(OBJECTIVE, skill)).toList();
        when(learningObjectiveSkillRepository.findByLearningObjectiveId(OBJECTIVE)).thenReturn(mappings);
        for (LearningObjectiveSkill mapping : mappings) {
            when(learningObjectiveSkillRepository.findBySkillId(mapping.getSkillId())).thenReturn(List.of(mapping));
        }

        LearningObjective objective = LearningObjective.create(UUID.randomUUID(), "OBJ", OBJECTIVE_TITLE, null);
        ReflectionTestUtils.setField(objective, "id", OBJECTIVE);
        when(learningObjectiveService.getById(OBJECTIVE)).thenReturn(objective);
        when(smartReviewService.listActive(CHILD)).thenReturn(List.of());
        when(parentInsightRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        masteryEngine = new MasteryEngine(skillStateRepository, skillHistoryRepository, objectiveStateRepository,
                learningObjectiveSkillRepository, new MasteryProperties(new BigDecimal("0.2"), 10, new BigDecimal("0.8")));
        when(learningDays.evidenceDays(CHILD)).thenAnswer(i -> SKILLS.stream()
                .collect(java.util.stream.Collectors.toMap(skill -> skill, skill -> 2)));
        insights = new ParentInsightService(objectiveStateRepository, learningObjectiveService, smartReviewService,
                skillService, parentInsightRepository, masteryEngine, learningDays);
    }

    @Test
    void noEvidenceMeansNoObjectiveStateAndNoClaim() {
        assertThat(objectiveState.get()).isNull();
        assertThat(insights.currentInsights(CHILD)).isEmpty();
    }

    @Test
    void oneCorrectAnswerIsNotYetEnoughToClaimAnything() {
        answer(0, true);

        assertThat(objectiveState.get().getStatus()).isEqualTo(LearnerObjectiveStatus.IN_PROGRESS);
        assertThat(objectiveState.get().getMasteryProbability()).isEqualByComparingTo("1.0000");
        assertThat(insights.currentInsights(CHILD)).isEmpty();
    }

    @Test
    void oneIncorrectAnswerStartsTheObjectiveButClaimsNothing() {
        answer(0, false);

        // Before 5C a 0 average left the objective NOT_STARTED - a wrong answer hid the child's engagement.
        assertThat(objectiveState.get().getStatus()).isEqualTo(LearnerObjectiveStatus.IN_PROGRESS);
        assertThat(insights.currentInsights(CHILD)).isEmpty();
    }

    @Test
    void twoOfTwoCorrectOrIncorrectIsStillBelowTheClaimThreshold() {
        answer(0, true, true);
        assertThat(insights.currentInsights(CHILD)).isEmpty();

        answer(1, false, false);
        assertThat(insights.currentInsights(CHILD)).isEmpty();
    }

    @Test
    void perfectEvidenceOnOneSkillIsGoodProgressNotWeaknessAndNotMastery() {
        answer(0, true, true, true); // three other mapped skills never practised

        LearnerObjectiveState state = objectiveState.get();
        assertThat(state.getMasteryProbability()).isEqualByComparingTo("1.0000"); // was 0.25 (zeros averaged in)
        assertThat(state.getStatus()).isEqualTo(LearnerObjectiveStatus.IN_PROGRESS); // not MASTERED: 1 of 4 observed

        ParentInsight insight = single(insights.currentInsights(CHILD));
        assertThat(insight.getInsightType()).isEqualTo(ParentInsightType.PROGRESS);
        assertThat(insight.getHeadline()).contains("making good progress");
        assertThat(insight.getSupportingData())
                .containsEntry("skillsMapped", 4)
                .containsEntry("skillsWithEvidence", 1)
                .containsEntry("skillsWithSufficientEvidence", 1);
    }

    @Test
    void poorEvidenceOnOneSkillIsWeaknessOnThatEvidence() {
        answer(0, false, false, false);

        assertThat(objectiveState.get().getStatus()).isEqualTo(LearnerObjectiveStatus.IN_PROGRESS);
        assertThat(single(insights.currentInsights(CHILD)).getInsightType()).isEqualTo(ParentInsightType.WEAKNESS);
    }

    @Test
    void mixedEvidenceOnOneSkillFollowsTheRecencyWeightedAccuracy() {
        answer(0, true, false, true);   // 0.6721
        assertThat(single(insights.currentInsights(CHILD)).getInsightType()).isEqualTo(ParentInsightType.PROGRESS);

        answer(1, false, true, false);  // 0.3279; mean of the two evidenced skills = 0.5000
        assertThat(single(insights.currentInsights(CHILD)).getInsightType()).isEqualTo(ParentInsightType.WEAKNESS);
    }

    @Test
    void strongEvidenceAcrossEverySkillIsMastery() {
        for (int skill = 0; skill < 4; skill++) {
            answer(skill, true, true, true);
        }

        assertThat(objectiveState.get().getStatus()).isEqualTo(LearnerObjectiveStatus.MASTERED);
        assertThat(single(insights.currentInsights(CHILD)).getInsightType()).isEqualTo(ParentInsightType.STRENGTH);
    }

    @Test
    void strongButThinEvidenceAcrossEverySkillClaimsNothingYet() {
        for (int skill = 0; skill < 4; skill++) {
            answer(skill, true, true);
        }

        // The numeric rollup reaches the threshold, but no skill has the evidence a claim
        // needs - exactly like two answers on a single skill (PD-21; this used to read
        // "showing early strength").
        assertThat(objectiveState.get().getStatus()).isEqualTo(LearnerObjectiveStatus.MASTERED);
        assertThat(insights.currentInsights(CHILD)).isEmpty();
    }

    @Test
    void aSingleCorrectAnswerOnAOneSkillObjectiveClaimsNothing() {
        singleSkillObjective();
        answer(0, true);

        assertThat(objectiveState.get().getStatus()).isEqualTo(LearnerObjectiveStatus.MASTERED);
        assertThat(insights.currentInsights(CHILD)).isEmpty();
    }

    @Test
    void thinEvidenceOnSomeSkillsLimitsTheClaimToTheSkillsWithEnough() {
        answer(0, true, true, true);
        for (int skill = 1; skill < 4; skill++) {
            answer(skill, true);
        }

        // Every skill observed and the average at the threshold, so the rollup says MASTERED;
        // only one skill has enough evidence, so the claim is progress on the parts practised.
        assertThat(objectiveState.get().getStatus()).isEqualTo(LearnerObjectiveStatus.MASTERED);
        ParentInsight insight = single(insights.currentInsights(CHILD));
        assertThat(insight.getInsightType()).isEqualTo(ParentInsightType.PROGRESS);
        assertThat(insight.getHeadline()).isEqualTo("Your child is making good progress in the parts of "
                + OBJECTIVE_TITLE + " practised so far.");
    }

    @Test
    void weakEvidenceAcrossEverySkillIsWeaknessNotNotStarted() {
        for (int skill = 0; skill < 4; skill++) {
            answer(skill, false, false, false);
        }

        // Before 5C: average 0 -> status stayed NOT_STARTED -> no insight at all.
        assertThat(objectiveState.get().getStatus()).isEqualTo(LearnerObjectiveStatus.IN_PROGRESS);
        assertThat(objectiveState.get().getMasteryProbability()).isEqualByComparingTo("0.0000");
        assertThat(single(insights.currentInsights(CHILD)).getInsightType()).isEqualTo(ParentInsightType.WEAKNESS);
    }

    @Test
    void currentInterpretationIsNotPersistedButAGeneratedSnapshotIs() {
        answer(0, true, true, true);

        List<ParentInsight> current = insights.currentInsights(CHILD);
        verify(parentInsightRepository, never()).saveAll(anyList());

        List<ParentInsight> recorded = insights.generateInsights(CHILD);
        verify(parentInsightRepository).saveAll(anyList());
        assertThat(recorded).extracting(ParentInsight::getHeadline)
                .containsExactlyElementsOf(current.stream().map(ParentInsight::getHeadline).toList());
    }

    @Test
    void currentInterpretationFollowsTheLearnerStateAsItChanges() {
        answer(0, false, false, false);
        assertThat(single(insights.currentInsights(CHILD)).getInsightType()).isEqualTo(ParentInsightType.WEAKNESS);

        answer(0, true, true, true, true, true, true); // the child improves
        assertThat(single(insights.currentInsights(CHILD)).getInsightType()).isEqualTo(ParentInsightType.PROGRESS);
    }

    private void answer(int skillIndex, boolean... outcomes) {
        for (boolean correct : outcomes) {
            masteryEngine.recordEvidence(CHILD, SKILLS.get(skillIndex), correct, UUID.randomUUID());
        }
    }

    private static ParentInsight single(List<ParentInsight> list) {
        assertThat(list).hasSize(1);
        return list.get(0);
    }

    /** The objective maps only the first skill - the shape of the one-skill curriculum found in browser verification. */
    private void singleSkillObjective() {
        when(learningObjectiveSkillRepository.findByLearningObjectiveId(OBJECTIVE))
                .thenReturn(List.of(LearningObjectiveSkill.create(OBJECTIVE, SKILLS.get(0))));
    }

}
