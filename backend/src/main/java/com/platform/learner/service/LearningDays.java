package com.platform.learner.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * D-17: on how many different days a child showed a skill. "Secure" needs more than one sitting (EvidenceStandard), and
 * a day is the child's learning day - their family's chosen zone (V70), else the platform's, the same rule the daily
 * learning time follows (DailyLearningTimeService).
 */
@Component
public class LearningDays {

    private final NamedParameterJdbcTemplate jdbc;
    private final ZoneId platformZone;

    public LearningDays(NamedParameterJdbcTemplate jdbc, @Value("${platform.learning.day-zone:Asia/Phnom_Penh}") String zone) {
        this.jdbc = jdbc;
        this.platformZone = ZoneId.of(zone);
    }

    /** The zone each child's learning day follows. One query. */
    public Map<UUID, ZoneId> zonesOf(Collection<UUID> childIds) {
        Map<UUID, ZoneId> zones = new HashMap<>();
        if (childIds.isEmpty()) {
            return zones;
        }
        jdbc.query("SELECT c.id, f.learning_day_zone FROM children c JOIN families f ON f.id = c.family_id WHERE c.id IN (:ids)",
                Map.of("ids", childIds), rs -> {
                    zones.put(rs.getObject(1, UUID.class), parse(rs.getString(2)));
                });
        childIds.forEach(id -> zones.putIfAbsent(id, platformZone));
        return zones;
    }

    public ZoneId zoneOf(UUID childId) {
        return zonesOf(java.util.List.of(childId)).get(childId);
    }

    /** The learning day an instant falls on, in {@code zone}. */
    public static LocalDate dayOf(Instant at, ZoneId zone) {
        return at.atZone(zone).toLocalDate();
    }

    /** For each skill the child has evidence on, the number of different learning days it came from. */
    public Map<UUID, Integer> evidenceDays(UUID childId) {
        return evidenceDaysForAll(java.util.List.of(childId), null).getOrDefault(childId, Map.of());
    }

    /** As it stood at {@code at} (StandingHistory): evidence given by then. */
    public Map<UUID, Integer> evidenceDaysAt(UUID childId, Instant at) {
        return evidenceDaysForAll(java.util.List.of(childId), at).getOrDefault(childId, Map.of());
    }

    public Map<UUID, Map<UUID, Integer>> evidenceDaysForAll(Collection<UUID> childIds) {
        return evidenceDaysForAll(childIds, null);
    }

    /**
     * The same for several children (a class): child -> skill -> days. One query per distinct zone among them (zones are
     * checked in Java first, so a stored zone the database wouldn't know can't break the statement).
     */
    private Map<UUID, Map<UUID, Integer>> evidenceDaysForAll(Collection<UUID> childIds, Instant upTo) {
        Map<UUID, Map<UUID, Integer>> days = new HashMap<>();
        if (childIds.isEmpty()) {
            return days;
        }
        Map<ZoneId, java.util.List<UUID>> byZone = new HashMap<>();
        zonesOf(childIds).forEach((child, zone) -> byZone.computeIfAbsent(zone, z -> new java.util.ArrayList<>()).add(child));
        byZone.forEach((zone, children) -> {
            // A region (Asia/Phnom_Penh) means the same in Java and PostgreSQL; a fixed offset is added explicitly, because
            // PostgreSQL reads "+07:00"-style zone names with the POSIX sign (the opposite way round).
            boolean fixed = zone.getRules().isFixedOffset();
            String day = fixed ? "((occurred_at AT TIME ZONE 'UTC') + make_interval(secs => :offset))::date"
                    : "(occurred_at AT TIME ZONE :zone)::date";
            Map<String, Object> params = new HashMap<>();
            params.put("children", children);
            params.put("zone", zone.getId());
            params.put("offset", fixed ? zone.getRules().getOffset(Instant.EPOCH).getTotalSeconds() : 0);
            params.put("upTo", upTo == null ? null : java.sql.Timestamp.from(upTo));
            jdbc.query("SELECT child_id, skill_id, count(DISTINCT " + day + ") FROM learner_skill_history"
                            + " WHERE child_id IN (:children) AND triggered_by_type IS NOT NULL"
                            + (upTo == null ? "" : " AND occurred_at <= :upTo")
                            + " GROUP BY child_id, skill_id",
                    params, rs -> {
                        days.computeIfAbsent(rs.getObject(1, UUID.class), id -> new HashMap<>())
                                .put(rs.getObject(2, UUID.class), rs.getInt(3));
                    });
        });
        return days;
    }

    private ZoneId parse(String chosen) {
        if (chosen == null) {
            return platformZone;
        }
        try {
            return ZoneId.of(chosen);
        } catch (DateTimeException e) {
            return platformZone;
        }
    }
}
