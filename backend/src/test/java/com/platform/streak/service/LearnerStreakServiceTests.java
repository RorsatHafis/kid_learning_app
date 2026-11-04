package com.platform.streak.service;

import com.platform.child.repository.ChildRepository;
import com.platform.child.service.DailyLearningTimeService;
import com.platform.streak.entity.LearnerStreak;
import com.platform.streak.repository.LearnerStreakRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * STREAK-1: a learning day is the child's learning day (Cambodia here), not the UTC date; a streak is alive while its
 * last learning day is today or yesterday and reads 0 after a missed day, its best kept; nothing is invented.
 */
class LearnerStreakServiceTests {

    private static final ZoneId CAMBODIA = ZoneId.of("Asia/Phnom_Penh");
    private final UUID child = UUID.randomUUID();
    private final LearnerStreakRepository repo = mock(LearnerStreakRepository.class);
    private final ChildRepository children = mock(ChildRepository.class);
    private final DailyLearningTimeService days = mock(DailyLearningTimeService.class);

    private LearnerStreakService at(String instant) {
        when(children.existsById(child)).thenReturn(true);
        when(days.learningDayZone(child)).thenReturn(CAMBODIA);
        return new LearnerStreakService(repo, children, days, Clock.fixed(Instant.parse(instant), CAMBODIA));
    }

    private LearnerStreak streakWith(LocalDate... learningDays) {
        LearnerStreak streak = LearnerStreak.create(child);
        for (LocalDate day : learningDays) {
            streak.record(day);
        }
        when(repo.findByChildId(child)).thenReturn(Optional.of(streak));
        when(repo.findByChildIdForUpdate(child)).thenReturn(Optional.of(streak));
        return streak;
    }

    @Test
    void anActivityFinishedEarlyInTheMorningCountsForThatDayNotTheUtcDayBefore() {
        LearnerStreakService service = at("2026-11-04T12:00:00Z");
        LearnerStreak streak = streakWith(LocalDate.of(2026, 11, 3));
        // 06:30 on 4 November in Phnom Penh is 23:30 on 3 November in UTC.
        service.recordLearning(child, Instant.parse("2026-11-03T23:30:00Z"));
        assertThat(streak.getLastActivityDate()).isEqualTo(LocalDate.of(2026, 11, 4));
        assertThat(streak.getCurrentStreak()).isEqualTo(2);
    }

    @Test
    void aStreakIsAliveTodayAndYesterdayAndReadsZeroAfterAMissedDayWithItsBestKept() {
        streakWith(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3));

        LearnerStreakService.Standing sameDay = at("2026-11-03T10:00:00Z").standing(child);
        assertThat(sameDay).isEqualTo(new LearnerStreakService.Standing(3, 3, LocalDate.of(2026, 11, 3), true));

        LearnerStreakService.Standing nextDay = at("2026-11-04T10:00:00Z").standing(child);
        assertThat(nextDay.current()).as("today can still continue it").isEqualTo(3);
        assertThat(nextDay.learnedToday()).isFalse();

        LearnerStreakService.Standing afterAGap = at("2026-11-06T10:00:00Z").standing(child);
        assertThat(afterAGap.current()).as("a missed day: no current streak").isZero();
        assertThat(afterAGap.longest()).as("the best is kept").isEqualTo(3);
    }

    @Test
    void aChildWhoHasNotLearnedYetHasNoStreak() {
        LearnerStreakService service = at("2026-11-04T10:00:00Z");
        when(repo.findByChildId(any())).thenReturn(Optional.empty());
        assertThat(service.standing(child)).isEqualTo(new LearnerStreakService.Standing(0, 0, null, false));
    }
}
