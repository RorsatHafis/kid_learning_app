package com.platform.admin.service;

import com.platform.common.web.PageResponse;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.common.web.SearchText;
import com.platform.content.media.MediaStorage;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.management.ManagementFactory;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The platform administrator's operational reads (X15): the schools directory, how the learning loop is
 * running, week-by-week reports and the system's state. Aggregates and operational facts only - counts,
 * names of schools and classes, staff addresses - never a child's name or learning record: operating the
 * platform doesn't need them (CLAUDE.md §10, §22). Each read is a fixed number of statements.
 */
@Service
public class AdminInsightService {

    static final int REPORT_WEEKS = 12;

    private final AccountRepository accounts;
    private final NamedParameterJdbcTemplate jdbc;
    private final MediaStorage mediaStorage;
    private final Environment environment;

    public AdminInsightService(AccountRepository accounts, NamedParameterJdbcTemplate jdbc, MediaStorage mediaStorage,
                               Environment environment) {
        this.accounts = accounts;
        this.jdbc = jdbc;
        this.mediaStorage = mediaStorage;
        this.environment = environment;
    }

    // ------------------------------------------------------------------ schools

    /** A school as operations sees it: who runs it, how big it is, and what is still to set up. */
    public record SchoolRow(UUID id, String name, String status, Instant createdAt, String principalEmail, String principalStatus,
                            int classes, int teachers, int childrenWithAccess, int classesWithoutTeacher, int classesWithoutCurriculum) {
    }

    public record ClassRow(UUID id, String name, List<String> teachers, int children, List<String> curricula, boolean openCode) {
    }

    public record SchoolDetail(SchoolRow school, List<ClassRow> classes, List<String> staff) {
    }

    private static final String SCHOOL_COLUMNS = """
            s.id, s.name, s.status, s.created_at, a.email AS principal_email, a.status AS principal_status,
            (SELECT count(*) FROM school_classes c WHERE c.school_id = s.id AND c.status = 'ACTIVE') AS classes,
            (SELECT count(*) FROM school_staff st WHERE st.school_id = s.id AND st.status = 'ACTIVE') AS teachers,
            (SELECT count(*) FROM school_child_access g WHERE g.school_id = s.id AND g.status = 'ACTIVE') AS children,
            (SELECT count(*) FROM school_classes c WHERE c.school_id = s.id AND c.status = 'ACTIVE'
               AND NOT EXISTS (SELECT 1 FROM teacher_assignments t WHERE t.school_class_id = c.id AND t.status = 'ACTIVE')) AS no_teacher,
            (SELECT count(*) FROM school_classes c WHERE c.school_id = s.id AND c.status = 'ACTIVE'
               AND NOT EXISTS (SELECT 1 FROM class_curriculum_assignments k WHERE k.school_class_id = c.id AND k.status = 'ACTIVE'))
               AS no_curriculum
            FROM schools s LEFT JOIN accounts a ON a.id = s.principal_account_id
            """;

