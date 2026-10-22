package com.platform.content.demo;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.content.entity.ActivityVersion;
import com.platform.content.entity.QuestionVersion;
import com.platform.content.service.ActivityService;
import com.platform.content.service.ContentOwnership;
import com.platform.content.service.QuestionService;
import com.platform.content.web.ActivityContentDtos.ActivityVersionDetailResponse;
import com.platform.content.web.LearnerActivityReader;
import com.platform.content.withdrawal.WithdrawnContent;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import com.platform.learning.service.AnswerFeedbackService;
import com.platform.learning.service.AnswerScoringService;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The public demo (M2): a few published activities anyone may try before registering, to feel the product rather than
 * read about it. What a visitor does is checked by the same scoring as a child's answer and **stored nowhere**: no
 * account, attempt, evidence, mastery or progress exists for a visitor.
 *
 * <p>Only exact published versions (pinned, never "the latest"), only platform content (a teacher's own content is
 * never made public through here), never withdrawn content, at most three, chosen by an administrator.
 */
@Service
public class PublicDemoService {

    static final int MAX_ACTIVITIES = 3;

    private final NamedParameterJdbcTemplate jdbc;
    private final ActivityService activities;
    private final QuestionService questions;
    private final ContentOwnership ownership;
    private final WithdrawnContent withdrawn;
    private final LearnerActivityReader reader;
    private final AnswerScoringService scoring;
    private final AnswerFeedbackService feedback;
    private final AccountRepository accounts;
    private final AuditService audit;

    public PublicDemoService(NamedParameterJdbcTemplate jdbc, ActivityService activities, QuestionService questions,
                             ContentOwnership ownership, WithdrawnContent withdrawn, LearnerActivityReader reader,
                             AnswerScoringService scoring, AnswerFeedbackService feedback, AccountRepository accounts,
                             AuditService audit) {
        this.jdbc = jdbc;
        this.activities = activities;
        this.questions = questions;
        this.ownership = ownership;
        this.withdrawn = withdrawn;
        this.reader = reader;
        this.scoring = scoring;
        this.feedback = feedback;
        this.accounts = accounts;
        this.audit = audit;
    }

    public record DemoAnswer(boolean correct, AnswerFeedbackService.CorrectAnswer correctAnswer, String explanation) {
    }

    public record Candidate(UUID activityVersionId, String title, int versionNumber, boolean chosen) {
    }

    /** The demo activities as a visitor plays them, in order; any that stopped being eligible are left out. */
    @Transactional(readOnly = true)
    public List<ActivityVersionDetailResponse> activities() {
        return chosenIds().stream().filter(this::eligible)
                .map(id -> reader.read(activities.getLearnerVisibleVersionById(id))).toList();
    }

