package com.platform.school.service;

import com.platform.learner.service.EvidenceStandard;
import com.platform.learner.service.LearningDays;
import com.platform.learner.service.EvidenceStandard.SkillStanding;
import com.platform.learning.service.ProgressChangeService;
import com.platform.learning.service.SkillProgressService.CurriculumProgress;
import com.platform.school.service.SchoolLearningService.ClassSnapshot;
import com.platform.school.service.SchoolLearningService.MemberRow;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * How a class has moved over the last weeks (X16) - the teacher's and principal's "is the class
 * getting there?". For the skills of the curricula the class assigns, and the children in it, where
 * the class stood at the end of each week: counts of children-on-skills by standing, recomputed from
 * the immutable evidence history with the same {@link EvidenceStandard} as today's standings
 * (StandingHistory, X12) - never a second scale. A child counts from when they joined the class;
 * learning before that is theirs, not the class's. Counts only: no child is named.
 */
@Service
public class ClassTrendService {

    public static final int MIN_WEEKS = 4;
    public static final int MAX_WEEKS = 26;

    /** Where the class stood at {@code at}: {@code children} counted, each skill of each in one standing. */
    public record TrendPoint(Instant at, int children, int notStarted, int tooEarlyToTell, int needsPractice, int progressing,
                             int secure) {
    }

    /** One week's answers by the class's children while in the class. */
    public record WeekOfAnswers(Instant from, Instant to, int answers, int childrenAnswering) {
    }

    /** Children-on-skills that moved over the whole window (from the start, or from when the child joined). */
    public record Movement(int becameSecure, int movedUp, int firstReading, int movedDown) {
    }

    public record SkillMovement(UUID skillId, String name, int becameSecure, int movedUp, int movedDown) {
    }

    public record ClassTrend(int weeks, int skills, List<TrendPoint> points, List<WeekOfAnswers> answers, Movement movement,
                             List<SkillMovement> bySkill) {
    }

    private final SchoolAccessGuard guard;
    private final SchoolLearningService learning;
    private final EvidenceStandard evidenceStandard;
    private final LearningDays learningDays;
    private final NamedParameterJdbcTemplate jdbc;

    public ClassTrendService(SchoolAccessGuard guard, SchoolLearningService learning, EvidenceStandard evidenceStandard,
                             NamedParameterJdbcTemplate jdbc, LearningDays learningDays) {
        this.guard = guard;
        this.learning = learning;
        this.evidenceStandard = evidenceStandard;
        this.jdbc = jdbc;
        this.learningDays = learningDays;
    }

    private record Row(UUID childId, UUID skillId, Instant at, BigDecimal mastery, boolean evidence) {
    }

    /** A child-on-skill as it stood: answers by then, and the estimate after the last row. */
    private static final class Running {
        int evidence;
        BigDecimal mastery;
        final Set<java.time.LocalDate> days = new HashSet<>();
    }

