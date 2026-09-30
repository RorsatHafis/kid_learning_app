package com.platform.learner.service;

import com.platform.common.entity.PublicationStatus;
import com.platform.content.entity.ActivityVersion;
import com.platform.content.repository.ActivityVersionSkillRepository;
import com.platform.content.repository.ActivityVersionRepository;
import com.platform.knowledge.repository.SkillPrerequisiteRepository;
import com.platform.learner.entity.LearnerSkillState;
import com.platform.learner.repository.LearnerSkillStateRepository;
import com.platform.learning.entity.LearningPathItem;
import com.platform.learning.entity.LearningPathItemSource;
import com.platform.learning.service.LearningPathService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.platform.learning.service.ContentScopes;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdaptiveEngineTests {

    @Mock private LearnerSkillStateRepository skillStateRepository;
    @Mock private SkillPrerequisiteRepository skillPrerequisiteRepository;
    @Mock private ActivityVersionSkillRepository activitySkillRepository;
    @Mock private ActivityVersionRepository activityVersionRepository;
    @Mock private LearningPathService learningPathService;

    @Test
    void challengeUsesAnExistingHarderPendingPlacementInsteadOfDuplicatingIt() {
        UUID childId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID pathId = UUID.randomUUID();
        UUID currentActivityId = UUID.randomUUID();
        UUID harderActivityId = UUID.randomUUID();
        UUID currentVersionId = UUID.randomUUID();
        UUID harderVersionId = UUID.randomUUID();

        LearnerSkillState confidentState = LearnerSkillState.initial(childId, skillId);
        for (int i = 0; i < 3; i++) {
            confidentState.applyEvidence(new BigDecimal("0.9000"), new BigDecimal("0.6000"), true, Instant.now());
        }
        ActivityVersion current = publishedVersion(currentActivityId, currentVersionId, 5);
        ActivityVersion harder = publishedVersion(harderActivityId, harderVersionId, 9);
        LearningPathItem pendingHarder = LearningPathItem.place(
                pathId, harderVersionId, 3, LearningPathItemSource.CURRICULUM, Instant.now());

        when(skillStateRepository.findByChildIdAndSkillId(childId, skillId)).thenReturn(Optional.of(confidentState));
        // PD-08: the published versions that practise the skill, by their own tags.
        when(activitySkillRepository.findPublishedPractising(skillId)).thenReturn(List.of(current, harder));
        when(learningPathService.listItems(pathId)).thenReturn(List.of(pendingHarder));

        AdaptiveEngine engine = new AdaptiveEngine(skillStateRepository, skillPrerequisiteRepository,
                activitySkillRepository, activityVersionRepository, learningPathService,
                ContentScopes.admittingEverything());

        Optional<AdaptiveEngine.Recommendation> recommendation = engine.recommendNext(
                childId, skillId, pathId, currentVersionId);

        assertThat(recommendation).isPresent();
        assertThat(recommendation.get().direction()).isEqualTo(AdaptiveEngine.Direction.CHALLENGE);
        assertThat(recommendation.get().insertedItem()).isSameAs(pendingHarder);
        verify(learningPathService, never()).appendItem(pathId, harderVersionId, LearningPathItemSource.ADAPTIVE);
    }

    private static ActivityVersion publishedVersion(UUID activityId, UUID versionId, int difficulty) {
        ActivityVersion version = ActivityVersion.draft(activityId, 1, "Instructions", difficulty, 60);
        version.publish(Instant.now());
        ReflectionTestUtils.setField(version, "id", versionId);
        return version;
    }
}
