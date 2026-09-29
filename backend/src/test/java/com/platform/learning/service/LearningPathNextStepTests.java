package com.platform.learning.service;

import com.platform.learning.entity.LearningPathItem;
import com.platform.learning.entity.LearningPathItemSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** LI-1: which step a child is served next, and the order the journey tells. */
class LearningPathNextStepTests {

    private static final UUID PATH = UUID.randomUUID();

    private static LearningPathItem item(int position, LearningPathItemSource source) {
        return LearningPathItem.place(PATH, UUID.randomUUID(), position, source, Instant.parse("2026-09-01T00:00:00Z"));
    }

    @Test
    void placedPracticeIsServedBeforeTheRemainingCurriculum() {
        LearningPathItem first = item(0, LearningPathItemSource.CURRICULUM);
        LearningPathItem second = item(1, LearningPathItemSource.CURRICULUM);
        LearningPathItem third = item(2, LearningPathItemSource.CURRICULUM);
        LearningPathItem reinforcement = item(3, LearningPathItemSource.ADAPTIVE);
        LearningPathItem review = item(4, LearningPathItemSource.REVIEW);
        first.complete(Instant.parse("2026-09-02T00:00:00Z"));

        List<LearningPathItem> items = List.of(first, second, third, reinforcement, review);

        assertThat(LearningPathService.nextOf(items)).contains(reinforcement);
        assertThat(LearningPathService.playOrder(items)).containsExactly(first, reinforcement, review, second, third);
    }

    @Test
    void anActivityAlreadyUnderwayResumesFirst() {
        LearningPathItem started = item(0, LearningPathItemSource.CURRICULUM);
        LearningPathItem reinforcement = item(1, LearningPathItemSource.ADAPTIVE);
        started.start();

        assertThat(LearningPathService.nextOf(List.of(started, reinforcement))).contains(started);
    }

    @Test
    void theCurriculumContinuesOncePlacedPracticeIsDone() {
        LearningPathItem first = item(0, LearningPathItemSource.CURRICULUM);
        LearningPathItem second = item(1, LearningPathItemSource.CURRICULUM);
        LearningPathItem reinforcement = item(2, LearningPathItemSource.ADAPTIVE);
        first.complete(Instant.parse("2026-09-02T00:00:00Z"));
        reinforcement.complete(Instant.parse("2026-09-03T00:00:00Z"));

        List<LearningPathItem> items = List.of(first, second, reinforcement);

        assertThat(LearningPathService.nextOf(items)).contains(second);
        // Done steps are told in the order they were completed.
        assertThat(LearningPathService.playOrder(items)).containsExactly(first, reinforcement, second);
    }

    @Test
    void skippedStepsAreLeftOutAndAFinishedPathHasNoNextStep() {
        LearningPathItem done = item(0, LearningPathItemSource.CURRICULUM);
        LearningPathItem skipped = item(1, LearningPathItemSource.CURRICULUM);
        done.complete(Instant.parse("2026-09-02T00:00:00Z"));
        skipped.skip();

        assertThat(LearningPathService.nextOf(List.of(done, skipped))).isEmpty();
        assertThat(LearningPathService.playOrder(List.of(done, skipped))).containsExactly(done);
    }
}
