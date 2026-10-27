package com.platform.learning.service;

import com.platform.knowledge.entity.Skill;
import com.platform.knowledge.repository.SkillRepository;
import com.platform.learner.entity.LearnerSkillState;
import com.platform.learner.repository.LearnerSkillStateRepository;
import com.platform.learner.service.EvidenceStandard;
import com.platform.learner.service.LearningDays;
import com.platform.learner.service.EvidenceStandard.SkillStanding;
import com.platform.learner.service.StandingHistory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What changed in a child's learning over the last days (X12) - the parent's "what is improving?".
 * Each skill the child answered on in the window, with where it stood at the start and where it
 * stands now (the same {@link EvidenceStandard} claims as everywhere else), and how many answers that
 * rests on. Nothing changes without answers, so a skill without new answers is not listed.
 */
@Service
public class ProgressChangeService {

    public static final int MIN_DAYS = 7;
    public static final int MAX_DAYS = 90;

    /** How a skill moved, in the order a parent reads them. */
    public enum ChangeKind {
        /** Now secure (it wasn't). */
        BECAME_SECURE,
        /** Needs practice then, progressing now. */
        MOVED_UP,
        /** Enough answers now for a first reading (too few, or none, before). */
        FIRST_READING,
        /** First answers; too few to judge yet. */
        STARTED,
        /** Answered again; standing unchanged. */
        PRACTISED,
        /** Lower than before: secure to progressing, or to needs practice. */
        MOVED_DOWN
    }

    /**
     * {@code onPlan}: the skill is in one of the child's learning paths, so it has a skill page; a skill practised only
     * outside them (a class's own practice, free practice of other content) is told here but has none.
     */
    public record SkillChange(UUID skillId, String name, ChangeKind kind, SkillStanding then, SkillStanding now,
                              int answersSince, Instant lastPracticedAt, boolean onPlan) {
    }

    public record ProgressSince(Instant since, int days, int answersSince, List<SkillChange> changes) {
    }

    private final LearnerSkillStateRepository skillStates;
    private final SkillRepository skills;
    private final StandingHistory standingHistory;
    private final EvidenceStandard evidenceStandard;
    private final LearningDays learningDays;
    private final SkillProgressService skillProgress;

    public ProgressChangeService(LearnerSkillStateRepository skillStates, SkillRepository skills, StandingHistory standingHistory,
                                 EvidenceStandard evidenceStandard, LearningDays learningDays, SkillProgressService skillProgress) {
        this.skillStates = skillStates;
        this.skills = skills;
        this.standingHistory = standingHistory;
        this.evidenceStandard = evidenceStandard;
        this.learningDays = learningDays;
        this.skillProgress = skillProgress;
    }

    @Transactional(readOnly = true)
    public ProgressSince since(UUID childId, int days) {
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new IllegalArgumentException("days must be between " + MIN_DAYS + " and " + MAX_DAYS);
        }
        Instant since = Instant.now().minus(Duration.ofDays(days));
        Map<UUID, StandingHistory.Moment> then = standingHistory.at(childId, since);
        List<LearnerSkillState> practised = skillStates.findByChildId(childId).stream()
                .filter(state -> state.getEvidenceCount() > then.getOrDefault(state.getSkillId(), StandingHistory.Moment.NONE).evidence())
                .toList();
        Map<UUID, Integer> evidenceDays = learningDays.evidenceDays(childId);
        // The same skills the skill page serves (SkillStoryService reads them from here too).
        java.util.Set<UUID> onPlan = skillProgress.progressFor(childId).stream()
                .flatMap(curriculum -> curriculum.objectives().stream())
                .flatMap(objective -> objective.skills().stream())
                .map(SkillProgressService.SkillProgress::skillId)
                .collect(Collectors.toSet());
        Map<UUID, Skill> names = skills.findAllById(practised.stream().map(LearnerSkillState::getSkillId).toList()).stream()
                .collect(Collectors.toMap(Skill::getId, Function.identity()));

        List<SkillChange> changes = practised.stream()
                .map(state -> {
                    StandingHistory.Moment before = then.getOrDefault(state.getSkillId(), StandingHistory.Moment.NONE);
                    SkillStanding now = evidenceStandard.standingFor(state, evidenceDays.getOrDefault(state.getSkillId(), 0));
                    Skill skill = names.get(state.getSkillId());
                    return new SkillChange(state.getSkillId(), skill == null ? "A skill" : skill.getName(), kind(before.standing(), now),
                            before.standing(), now, state.getEvidenceCount() - before.evidence(), state.getLastEvidenceAt(), onPlan.contains(state.getSkillId()));
                })
                .sorted(Comparator.comparing(SkillChange::kind).thenComparing(SkillChange::name))
                .toList();
        return new ProgressSince(since, days, changes.stream().mapToInt(SkillChange::answersSince).sum(), changes);
    }

    /** How a skill moved between two standings (also the class trend's, X16). */
    public static ChangeKind kind(SkillStanding then, SkillStanding now) {
        if (now == SkillStanding.SECURE && then != SkillStanding.SECURE) {
            return ChangeKind.BECAME_SECURE;
        }
        boolean judgedThen = then == SkillStanding.NEEDS_PRACTICE || then == SkillStanding.PROGRESSING || then == SkillStanding.SECURE;
        if (!judgedThen) {
            return now == SkillStanding.TOO_EARLY_TO_TELL ? ChangeKind.STARTED : ChangeKind.FIRST_READING;
        }
        if (then == now) {
            return ChangeKind.PRACTISED;
        }
        return rank(now) > rank(then) ? ChangeKind.MOVED_UP : ChangeKind.MOVED_DOWN;
    }

    private static int rank(SkillStanding standing) {
        return switch (standing) {
            case NOT_STARTED, TOO_EARLY_TO_TELL -> 0;
            case NEEDS_PRACTICE -> 1;
            case PROGRESSING -> 2;
            case SECURE -> 3;
        };
    }

}
