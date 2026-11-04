package com.platform.streak.service;

import com.platform.child.repository.ChildRepository;
import com.platform.child.service.DailyLearningTimeService;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.streak.entity.LearnerStreak;
import com.platform.streak.repository.LearnerStreakRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * STREAK-1: a child's learning streak - consecutive learning days on which they finished an activity. A day is the
 * child's learning day (their family's time zone, else the platform's - the same day the daily limit counts), never
 * the UTC date. The streak is real activity only: nothing is granted, and a streak whose last learning day is before
 * yesterday is shown as no current streak (its best is kept). Nothing is ever taken away as a penalty.
 */
@Service
public class LearnerStreakService {

    /** What the child's space shows: the streak as of today, the best ever, and whether today already counts. */
    public record Standing(int current, int longest, LocalDate lastLearningDay, boolean learnedToday) {
    }

    private final LearnerStreakRepository repo;
    private final ChildRepository children;
    private final DailyLearningTimeService days;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public LearnerStreakService(LearnerStreakRepository repo, ChildRepository children, DailyLearningTimeService days) {
        this(repo, children, days, Clock.systemUTC());
    }

    LearnerStreakService(LearnerStreakRepository repo, ChildRepository children, DailyLearningTimeService days, Clock clock) {
        this.repo = repo;
        this.children = children;
        this.days = days;
        this.clock = clock;
    }

    /** A finished activity: its learning day counts towards the streak (once per day). Race-free create + row lock. */
    @Transactional
    public LearnerStreak recordLearning(UUID childId, Instant at) {
        if (!children.existsById(childId)) {
            throw new ResourceNotFoundException("Child", childId);
        }
        repo.insertIfAbsent(UUID.randomUUID(), childId);
        LearnerStreak streak = repo.findByChildIdForUpdate(childId).orElseThrow(); // Battle 5D: completion path
        streak.record(at.atZone(days.learningDayZone(childId)).toLocalDate());
        return streak;
    }

    @Transactional(readOnly = true)
    public LearnerStreak get(UUID childId) {
        if (!children.existsById(childId)) {
            throw new ResourceNotFoundException("Child", childId);
        }
        return repo.findByChildId(childId).orElseGet(() -> LearnerStreak.create(childId));
    }

    /**
     * The streak as it stands today: alive while the last learning day is today or yesterday (today can still continue
     * it), otherwise no current streak - the stored count is history, not what the child has now.
     */
    @Transactional(readOnly = true)
    public Standing standing(UUID childId) {
        LearnerStreak streak = get(childId);
        ZoneId zone = days.learningDayZone(childId);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate last = streak.getLastActivityDate();
        boolean learnedToday = today.equals(last);
        boolean alive = last != null && (learnedToday || today.minusDays(1).equals(last));
        return new Standing(alive ? streak.getCurrentStreak() : 0, streak.getLongestStreak(), last, learnedToday);
    }
}
