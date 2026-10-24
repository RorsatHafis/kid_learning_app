package com.platform.child.service;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.child.entity.ChildSettings;
import com.platform.child.repository.ChildRepository;
import com.platform.child.repository.ChildSettingsRepository;
import com.platform.common.web.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A child's learning day (M3): how long the parent wants it to be, how much of it is used, and whether a new activity
 * may start. Server-authoritative, from server timestamps only - each activity attempt started today counts from its
 * start to its last answer (or completion), at most 30 minutes, so a forgotten open activity can't eat the day and the
 * device's clock or a reported answer time can't stretch it.
 *
 * <p>The boundary is gentle: once the day's time is used, a <em>new</em> activity doesn't start; an activity already
 * open always finishes, and answers are never refused. Off unless the parent sets it. A parent can give a little more
 * time for today (10, 15 or 30 minutes, at most 2 hours extra a day), recorded and audited.
 *
 * <p>The learning day follows the family's own time zone when a parent has set one, else
 * {@code platform.learning.day-zone} (Cambodia by default).
 */
@Service
public class DailyLearningTimeService {

    static final Set<Integer> EXTENSION_MINUTES = Set.of(10, 15, 30);
    static final int MAX_EXTRA_MINUTES_PER_DAY = 120;
    /** Minutes left at which the child is told this is the last activity for today. */
    static final int NEAR_END_MINUTES = 5;

    /** More time a family gave on one learning day (in the family's data export). */
    public record Extension(LocalDate learningDay, int minutes, Instant grantedAt) {
    }

    /** {@code zone}: the IANA time zone the learning day follows (midnight there starts a new day). */
    public record Today(LocalDate day, Integer limitMinutes, int usedMinutes, int extraMinutes, Integer remainingMinutes,
                        boolean reached, boolean nearEnd, String zone) {
    }

    /** A family's learning day: the zone it follows, and whether that is the family's own choice or the platform's. */
    public record FamilyDay(String zone, boolean familyChoice, String platformZone) {
    }

    static final String UNKNOWN_ZONE = "Choose a time zone from the list";

    private final NamedParameterJdbcTemplate jdbc;
    private final ChildRepository children;
    private final ChildSettingsRepository settings;
    private final AuditService audit;
    private final ZoneId zone;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public DailyLearningTimeService(NamedParameterJdbcTemplate jdbc, ChildRepository children, ChildSettingsRepository settings,
                                    AuditService audit, @Value("${platform.learning.day-zone:Asia/Phnom_Penh}") String zone) {
        this(jdbc, children, settings, audit, ZoneId.of(zone), Clock.systemUTC());
    }

