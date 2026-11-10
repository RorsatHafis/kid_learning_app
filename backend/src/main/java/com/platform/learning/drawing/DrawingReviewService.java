package com.platform.learning.drawing;

import com.platform.common.web.ResourceNotFoundException;
import com.platform.family.service.ChildAccess;
import com.platform.family.service.FamilyAccessGuard;
import com.platform.school.service.SchoolAccessGuard;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owner-approved 2026-11-10: a child's drawings (DRAWING answers, never graded) reviewed by grown-ups who may see them,
 * with their feedback (V91).
 *
 * <ul>
 *   <li>The family (and a caregiver who may see the child's progress) sees every drawing of the child and all feedback;
 *       the family leaves feedback.</li>
 *   <li>A class's teachers and principal see only drawings made in learning that class assigns - never the family's own
 *       learning at home (CLAUDE.md §15) - of the class's current members whose school access is active, and only the
 *       school's own feedback; they leave feedback there.</li>
 * </ul>
 * Anyone else is answered as if the drawing did not exist.
 */
@Service
public class DrawingReviewService {

    private static final int SHOWN = 60;

    public enum Side { FAMILY, SCHOOL }

    public record Feedback(UUID id, Side side, boolean mine, String body, Instant at) {
    }

    /** {@code guide} and {@code strokes} are JSON (Drawings); the screen draws them. */
    public record Drawing(UUID answerId, UUID childId, String childName, String activityTitle, String prompt, String guide,
                          String strokes, Instant drawnAt, List<Feedback> feedback) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final ChildAccess childAccess;
    private final FamilyAccessGuard familyAccess;
    private final SchoolAccessGuard schoolAccess;

    public DrawingReviewService(NamedParameterJdbcTemplate jdbc, ChildAccess childAccess, FamilyAccessGuard familyAccess,
                                SchoolAccessGuard schoolAccess) {
        this.jdbc = jdbc;
        this.childAccess = childAccess;
        this.familyAccess = familyAccess;
        this.schoolAccess = schoolAccess;
    }

    // ------------------------------------------------------------------ the family

    @Transactional(readOnly = true)
    public List<Drawing> forChild(UUID accountId, UUID childId) {
        childAccess.requireViewer(accountId, childId);
        return drawings("a.child_id = :child", new MapSqlParameterSource("child", childId), accountId, null);
    }

    @Transactional
    public Drawing familyFeedback(UUID accountId, UUID childId, UUID answerId, String body) {
        familyAccess.requireChildAccess(accountId, childId);
        List<Drawing> found = drawings("a.child_id = :child AND r.id = :answer",
                new MapSqlParameterSource("child", childId).addValue("answer", answerId), accountId, null);
        return addFeedback(found, answerId, accountId, Side.FAMILY, body, null);
    }

    // ------------------------------------------------------------------ a class

    @Transactional(readOnly = true)
    public List<Drawing> forClass(UUID accountId, UUID classId) {
        schoolAccess.requireClassStaff(accountId, classId);
        return drawings(CLASS_SCOPE, new MapSqlParameterSource("class", classId), accountId, Side.SCHOOL);
    }

    @Transactional
    public Drawing classFeedback(UUID accountId, UUID classId, UUID answerId, String body) {
        schoolAccess.requireClassStaff(accountId, classId);
        List<Drawing> found = drawings(CLASS_SCOPE + " AND r.id = :answer",
                new MapSqlParameterSource("class", classId).addValue("answer", answerId), accountId, Side.SCHOOL);
        return addFeedback(found, answerId, accountId, Side.SCHOOL, body, Side.SCHOOL);
    }

    /** The class's current members, with the school's access active, on a path the class assigns. */
    private static final String CLASS_SCOPE = """
            EXISTS (SELECT 1 FROM class_enrollments ce
                    JOIN school_classes sc ON sc.id = ce.school_class_id AND sc.status = 'ACTIVE'
                    JOIN school_child_access g ON g.child_id = ce.child_id AND g.school_id = sc.school_id AND g.status = 'ACTIVE'
                    JOIN class_curriculum_assignments k ON k.school_class_id = sc.id AND k.status = 'ACTIVE'
                    JOIN learning_path_items i ON i.id = a.learning_path_item_id
                    JOIN learning_paths p ON p.id = i.learning_path_id
                    JOIN enrollments e ON e.id = p.enrollment_id
                    JOIN curriculum_versions cv ON cv.id = e.curriculum_version_id AND cv.curriculum_id = k.curriculum_id
                    WHERE ce.school_class_id = :class AND ce.child_id = a.child_id AND ce.status = 'ACTIVE')
            """;

    // ------------------------------------------------------------------ shared

    private Drawing addFeedback(List<Drawing> found, UUID answerId, UUID accountId, Side side, String body, Side visible) {
        if (found.isEmpty()) {
            throw new ResourceNotFoundException("Drawing", answerId);
        }
        String text = body == null ? "" : body.strip();
        if (text.isEmpty() || text.length() > 500) {
            throw new IllegalArgumentException("Write a few words, up to 500 characters.");
        }
        jdbc.update("""
                INSERT INTO drawing_feedback (id, answer_record_id, author_account_id, author_side, body)
                VALUES (:id, :answer, :author, :side, :body)
                """, Map.of("id", UUID.randomUUID(), "answer", answerId, "author", accountId, "side", side.name(), "body", text));
        Drawing drawing = found.getFirst();
        return new Drawing(drawing.answerId(), drawing.childId(), drawing.childName(), drawing.activityTitle(), drawing.prompt(),
                drawing.guide(), drawing.strokes(), drawing.drawnAt(), feedbackOf(answerId, accountId, visible));
    }

    private List<Drawing> drawings(String scope, MapSqlParameterSource params, UUID accountId, Side visible) {
        params.addValue("limit", SHOWN);
        List<Drawing> rows = jdbc.query("""
                SELECT r.id, a.child_id, c.display_name, act.title, qv.prompt, qv.drawing_guide, r.submitted_answer, r.occurred_at
                FROM answer_records r
                JOIN activity_attempts a ON a.id = r.activity_attempt_id
                JOIN children c ON c.id = a.child_id AND c.status = 'ACTIVE'
                JOIN question_versions qv ON qv.id = r.question_version_id
                JOIN activity_versions av ON av.id = a.activity_version_id
                JOIN activities act ON act.id = av.activity_id
                WHERE NOT r.scored AND\s""" + scope + """
                 ORDER BY r.occurred_at DESC, r.id
                LIMIT :limit
                """, params, (rs, i) -> new Drawing(rs.getObject("id", UUID.class), rs.getObject("child_id", UUID.class),
                rs.getString("display_name"), rs.getString("title"), rs.getString("prompt"), rs.getString("drawing_guide"),
                rs.getString("submitted_answer"), rs.getTimestamp("occurred_at").toInstant(), List.of()));
        return rows.stream().map(d -> new Drawing(d.answerId(), d.childId(), d.childName(), d.activityTitle(), d.prompt(), d.guide(),
                d.strokes(), d.drawnAt(), feedbackOf(d.answerId(), accountId, visible))).toList();
    }

    /** The feedback on a drawing, oldest first; {@code visible}: only that side's (a school sees its own), null: all. */
    private List<Feedback> feedbackOf(UUID answerId, UUID accountId, Side visible) {
        MapSqlParameterSource params = new MapSqlParameterSource("answer", answerId).addValue("me", accountId);
        String sideFilter = visible == null ? "" : " AND author_side = :side";
        if (visible != null) params.addValue("side", visible.name());
        return jdbc.query("SELECT id, author_side, author_account_id = :me AS mine, body, created_at FROM drawing_feedback "
                        + "WHERE answer_record_id = :answer" + sideFilter + " ORDER BY created_at, id", params,
                (rs, i) -> new Feedback(rs.getObject("id", UUID.class), Side.valueOf(rs.getString("author_side")),
                        rs.getBoolean("mine"), rs.getString("body"), rs.getTimestamp("created_at").toInstant()));
    }
}