    /**
     * Checks a visitor's answer to one question of a demo activity. Refused (404, nothing revealed) for anything that
     * isn't a question of a current demo activity. Stores nothing.
     */
    @Transactional(readOnly = true)
    public DemoAnswer check(UUID activityVersionId, UUID questionVersionId, String submittedAnswer) {
        if (!chosenIds().contains(activityVersionId) || !eligible(activityVersionId) || !Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM activity_items WHERE activity_version_id = :a AND question_version_id = :q)",
                Map.of("a", activityVersionId, "q", questionVersionId), Boolean.class))) {
            throw new ResourceNotFoundException("Demo question", questionVersionId);
        }
        QuestionVersion version = questions.getLearnerVisibleVersionById(questionVersionId);
        boolean correct = scoring.score(questions.getQuestion(version.getQuestionId()).getQuestionType(), version, submittedAnswer);
        return new DemoAnswer(correct, feedback.correctAnswerFor(questionVersionId), feedback.explanationFor(questionVersionId));
    }

    // ------------------------------------------------------------------ administration

    static final int CANDIDATES_SHOWN = 12;

    /**
     * Published platform activities an administrator may offer (each one's newest published version): the chosen ones
     * first, then up to {@value #CANDIDATES_SHOWN} whose title contains {@code query} (case-insensitive), so the list
     * stays short however much content the platform has.
     */
    @Transactional(readOnly = true)
    public List<Candidate> candidates(UUID adminId, String query) {
        requireAdmin(adminId);
        List<UUID> chosen = chosenIds();
        // A typed % or _ is a character, not a wildcard (backslash is PostgreSQL's LIKE escape).
        String like = "%" + (query == null ? "" : query.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")) + "%";
        List<Candidate> matches = jdbc.query("""
                SELECT id, title, version_number FROM (
                    SELECT DISTINCT ON (a.id) av.id, a.title, av.version_number
                    FROM activity_versions av
                    JOIN activities a ON a.id = av.activity_id
                    LEFT JOIN lessons l ON l.id = a.lesson_id
                    WHERE av.status = 'PUBLISHED' AND (a.lesson_id IS NULL OR l.owner_account_id IS NULL)
                    ORDER BY a.id, av.version_number DESC
                ) latest
                WHERE title ILIKE :like OR id IN (:chosen)
                ORDER BY lower(title)
                LIMIT :limit
                """, new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("like", like)
                        .addValue("chosen", chosen.isEmpty() ? List.of(new UUID(0, 0)) : chosen)
                        .addValue("limit", CANDIDATES_SHOWN + chosen.size()),
                (rs, i) -> {
                    UUID id = rs.getObject(1, UUID.class);
                    return new Candidate(id, rs.getString(2), rs.getInt(3), chosen.contains(id));
                });
        java.util.stream.Stream<Candidate> picked = chosen.stream()
                .flatMap(id -> matches.stream().filter(c -> c.activityVersionId().equals(id)));
        java.util.stream.Stream<Candidate> others = matches.stream().filter(c -> !c.chosen())
                .filter(c -> !withdrawn.activityVersion(c.activityVersionId())).limit(CANDIDATES_SHOWN);
        return java.util.stream.Stream.concat(picked, others).toList();
    }

    /** Replaces the demo with these versions, in this order (0 to 3). Each must be published platform content. */
    @Transactional
    public List<UUID> choose(UUID adminId, List<UUID> activityVersionIds) {
        Account admin = requireAdmin(adminId);
        List<UUID> ids = List.copyOf(new LinkedHashSet<>(activityVersionIds));
        if (ids.size() > MAX_ACTIVITIES) {
            throw new IllegalArgumentException("At most 3 activities can be tried without an account");
        }
        for (UUID id : ids) {
            ActivityVersion version = activities.getLearnerVisibleVersionById(id); // 404 for drafts and unknown ids
            if (ownership.activityOwner(version.getActivityId()).isPresent()) {
                throw new IllegalArgumentException("Only platform content can be tried without an account, not an author's own content");
            }
            if (withdrawn.activityVersion(id)) {
                throw new IllegalArgumentException("That activity has been taken down");
            }
            if (!eligible(id)) {
                throw new IllegalArgumentException("Only the current published version of an activity can be offered");
            }
        }
        jdbc.update("DELETE FROM public_demo_activities", Map.of());
        for (int i = 0; i < ids.size(); i++) {
            jdbc.update("INSERT INTO public_demo_activities (activity_version_id, position) VALUES (:id, :p)",
                    Map.of("id", ids.get(i), "p", i + 1));
        }
        audit.record(new RecordAuditEvent(null, admin.getId(), AuditActorType.ADMIN, "PUBLIC_DEMO_CHOSEN", "PUBLIC_DEMO", null,
                Map.of("activityVersionIds", ids.stream().map(UUID::toString).toList())));
        return ids;
    }

    // ------------------------------------------------------------------ helpers

    private List<UUID> chosenIds() {
        return jdbc.queryForList("SELECT activity_version_id FROM public_demo_activities ORDER BY position", Map.of(), UUID.class);
    }

    /**
     * The current published version of platform content, not taken down (one query: a refusal must not throw inside the
     * caller's transaction). New work always uses the current published version, so a superseded one drops out.
     */
    private boolean eligible(UUID activityVersionId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM activity_versions av
                               JOIN activities a ON a.id = av.activity_id
                               LEFT JOIN lessons l ON l.id = a.lesson_id
                               WHERE av.id = :id AND av.status = 'PUBLISHED' AND (a.lesson_id IS NULL OR l.owner_account_id IS NULL))
                """, Map.of("id", activityVersionId), Boolean.class)) && !withdrawn.activityVersion(activityVersionId);
    }

    private Account requireAdmin(UUID accountId) {
        Account account = accounts.findById(accountId).orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        if (account.getPlatformRole() != PlatformRole.ADMIN) {
            throw new AccessDeniedException("Admin access required");
        }
        return account;
    }

}
