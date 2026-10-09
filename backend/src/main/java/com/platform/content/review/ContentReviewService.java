package com.platform.content.review;

import com.platform.common.web.PageResponse;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.identity.entity.Account;
import com.platform.identity.repository.AccountRepository;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A second person's review before platform content is published (Y14; PD-20 is the owner's
 * decision). With {@link Settings#required()} off - the default - nothing changes: an author
 * publishes. With it on, a draft version of platform content (owned by no one) is published only
 * by a <i>different</i> administrator approving a review its author asked for, and only if the
 * draft hasn't changed since. A teacher's own content is published by the teacher as before: it
 * reaches only the classes they teach.
 */
@Service
public class ContentReviewService {

    /** @param required whether platform content needs a second administrator's approval to be published */
    @ConfigurationProperties(prefix = "platform.content-review")
    public record Settings(boolean required) {
    }

    /** Proof, made only here, that a review approved this publication. */
    public static final class Approval {
        private final ContentReview.Kind kind;
        private final UUID versionId;

        Approval(ContentReview.Kind kind, UUID versionId) {
            this.kind = kind;
            this.versionId = versionId;
        }
    }

    /** What a version is, for the review rules and the queue. */
    public record ContentRef(ContentReview.Kind kind, UUID versionId, UUID contentId, UUID subjectId, UUID ownerId,
                             String title, String status) {
    }

    public record ReviewView(UUID id, ContentReview.Status status, String requestedBy, Instant requestedAt, String decidedBy,
                             Instant decidedAt, String note, boolean mine, boolean changedSince) {
    }

    /** {@code required}: this version needs a review to be published; {@code review}: its latest review, if any. */
    public record ReviewState(boolean required, ReviewView review) {
    }

    public record QueueRow(UUID id, ContentReview.Kind kind, UUID versionId, UUID contentId, UUID subjectId, String title,
                           String requestedBy, Instant requestedAt) {
    }

    private static final Map<ContentReview.Kind, String> REF_SQL = Map.of(
            ContentReview.Kind.QUESTION, """
                    select q.id as content_id, q.subject_id, q.owner_account_id as owner_id, v.prompt as title, v.status
                    from question_versions v join questions q on q.id = v.question_id where v.id = :v""",
            ContentReview.Kind.ACTIVITY, """
                    select a.id as content_id, a.subject_id, l.owner_account_id as owner_id, a.title, v.status
                    from activity_versions v join activities a on a.id = v.activity_id
                    left join lessons l on l.id = a.lesson_id where v.id = :v""",
            ContentReview.Kind.CURRICULUM, """
                    select c.id as content_id, c.subject_id, c.owner_account_id as owner_id, c.name as title, v.status
                    from curriculum_versions v join curriculums c on c.id = v.curriculum_id where v.id = :v""");

    /** Everything the draft is made of: any edit (each row carries updated_at) changes it. */
    private static final Map<ContentReview.Kind, String> FINGERPRINT_SQL = Map.of(
            ContentReview.Kind.QUESTION, """
                    select encode(sha256(convert_to(
                        (select row_to_json(v)::text from question_versions v where v.id = :v)
                        || coalesce((select string_agg(row_to_json(o)::text, ',' order by o.id) from question_options o
                                     where o.question_version_id = :v), '')
                        || coalesce((select string_agg(row_to_json(s)::text, ',' order by s.id) from question_version_skills s
                                     where s.question_version_id = :v), ''), 'UTF8')), 'hex')""",
            ContentReview.Kind.ACTIVITY, """
                    select encode(sha256(convert_to(
                        (select row_to_json(v)::text from activity_versions v where v.id = :v)
                        || coalesce((select string_agg(row_to_json(i)::text, ',' order by i.id) from activity_items i
                                     where i.activity_version_id = :v), '')
                        || coalesce((select string_agg(row_to_json(s)::text, ',' order by s.id) from activity_version_skills s
                                     where s.activity_version_id = :v), ''), 'UTF8')), 'hex')""",
            ContentReview.Kind.CURRICULUM, """
                    select encode(sha256(convert_to(
                        (select row_to_json(v)::text from curriculum_versions v where v.id = :v)
                        || coalesce((select string_agg(row_to_json(o)::text, ',' order by o.id) from curriculum_objectives o
                                     where o.curriculum_version_id = :v), ''), 'UTF8')), 'hex')""");

    private final Settings settings;
    private final ContentReviewRepository reviews;
    private final AccountRepository accounts;
    private final NamedParameterJdbcTemplate jdbc;

    public ContentReviewService(Settings settings, ContentReviewRepository reviews, AccountRepository accounts,
                                NamedParameterJdbcTemplate jdbc) {
        this.settings = settings;
        this.reviews = reviews;
        this.accounts = accounts;
        this.jdbc = jdbc;
    }

    public boolean reviewSwitchedOn() {
        return settings.required();
    }

    @Transactional(readOnly = true)
    public ContentRef ref(ContentReview.Kind kind, UUID versionId) {
        List<ContentRef> found = jdbc.query(REF_SQL.get(kind), Map.of("v", versionId), (rs, i) -> new ContentRef(kind, versionId,
                rs.getObject("content_id", UUID.class), rs.getObject("subject_id", UUID.class),
                rs.getObject("owner_id", UUID.class), rs.getString("title"), rs.getString("status")));
        return found.stream().findFirst().orElseThrow(() -> new ResourceNotFoundException(kind.name(), versionId));
    }

    /** Whether publishing this version needs an approved review. */
    public boolean required(ContentRef ref) {
        return settings.required() && ref.ownerId() == null;
    }

    /** The gate every publish passes: refused while review is required and no approval came with it. */
    @Transactional(readOnly = true)
    public void requirePublishable(ContentReview.Kind kind, UUID versionId, Approval approval) {
        if (!settings.required()) {
            return;
        }
        if (approval != null && approval.kind == kind && approval.versionId.equals(versionId)) {
            return;
        }
        if (required(ref(kind, versionId))) {
            throw new IllegalStateException("Platform content needs a second administrator's review before it is published: "
                    + "ask for a review");
        }
    }

    @Transactional(readOnly = true)
    public ReviewState state(UUID accountId, ContentReview.Kind kind, UUID versionId) {
        ContentRef ref = ref(kind, versionId);
        return new ReviewState(required(ref), reviews.findFirstByVersionIdOrderByRequestedAtDesc(versionId)
                .map(review -> view(review, accountId)).orElse(null));
    }

    /**
     * Asks for a review of a draft that passes its checks (the caller has checked them and the
     * author's access). An open review of the same, unchanged draft is kept; one of an older state
     * of the draft is replaced.
     */
    @Transactional
    public ReviewState request(UUID accountId, ContentReview.Kind kind, UUID versionId) {
        ContentRef ref = ref(kind, versionId);
        if (!required(ref)) {
            throw new IllegalStateException("This content is published without a review");
        }
        if (!"DRAFT".equals(ref.status())) {
            throw new IllegalStateException("Only a draft can be reviewed");
        }
        String fingerprint = fingerprint(kind, versionId);
        Instant now = Instant.now();
        Optional<ContentReview> open = reviews.findOpenForUpdate(versionId);
        if (open.isPresent() && open.get().getFingerprint().equals(fingerprint)) {
            return state(accountId, kind, versionId);
        }
        open.ifPresent(stale -> {
            stale.decide(ContentReview.Status.WITHDRAWN, accountId, "The draft changed; a new review was asked", now);
            reviews.flush();
        });
        reviews.save(ContentReview.open(kind, versionId, fingerprint, accountId, now));
        return state(accountId, kind, versionId);
    }

    /** The author withdraws their own open request. */
    @Transactional
    public ReviewState withdraw(UUID accountId, ContentReview.Kind kind, UUID versionId) {
        ContentReview open = reviews.findOpenForUpdate(versionId)
                .orElseThrow(() -> new IllegalStateException("No review is waiting for this draft"));
        if (!open.getRequestedBy().equals(accountId)) {
            throw new IllegalStateException("Only the person who asked can withdraw this review");
        }
        open.decide(ContentReview.Status.WITHDRAWN, accountId, null, Instant.now());
        return state(accountId, kind, versionId);
    }

    /**
     * Starts approving: the review must be open, asked by someone else, and of the draft as it is
     * now. Returns the approval the publication must carry; {@link #approved} records it after.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Approval approve(UUID reviewerId, UUID reviewId) {
        ContentReview review = openReview(reviewId);
        if (review.getRequestedBy().equals(reviewerId)) {
            throw new IllegalStateException("You asked for this review: another administrator approves it");
        }
        if (!review.getFingerprint().equals(fingerprint(review.getKind(), review.getVersionId()))) {
            throw new IllegalStateException("The draft changed after the review was asked: its author asks again");
        }
        review.decide(ContentReview.Status.APPROVED, reviewerId, null, Instant.now());
        return new Approval(review.getKind(), review.getVersionId());
    }

    @Transactional
    public ContentReview requestChanges(UUID reviewerId, UUID reviewId, String note) {
        ContentReview review = openReview(reviewId);
        if (review.getRequestedBy().equals(reviewerId)) {
            throw new IllegalStateException("You asked for this review: withdraw it instead");
        }
        review.decide(ContentReview.Status.CHANGES_REQUESTED, reviewerId, note, Instant.now());
        return review;
    }

    @Transactional(readOnly = true)
    public ContentReview get(UUID reviewId) {
        return reviews.findById(reviewId).orElseThrow(() -> new ResourceNotFoundException("Review", reviewId));
    }

    /** Open reviews, oldest first, for administrators. */
    @Transactional(readOnly = true)
    public PageResponse<QueueRow> openQueue(PageResponse.Request page) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("limit", page.size()).addValue("offset", page.offset());
        Long total = jdbc.queryForObject("select count(*) from content_reviews where status = 'OPEN'", params, Long.class);
        List<QueueRow> rows = jdbc.query("""
                select r.id, r.content_kind, r.version_id, r.requested_at, a.email
                from content_reviews r join accounts a on a.id = r.requested_by_account_id
                where r.status = 'OPEN' order by r.requested_at, r.id limit :limit offset :offset
                """, params, (rs, i) -> {
            ContentReview.Kind kind = ContentReview.Kind.valueOf(rs.getString("content_kind"));
            ContentRef ref = ref(kind, rs.getObject("version_id", UUID.class));
            return new QueueRow(rs.getObject("id", UUID.class), kind, ref.versionId(), ref.contentId(), ref.subjectId(),
                    ref.title(), rs.getString("email"), rs.getTimestamp("requested_at").toInstant());
        });
        return new PageResponse<>(rows, page.page(), page.size(), total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public long openCount() {
        Long count = jdbc.queryForObject("select count(*) from content_reviews where status = 'OPEN'", Map.of(), Long.class);
        return count == null ? 0 : count;
    }

    // ------------------------------------------------------------------

    private ContentReview openReview(UUID reviewId) {
        ContentReview review = reviews.findByIdForUpdate(reviewId).orElseThrow(() -> new ResourceNotFoundException("Review", reviewId));
        if (review.getStatus() != ContentReview.Status.OPEN) {
            throw new IllegalStateException("This review has already been answered");
        }
        return review;
    }

    private String fingerprint(ContentReview.Kind kind, UUID versionId) {
        return jdbc.queryForObject(FINGERPRINT_SQL.get(kind), Map.of("v", versionId), String.class);
    }

    private ReviewView view(ContentReview review, UUID viewer) {
        boolean changed = review.getStatus() == ContentReview.Status.OPEN
                && !review.getFingerprint().equals(fingerprint(review.getKind(), review.getVersionId()));
        return new ReviewView(review.getId(), review.getStatus(), email(review.getRequestedBy()), review.getRequestedAt(),
                review.getDecidedBy() == null ? null : email(review.getDecidedBy()), review.getDecidedAt(), review.getNote(),
                review.getRequestedBy().equals(viewer), changed);
    }

    private String email(UUID accountId) {
        return accounts.findById(accountId).map(Account::getEmail).orElse("a former account");
    }

}