    DailyLearningTimeService(NamedParameterJdbcTemplate jdbc, ChildRepository children, ChildSettingsRepository settings,
                             AuditService audit, ZoneId zone, Clock clock) {
        this.jdbc = jdbc;
        this.children = children;
        this.settings = settings;
        this.audit = audit;
        this.zone = zone;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Integer limitMinutes(UUID childId) {
        return settings.findByChildId(childId).map(ChildSettings::getDailyLimitMinutes).orElse(null);
    }

    @Transactional
    public Integer setLimitMinutes(UUID childId, Integer minutes) {
        children.findByIdForUpdate(childId).orElseThrow(() -> new ResourceNotFoundException("Child", childId));
        ChildSettings row = settings.findByChildId(childId).orElseGet(() -> ChildSettings.of(childId));
        row.setDailyLimitMinutes(minutes);
        settings.saveAndFlush(row);
        return row.getDailyLimitMinutes();
    }

    @Transactional(readOnly = true)
    public Today today(UUID childId) {
        ZoneId zone = zoneOf(childId);
        LocalDate day = LocalDate.now(clock.withZone(zone));
        Integer limit = limitMinutes(childId);
        int used = usedMinutes(childId, day, zone);
        int extra = extraMinutes(childId, day);
        if (limit == null) {
            return new Today(day, null, used, extra, null, false, false, zone.getId());
        }
        int remaining = Math.max(0, limit + extra - used);
        return new Today(day, limit, used, extra, remaining, remaining == 0, remaining > 0 && remaining <= NEAR_END_MINUTES,
                zone.getId());
    }

    /** The learning day of the family this parent belongs to. */
    @Transactional(readOnly = true)
    public FamilyDay familyDay(UUID accountId) {
        String chosen = jdbc.queryForObject("SELECT learning_day_zone FROM families WHERE id = :f", Map.of("f", familyOf(accountId)),
                String.class);
        return new FamilyDay(chosen != null ? chosen : zone.getId(), chosen != null, zone.getId());
    }

    /**
     * A parent sets the zone their family's learning day follows (null: the platform's again). Only known IANA zone
     * ids are stored; today's learning time is then counted from midnight there.
     */
    @Transactional
    public FamilyDay setFamilyZone(UUID accountId, String zoneId) {
        UUID familyId = familyOf(accountId);
        String clean = zoneId == null || zoneId.isBlank() ? null : zoneId.strip();
        if (clean != null && (clean.length() > 64 || !ZoneId.getAvailableZoneIds().contains(clean))) {
            throw new IllegalArgumentException(UNKNOWN_ZONE);
        }
        String before = jdbc.queryForObject("SELECT learning_day_zone FROM families WHERE id = :f FOR UPDATE", Map.of("f", familyId),
                String.class);
        jdbc.update("UPDATE families SET learning_day_zone = :z WHERE id = :f", new MapSqlParameterSource("z", clean).addValue("f", familyId));
        if (!java.util.Objects.equals(before, clean)) {
            Map<String, Object> details = new java.util.HashMap<>();
            details.put("zone", clean == null ? "platform" : clean);
            audit.record(new RecordAuditEvent(null, accountId, AuditActorType.PARENT, "FAMILY_LEARNING_DAY_ZONE_CHANGED", "FAMILY",
                    familyId, details));
        }
        return familyDay(accountId);
    }

    /** Refuses a new activity once today's time is used (the caller has let an open one continue already). */
    @Transactional(readOnly = true)
    public void requireTimeLeft(UUID childId) {
        Today today = today(childId);
        if (today.reached()) {
            throw new DailyLimitReachedException(childId, today);
        }
    }

    /** A little more time for today, given by a member of the child's family (the caller checked the access). */
    @Transactional
    public Today extend(UUID accountId, UUID childId, int minutes) {
        children.findByIdForUpdate(childId).orElseThrow(() -> new ResourceNotFoundException("Child", childId));
        if (!EXTENSION_MINUTES.contains(minutes)) {
            throw new IllegalArgumentException("More time can be 10, 15 or 30 minutes");
        }
        if (limitMinutes(childId) == null) {
            throw new IllegalStateException("There's no daily limit to extend");
        }
        LocalDate day = LocalDate.now(clock.withZone(zoneOf(childId)));
        if (extraMinutes(childId, day) + minutes > MAX_EXTRA_MINUTES_PER_DAY) {
            throw new IllegalStateException("A learning day can be extended by 2 hours at most");
        }
        jdbc.update("""
                INSERT INTO child_daily_extensions (id, child_id, learning_day, minutes, granted_by_account_id)
                VALUES (:id, :c, :day, :m, :by)
                """, new MapSqlParameterSource("id", UUID.randomUUID()).addValue("c", childId).addValue("day", day)
                .addValue("m", minutes).addValue("by", accountId));
        audit.record(new RecordAuditEvent(null, accountId, AuditActorType.PARENT, "CHILD_DAILY_TIME_EXTENDED", "CHILD", childId,
                Map.of("minutes", minutes, "learningDay", day.toString())));
        return today(childId);
    }

    /** Every extension given for this child, newest first. */
    @Transactional(readOnly = true)
    public java.util.List<Extension> extensionsOf(UUID childId) {
        return jdbc.query("SELECT learning_day, minutes, granted_at FROM child_daily_extensions WHERE child_id = :c ORDER BY granted_at DESC",
                Map.of("c", childId), (rs, i) -> new Extension(rs.getObject(1, LocalDate.class), rs.getInt(2), rs.getTimestamp(3).toInstant()));
    }

    // ------------------------------------------------------------------ helpers

    /** The zone this child's learning day follows: their family's choice, else the platform's. */
    private ZoneId zoneOf(UUID childId) {
        String chosen = jdbc.query("SELECT f.learning_day_zone FROM children c JOIN families f ON f.id = c.family_id WHERE c.id = :c",
                Map.of("c", childId), rs -> rs.next() ? rs.getString(1) : null);
        if (chosen == null) {
            return zone;
        }
        try {
            return ZoneId.of(chosen);
        } catch (java.time.DateTimeException e) {
            return zone;
        }
    }

    private UUID familyOf(UUID accountId) {
        return jdbc.queryForList("SELECT family_id FROM memberships WHERE account_id = :a AND status = 'ACTIVE' ORDER BY created_at LIMIT 1",
                Map.of("a", accountId), UUID.class).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Family for account", accountId));
    }

    private int usedMinutes(UUID childId, LocalDate day, ZoneId zone) {
        Instant start = day.atStartOfDay(zone).toInstant();
        Instant end = day.plusDays(1).atStartOfDay(zone).toInstant();
        Double seconds = jdbc.queryForObject("""
                SELECT COALESCE(SUM(LEAST(EXTRACT(EPOCH FROM (COALESCE(last.at, a.completed_at, a.started_at) - a.started_at)), 1800)), 0)
                FROM activity_attempts a
                LEFT JOIN LATERAL (SELECT max(r.occurred_at) AS at FROM answer_records r WHERE r.activity_attempt_id = a.id) last ON TRUE
                WHERE a.child_id = :c AND a.started_at >= :start AND a.started_at < :end
                """, new MapSqlParameterSource("c", childId).addValue("start", Timestamp.from(start)).addValue("end", Timestamp.from(end)),
                Double.class);
        return (int) Math.floor((seconds == null ? 0 : Math.max(0, seconds)) / 60.0);
    }

    private int extraMinutes(UUID childId, LocalDate day) {
        Integer extra = jdbc.queryForObject("SELECT COALESCE(SUM(minutes), 0) FROM child_daily_extensions WHERE child_id = :c AND learning_day = :day",
                new MapSqlParameterSource("c", childId).addValue("day", day), Integer.class);
        return extra == null ? 0 : extra;
    }

}