    @Transactional(readOnly = true)
    public ClassTrend trend(UUID accountId, UUID classId, int weeks) {
        if (weeks < MIN_WEEKS || weeks > MAX_WEEKS) {
            throw new IllegalArgumentException("weeks must be between " + MIN_WEEKS + " and " + MAX_WEEKS);
        }
        guard.requireClassStaff(accountId, classId);
        ClassSnapshot snapshot = learning.snapshot(classId);

        Map<UUID, String> skillNames = new LinkedHashMap<>();
        for (List<CurriculumProgress> progress : snapshot.progressByMember().values()) {
            progress.forEach(curriculum -> curriculum.objectives().forEach(objective ->
                    objective.skills().forEach(skill -> skillNames.putIfAbsent(skill.skillId(), skill.name()))));
        }
        Map<UUID, Instant> joined = new HashMap<>();
        for (MemberRow member : snapshot.members()) {
            joined.put(member.childId(), member.joinedAt());
        }

        Instant now = Instant.now();
        List<Instant> at = new ArrayList<>();
        for (int k = weeks; k >= 0; k--) {
            at.add(now.minus(Duration.ofDays(7L * k)));
        }

        List<Row> rows = joined.isEmpty() || skillNames.isEmpty() ? List.of() : jdbc.query("""
                SELECT child_id, skill_id, occurred_at, mastery_probability, triggered_by_type IS NOT NULL AS evidence
                FROM learner_skill_history
                WHERE child_id IN (:children) AND skill_id IN (:skills) AND occurred_at <= :now
                ORDER BY occurred_at, id
                """, new MapSqlParameterSource("children", joined.keySet()).addValue("skills", skillNames.keySet())
                .addValue("now", Timestamp.from(now)), (rs, i) -> new Row(rs.getObject("child_id", UUID.class),
                rs.getObject("skill_id", UUID.class), rs.getTimestamp("occurred_at").toInstant(), rs.getBigDecimal("mastery_probability"),
                rs.getBoolean("evidence")));

        // Replay the history once, oldest first, taking each point as it is passed.
        Map<UUID, Map<UUID, Running>> state = new HashMap<>();
        Map<UUID, java.time.ZoneId> zones = learningDays.zonesOf(joined.keySet());
        List<Map<SkillStanding, Integer>> standings = new ArrayList<>();
        int[] answers = new int[weeks];
        List<Set<UUID>> answering = new ArrayList<>();
        for (int w = 0; w < weeks; w++) {
            answering.add(new HashSet<>());
        }
        // The window's start for each child-on-skill: the first point, or the child's joining if later.
        Map<UUID, Map<UUID, SkillStanding>> startOf = new HashMap<>();
        int next = 0;
        for (Row row : rows) {
            while (next < at.size() && row.at().isAfter(at.get(next))) {
                standings.add(countAt(at.get(next), state, joined, skillNames.size()));
                next++;
            }
            Instant start = later(at.getFirst(), joined.get(row.childId()));
            if (!row.at().isBefore(start)) {
                startOf.computeIfAbsent(row.childId(), id -> new HashMap<>()).computeIfAbsent(row.skillId(),
                        id -> standingOf(state.getOrDefault(row.childId(), Map.of()).get(row.skillId())));
            }
            Running running = state.computeIfAbsent(row.childId(), id -> new HashMap<>()).computeIfAbsent(row.skillId(), id -> new Running());
            if (row.evidence()) {
                running.evidence++;
                running.days.add(LearningDays.dayOf(row.at(), zones.get(row.childId())));
                Instant since = joined.get(row.childId());
                if (next > 0 && !row.at().isBefore(since)) {
                    answers[next - 1]++;
                    answering.get(next - 1).add(row.childId());
                }
            }
            running.mastery = row.mastery();
        }
        while (next < at.size()) {
            standings.add(countAt(at.get(next), state, joined, skillNames.size()));
            next++;
        }

        List<TrendPoint> points = new ArrayList<>();
        for (int i = 0; i < at.size(); i++) {
            Map<SkillStanding, Integer> c = standings.get(i);
            Instant point = at.get(i);
            int children = (int) joined.values().stream().filter(j -> !j.isAfter(point)).count();
            points.add(new TrendPoint(point, children, c.get(SkillStanding.NOT_STARTED), c.get(SkillStanding.TOO_EARLY_TO_TELL),
                    c.get(SkillStanding.NEEDS_PRACTICE), c.get(SkillStanding.PROGRESSING), c.get(SkillStanding.SECURE)));
        }
        List<WeekOfAnswers> weekly = new ArrayList<>();
        for (int w = 0; w < weeks; w++) {
            weekly.add(new WeekOfAnswers(at.get(w), at.get(w + 1), answers[w], answering.get(w).size()));
        }

        int becameSecure = 0;
        int movedUp = 0;
        int firstReading = 0;
        int movedDown = 0;
        Map<UUID, int[]> perSkill = new LinkedHashMap<>();
        for (Map.Entry<UUID, Map<UUID, SkillStanding>> child : startOf.entrySet()) {
            for (Map.Entry<UUID, SkillStanding> skill : child.getValue().entrySet()) {
                SkillStanding then = skill.getValue();
                SkillStanding current = standingOf(state.get(child.getKey()).get(skill.getKey()));
                if (then == current) {
                    continue;
                }
                int[] tally = perSkill.computeIfAbsent(skill.getKey(), id -> new int[3]);
                switch (ProgressChangeService.kind(then, current)) {
                    case BECAME_SECURE -> {
                        becameSecure++;
                        tally[0]++;
                    }
                    case MOVED_UP -> {
                        movedUp++;
                        tally[1]++;
                    }
                    case FIRST_READING -> firstReading++;
                    case MOVED_DOWN -> {
                        movedDown++;
                        tally[2]++;
                    }
                    case STARTED, PRACTISED -> {
                    }
                }
            }
        }
        List<SkillMovement> bySkill = perSkill.entrySet().stream()
                .filter(e -> e.getValue()[0] + e.getValue()[1] + e.getValue()[2] > 0)
                .map(e -> new SkillMovement(e.getKey(), skillNames.get(e.getKey()), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .sorted(Comparator.comparingInt((SkillMovement s) -> -(s.becameSecure() + s.movedUp()))
                        .thenComparing(SkillMovement::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new ClassTrend(weeks, skillNames.size(), points, weekly, new Movement(becameSecure, movedUp, firstReading, movedDown),
                bySkill);
    }

    /** Every child in the class by {@code at}, on every skill: one standing each. */
    private Map<SkillStanding, Integer> countAt(Instant at, Map<UUID, Map<UUID, Running>> state, Map<UUID, Instant> joined, int skills) {
        Map<SkillStanding, Integer> counts = new EnumMap<>(SkillStanding.class);
        for (SkillStanding standing : SkillStanding.values()) {
            counts.put(standing, 0);
        }
        for (Map.Entry<UUID, Instant> child : joined.entrySet()) {
            if (child.getValue().isAfter(at)) {
                continue;
            }
            Map<UUID, Running> skillsOfChild = state.getOrDefault(child.getKey(), Map.of());
            int started = 0;
            for (Running running : skillsOfChild.values()) {
                SkillStanding standing = standingOf(running);
                if (standing != SkillStanding.NOT_STARTED) {
                    counts.merge(standing, 1, Integer::sum);
                    started++;
                }
            }
            counts.merge(SkillStanding.NOT_STARTED, skills - started, Integer::sum);
        }
        return counts;
    }

    private SkillStanding standingOf(Running running) {
        return running == null || running.evidence == 0 ? SkillStanding.NOT_STARTED
                : evidenceStandard.standingFor(running.evidence, running.mastery, running.days.size());
    }

    private static Instant later(Instant a, Instant b) {
        return b != null && b.isAfter(a) ? b : a;
    }

}
