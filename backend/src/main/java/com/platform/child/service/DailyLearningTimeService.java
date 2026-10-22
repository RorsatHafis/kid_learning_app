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
 * <p>The learning day follows {@code platform.learning.day-zone} (Cambodia by default); families in other zones are a
 * later per-family setting.
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

    public record Today(LocalDate day, Integer limitMinutes, int usedMinutes, int extraMinutes, Integer remainingMinutes,
                        boolean reached, boolean nearEnd) {
    }

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
        LocalDate day = LocalDate.now(clock.withZone(zone));
        Integer limit = limitMinutes(childId);
        int used = usedMinutes(childId, day);
        int extra = extraMinutes(childId, day);
        if (limit == null) {
            return new Today(day, null, used, extra, null, false, false);
        }
        int remaining = Math.max(0, limit + extra - used);
        return new Today(day, limit, used, extra, remaining, remaining == 0, remaining > 0 && remaining <= NEAR_END_MINUTES);
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
        LocalDate day = LocalDate.now(clock.withZone(zone));
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

    private int usedMinutes(UUID childId, LocalDate day) {
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
