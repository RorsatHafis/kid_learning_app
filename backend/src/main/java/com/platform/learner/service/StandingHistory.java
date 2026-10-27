package com.platform.learner.service;

import com.platform.learner.entity.LearnerSkillHistory;
import com.platform.learner.repository.LearnerSkillHistoryRepository;
import com.platform.learner.service.EvidenceStandard.SkillStanding;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Where a child stood on a skill at a past moment (X12), from the immutable evidence history
 * (learner_skill_history, one row per answer that was evidence) and the same rule that gives today's
 * standing ({@link EvidenceStandard}) - so "then" and "now" are claims of the same kind, never a
 * second scale. Nothing is stored: a past standing is recomputed from what had been recorded by then.
 *
 * <p>History written before V34's mastery bias correction is on the older scale; such a past
 * standing reads as it was stated then.
 */
@Service
public class StandingHistory {

    /** A skill as it stood: answers by then, and what they supported. */
    public record Moment(int evidence, SkillStanding standing) {
        public static final Moment NONE = new Moment(0, SkillStanding.NOT_STARTED);
    }

    private final LearnerSkillHistoryRepository history;
    private final EvidenceStandard evidenceStandard;
    private final LearningDays learningDays;

    public StandingHistory(LearnerSkillHistoryRepository history, EvidenceStandard evidenceStandard, LearningDays learningDays) {
        this.history = history;
        this.evidenceStandard = evidenceStandard;
        this.learningDays = learningDays;
    }

    /** Each skill the child had evidence on by {@code at}; a skill not in the map had none. One query. */
    @Transactional(readOnly = true)
    public Map<UUID, Moment> at(UUID childId, Instant at) {
        Map<UUID, Moment> moments = new HashMap<>();
        Map<UUID, Integer> days = learningDays.evidenceDaysAt(childId, at);
        for (LearnerSkillHistoryRepository.EvidenceAt row : history.evidenceAt(childId, at)) {
            int evidence = (int) row.getEvidence();
            moments.put(row.getSkillId(), new Moment(evidence,
                    evidenceStandard.standingFor(evidence, row.getMastery(), days.getOrDefault(row.getSkillId(), 0))));
        }
        return moments;
    }

    /** For one skill, each child as they stood at their own moment (e.g. when practice was placed for them). One query. */
    @Transactional(readOnly = true)
    public Map<UUID, Moment> forSkillAt(UUID skillId, Map<UUID, Instant> atByChild) {
        Map<UUID, Moment> moments = new HashMap<>();
        if (atByChild.isEmpty()) {
            return moments;
        }
        Map<UUID, int[]> evidence = new HashMap<>();
        Map<UUID, BigDecimal> mastery = new HashMap<>();
        Map<UUID, java.util.Set<java.time.LocalDate>> days = new HashMap<>();
        Map<UUID, java.time.ZoneId> zones = learningDays.zonesOf(atByChild.keySet());
        List<LearnerSkillHistory> rows = history.findBySkillIdAndChildIdInOrderByOccurredAtAsc(skillId, atByChild.keySet());
        for (LearnerSkillHistory row : rows) {
            Instant at = atByChild.get(row.getChildId());
            if (at == null || row.getOccurredAt().isAfter(at)) {
                continue;
            }
            if (row.getTriggeredByType() != null) {
                evidence.computeIfAbsent(row.getChildId(), id -> new int[1])[0]++;
                days.computeIfAbsent(row.getChildId(), id -> new java.util.HashSet<>())
                        .add(LearningDays.dayOf(row.getOccurredAt(), zones.get(row.getChildId())));
            }
            mastery.put(row.getChildId(), row.getMasteryProbability());
        }
        atByChild.keySet().forEach(childId -> {
            int count = evidence.containsKey(childId) ? evidence.get(childId)[0] : 0;
            moments.put(childId, count == 0 ? Moment.NONE : new Moment(count, evidenceStandard.standingFor(count, mastery.get(childId),
                    days.getOrDefault(childId, java.util.Set.of()).size())));
        });
        return moments;
    }

}