    @Transactional(readOnly = true)
    public PageResponse<SchoolRow> schools(UUID adminId, String text, PageResponse.Request request) {
        requireAdmin(adminId);
        MapSqlParameterSource params = new MapSqlParameterSource("pattern", SearchText.containsPattern(text))
                .addValue("limit", request.size()).addValue("offset", request.offset());
        String where = " WHERE (CAST(:pattern AS varchar) IS NULL OR s.name ILIKE CAST(:pattern AS varchar)"
                + " OR a.email ILIKE CAST(:pattern AS varchar)) ";
        Long total = jdbc.queryForObject("SELECT count(*) FROM schools s LEFT JOIN accounts a ON a.id = s.principal_account_id" + where,
                params, Long.class);
        List<SchoolRow> rows = total == null || total == 0 ? List.of()
                : jdbc.query("SELECT " + SCHOOL_COLUMNS + where + " ORDER BY lower(s.name), s.id LIMIT :limit OFFSET :offset", params,
                (rs, i) -> schoolRow(rs));
        return new PageResponse<>(rows, request.page(), request.size(), total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public SchoolDetail school(UUID adminId, UUID schoolId) {
        requireAdmin(adminId);
        MapSqlParameterSource params = new MapSqlParameterSource("id", schoolId);
        List<SchoolRow> found = jdbc.query("SELECT " + SCHOOL_COLUMNS + " WHERE s.id = :id", params, (rs, i) -> schoolRow(rs));
        if (found.isEmpty()) {
            throw new ResourceNotFoundException("School", schoolId);
        }
        List<ClassRow> classes = jdbc.query("""
                SELECT c.id, c.name,
                  COALESCE((SELECT string_agg(a.email, '|' ORDER BY a.email) FROM teacher_assignments t JOIN accounts a ON a.id = t.teacher_account_id
                            WHERE t.school_class_id = c.id AND t.status = 'ACTIVE'), '') AS teachers,
                  (SELECT count(*) FROM class_enrollments e JOIN school_child_access g ON g.child_id = e.child_id AND g.school_id = c.school_id
                   WHERE e.school_class_id = c.id AND e.status = 'ACTIVE' AND g.status = 'ACTIVE') AS children,
                  COALESCE((SELECT string_agg(cu.name, '|' ORDER BY cu.name) FROM class_curriculum_assignments k JOIN curriculums cu ON cu.id = k.curriculum_id
                            WHERE k.school_class_id = c.id AND k.status = 'ACTIVE'), '') AS curricula,
                  EXISTS (SELECT 1 FROM class_join_codes j WHERE j.school_class_id = c.id AND j.revoked_at IS NULL AND j.expires_at > now()) AS open_code
                FROM school_classes c WHERE c.school_id = :id AND c.status = 'ACTIVE' ORDER BY lower(c.name), c.id
                """, params, (rs, i) -> new ClassRow(rs.getObject("id", UUID.class), rs.getString("name"), split(rs.getString("teachers")),
                rs.getInt("children"), split(rs.getString("curricula")), rs.getBoolean("open_code")));
        List<String> staff = jdbc.queryForList("""
                SELECT a.email FROM school_staff st JOIN accounts a ON a.id = st.account_id
                WHERE st.school_id = :id AND st.status = 'ACTIVE' ORDER BY a.email
                """, params, String.class);
        return new SchoolDetail(found.getFirst(), classes, staff);
    }

    private static SchoolRow schoolRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        return new SchoolRow(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("status"),
                created == null ? null : created.toInstant(), rs.getString("principal_email"), rs.getString("principal_status"),
                rs.getInt("classes"), rs.getInt("teachers"), rs.getInt("children"), rs.getInt("no_teacher"), rs.getInt("no_curriculum"));
    }

    private static List<String> split(String joined) {
        return joined == null || joined.isEmpty() ? List.of() : Arrays.asList(joined.split("\\|"));
    }

    // ------------------------------------------------------------------ learning operations

    /** Over the last 7 days, and the loop's standing queues. */
    public record LoopActivity(long attemptsStarted, long attemptsCompleted, long childrenLearning, long answers, long answersCorrect,
                               long attemptsLeftOpen, Map<String, Long> placementsBySource, long reviewItemsActive, long reviewDueNow) {
    }

    /** A skill a published, offered curriculum relies on that nothing published and in use practises: children can't be given it. */
    public record CoverageGap(UUID skillId, String skill, String subject, String curriculum) {
    }

    public record LearningOperations(LoopActivity lastSevenDays, List<CoverageGap> coverageGaps, List<String> ageGroupsWithNothingOffered) {
    }

    @Transactional(readOnly = true)
    public LearningOperations learningOperations(UUID adminId) {
        requireAdmin(adminId);
        Instant now = Instant.now();
        MapSqlParameterSource params = new MapSqlParameterSource("since", Timestamp.from(now.minus(Duration.ofDays(7))))
                .addValue("stale", Timestamp.from(now.minus(Duration.ofDays(1)))).addValue("now", Timestamp.from(now));
        Map<String, Object> attempts = jdbc.queryForMap("""
                SELECT count(*) FILTER (WHERE started_at >= :since) AS started,
                       count(*) FILTER (WHERE status = 'COMPLETED' AND completed_at >= :since) AS completed,
                       count(DISTINCT child_id) FILTER (WHERE status = 'COMPLETED' AND completed_at >= :since) AS children,
                       count(*) FILTER (WHERE status = 'IN_PROGRESS' AND started_at < :stale AND started_at >= :since) AS left_open
                FROM activity_attempts WHERE started_at >= :since OR (status = 'COMPLETED' AND completed_at >= :since)
                """, params);
        Map<String, Object> answers = jdbc.queryForMap("""
                SELECT count(*) AS answers, count(*) FILTER (WHERE is_correct) AS correct FROM answer_records WHERE occurred_at >= :since
                """, params);
        Map<String, Long> placements = new LinkedHashMap<>();
        for (String source : List.of("CURRICULUM", "ADAPTIVE", "REVIEW", "TEACHER_ASSIGNED")) {
            placements.put(source, 0L);
        }
        jdbc.query("SELECT source, count(*) AS n FROM learning_path_items WHERE added_at >= :since GROUP BY source", params,
                rs -> {
                    placements.put(rs.getString("source"), rs.getLong("n"));
                });
        Map<String, Object> review = jdbc.queryForMap("""
                SELECT count(*) AS active,
                       count(*) FILTER (WHERE EXISTS (SELECT 1 FROM review_schedules r WHERE r.review_item_id = i.id AND r.due_at <= :now)) AS due
                FROM review_items i WHERE i.status = 'ACTIVE'
                """, params);
        LoopActivity loop = new LoopActivity(num(attempts.get("started")), num(attempts.get("completed")), num(attempts.get("children")),
                num(answers.get("answers")), num(answers.get("correct")), num(attempts.get("left_open")), placements,
                num(review.get("active")), num(review.get("due")));

        List<CoverageGap> gaps = jdbc.query("""
                SELECT DISTINCT sk.id, sk.name AS skill, su.name AS subject, cu.name AS curriculum
                FROM curriculums cu
                JOIN curriculum_versions cv ON cv.curriculum_id = cu.id AND cv.status = 'PUBLISHED'
                JOIN curriculum_objectives co ON co.curriculum_version_id = cv.id
                JOIN learning_objective_skills los ON los.learning_objective_id = co.learning_objective_id
                JOIN skills sk ON sk.id = los.skill_id
                JOIN subjects su ON su.id = cu.subject_id
                WHERE cu.status = 'ACTIVE'
                  AND NOT EXISTS (SELECT 1 FROM activity_version_skills avs
                                  JOIN activity_versions av ON av.id = avs.activity_version_id AND av.status = 'PUBLISHED'
                                  JOIN activities ac ON ac.id = av.activity_id AND ac.status = 'ACTIVE'
                                  WHERE avs.skill_id = sk.id)
                ORDER BY su.name, cu.name, sk.name
                LIMIT 50
                """, params, (rs, i) -> new CoverageGap(rs.getObject("id", UUID.class), rs.getString("skill"), rs.getString("subject"),
                rs.getString("curriculum")));
        List<String> uncovered = jdbc.queryForList("""
                SELECT ah.name FROM age_hubs ah
                WHERE NOT EXISTS (SELECT 1 FROM curriculums c JOIN curriculum_versions v ON v.curriculum_id = c.id AND v.status = 'PUBLISHED'
                                  WHERE c.age_hub_id = ah.id AND c.status = 'ACTIVE' AND c.owner_account_id IS NULL)
                ORDER BY ah.display_order
                """, params, String.class);
        return new LearningOperations(loop, gaps, uncovered);
    }

    // ------------------------------------------------------------------ reports

    /** One week (Monday, UTC) of the platform: who joined, who learned, how much. */
    public record Week(LocalDate weekOf, long newFamilies, long childrenLearning, long activitiesCompleted, long answers, long newSchools) {
    }

    /** The last {@link #REPORT_WEEKS} weeks, oldest first; each figure one grouped statement over its own table. */
    @Transactional(readOnly = true)
    public List<Week> weekly(UUID adminId) {
        requireAdmin(adminId);
        LocalDate thisMonday = LocalDate.now(ZoneOffset.UTC).with(java.time.DayOfWeek.MONDAY);
        LocalDate first = thisMonday.minusWeeks(REPORT_WEEKS - 1);
        MapSqlParameterSource params = new MapSqlParameterSource("from", Timestamp.from(first.atStartOfDay().toInstant(ZoneOffset.UTC)));
        Map<LocalDate, long[]> byWeek = new LinkedHashMap<>();
        for (int i = 0; i < REPORT_WEEKS; i++) {
            byWeek.put(first.plusWeeks(i), new long[5]);
        }
        weekCounts("SELECT date_trunc('week', created_at AT TIME ZONE 'UTC') AS w, count(*) AS n FROM families WHERE created_at >= :from GROUP BY 1",
                params, byWeek, 0);
        weekCounts("SELECT date_trunc('week', completed_at AT TIME ZONE 'UTC') AS w, count(DISTINCT child_id) AS n FROM activity_attempts "
                + "WHERE status = 'COMPLETED' AND completed_at >= :from GROUP BY 1", params, byWeek, 1);
        weekCounts("SELECT date_trunc('week', completed_at AT TIME ZONE 'UTC') AS w, count(*) AS n FROM activity_attempts "
                + "WHERE status = 'COMPLETED' AND completed_at >= :from GROUP BY 1", params, byWeek, 2);
        weekCounts("SELECT date_trunc('week', occurred_at AT TIME ZONE 'UTC') AS w, count(*) AS n FROM answer_records "
                + "WHERE occurred_at >= :from GROUP BY 1", params, byWeek, 3);
        weekCounts("SELECT date_trunc('week', created_at AT TIME ZONE 'UTC') AS w, count(*) AS n FROM schools WHERE created_at >= :from GROUP BY 1",
                params, byWeek, 4);
        List<Week> weeks = new ArrayList<>();
        byWeek.forEach((week, n) -> weeks.add(new Week(week, n[0], n[1], n[2], n[3], n[4])));
        return weeks;
    }

    private void weekCounts(String sql, MapSqlParameterSource params, Map<LocalDate, long[]> byWeek, int column) {
        jdbc.query(sql, params, rs -> {
            LocalDate week = rs.getTimestamp("w").toLocalDateTime().toLocalDate();
            long[] row = byWeek.get(week);
            if (row != null) {
                row[column] = rs.getLong("n");
            }
        });
    }

    // ------------------------------------------------------------------ system

    /**
     * What an operator checks first: the build, the schema, storage, mail routing, how long it has been up.
     * Settings are named, never shown with secrets.
     */
    public record SystemStatus(String version, String profiles, String schemaVersion, long databaseBytes, long mediaFiles, long mediaBytes,
                               long mediaFreeBytes, String mailHost, String publicUrl, long uptimeSeconds, String javaVersion,
                               long heapUsedBytes, long heapMaxBytes, Instant checkedAt) {
    }

    @Transactional(readOnly = true)
    public SystemStatus system(UUID adminId) {
        requireAdmin(adminId);
        MapSqlParameterSource none = new MapSqlParameterSource();
        String schema = jdbc.queryForObject("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1",
                none, String.class);
        Long dbBytes = jdbc.queryForObject("SELECT pg_database_size(current_database())", none, Long.class);
        Map<String, Object> media = jdbc.queryForMap("SELECT count(*) AS files, COALESCE(sum(size_bytes), 0) AS bytes FROM content_assets "
                + "WHERE sha256 IS NOT NULL", none);
        String version = AdminInsightService.class.getPackage().getImplementationVersion();
        Runtime runtime = Runtime.getRuntime();
        return new SystemStatus(version == null ? "development build" : version, String.join(", ", environment.getActiveProfiles()),
                schema, dbBytes == null ? 0 : dbBytes, num(media.get("files")), num(media.get("bytes")), mediaStorage.usableSpace(),
                environment.getProperty("spring.mail.host", "not set"), environment.getProperty("platform.mail.public-url", "not set"),
                ManagementFactory.getRuntimeMXBean().getUptime() / 1000, System.getProperty("java.version"),
                runtime.totalMemory() - runtime.freeMemory(), runtime.maxMemory(), Instant.now().truncatedTo(ChronoUnit.SECONDS));
    }

    // ------------------------------------------------------------------ helpers

    private static long num(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    private Account requireAdmin(UUID accountId) {
        Account account = accounts.findById(accountId).orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        if (account.getPlatformRole() != PlatformRole.ADMIN) {
            throw new AccessDeniedException("Admin access required");
        }
        return account;
    }

}
