package com.platform.site;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * LAND-1: the landing page's questions and answers, written by administrators in English and Khmer (V86) - ordered,
 * shown or hidden, each change audited, an edit over someone else's refused. The public read gives only what is
 * shown; nothing is shown that isn't written in both languages.
 */
@Service
public class SiteFaqService {

    public record Faq(UUID id, String questionEn, String questionKm, String answerEn, String answerKm, int position,
                      boolean visible, long version, Instant updatedAt) {
    }

    public record PublicFaq(UUID id, String questionEn, String questionKm, String answerEn, String answerKm) {
    }

    public record Draft(String questionEn, String questionKm, String answerEn, String answerKm, int position, boolean visible) {
    }

    private static final RowMapper<Faq> ROW = (rs, n) -> new Faq(rs.getObject("id", UUID.class), rs.getString("question_en"),
            rs.getString("question_km"), rs.getString("answer_en"), rs.getString("answer_km"), rs.getInt("position"),
            rs.getBoolean("visible"), rs.getLong("version"), rs.getTimestamp("updated_at").toInstant());

    private final NamedParameterJdbcTemplate jdbc;
    private final AccountRepository accounts;
    private final AuditService audit;

    public SiteFaqService(NamedParameterJdbcTemplate jdbc, AccountRepository accounts, AuditService audit) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<PublicFaq> visible() {
        return jdbc.query("SELECT * FROM site_faq WHERE visible ORDER BY position, created_at", Map.of(), ROW).stream()
                .map(f -> new PublicFaq(f.id(), f.questionEn(), f.questionKm(), f.answerEn(), f.answerKm()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Faq> all(UUID adminId) {
        requireAdmin(adminId);
        return jdbc.query("SELECT * FROM site_faq ORDER BY position, created_at", Map.of(), ROW);
    }

    @Transactional
    public Faq create(UUID adminId, Draft draft) {
        Account admin = requireAdmin(adminId);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO site_faq (id, question_en, question_km, answer_en, answer_km, position, visible)
                VALUES (:id, :questionEn, :questionKm, :answerEn, :answerKm, :position, :visible)
                """, params(validated(draft)).addValue("id", id));
        record(admin, "SITE_FAQ_CREATED", id, draft);
        return find(id);
    }

    /** Replaces a question's fields; {@code expectedVersion} refuses an edit made over someone else's (409). */
    @Transactional
    public Faq update(UUID adminId, UUID id, long expectedVersion, Draft draft) {
        Account admin = requireAdmin(adminId);
        int changed = jdbc.update("""
                UPDATE site_faq SET question_en = :questionEn, question_km = :questionKm, answer_en = :answerEn,
                       answer_km = :answerKm, position = :position, visible = :visible, version = version + 1
                WHERE id = :id AND version = :version
                """, params(validated(draft)).addValue("id", id).addValue("version", expectedVersion));
        if (changed == 0) {
            find(id); // 404 when it doesn't exist
            throw new IllegalStateException("This question was changed by someone else meanwhile. Reload it and try again.");
        }
        record(admin, "SITE_FAQ_UPDATED", id, draft);
        return find(id);
    }

    @Transactional
    public void delete(UUID adminId, UUID id) {
        Account admin = requireAdmin(adminId);
        Faq existing = find(id);
        jdbc.update("DELETE FROM site_faq WHERE id = :id", Map.of("id", id));
        audit.record(new RecordAuditEvent(null, admin.getId(), AuditActorType.ADMIN, "SITE_FAQ_DELETED", "SITE_FAQ", id,
                Map.of("questionEn", existing.questionEn())));
    }

    // ------------------------------------------------------------------ helpers

    private static Draft validated(Draft draft) {
        if (blank(draft.questionEn()) || blank(draft.questionKm()) || blank(draft.answerEn()) || blank(draft.answerKm())) {
            throw new IllegalArgumentException("A question needs its question and answer in English and Khmer");
        }
        if (draft.questionEn().strip().length() > 200 || draft.questionKm().strip().length() > 200) {
            throw new IllegalArgumentException("A question can be at most 200 characters in each language");
        }
        if (draft.answerEn().strip().length() > 1000 || draft.answerKm().strip().length() > 1000) {
            throw new IllegalArgumentException("An answer can be at most 1000 characters in each language");
        }
        if (draft.position() < 0) {
            throw new IllegalArgumentException("The position can't be negative");
        }
        return draft;
    }

    private static MapSqlParameterSource params(Draft draft) {
        return new MapSqlParameterSource()
                .addValue("questionEn", draft.questionEn().strip())
                .addValue("questionKm", draft.questionKm().strip())
                .addValue("answerEn", draft.answerEn().strip())
                .addValue("answerKm", draft.answerKm().strip())
                .addValue("position", draft.position())
                .addValue("visible", draft.visible());
    }

    private Faq find(UUID id) {
        return jdbc.query("SELECT * FROM site_faq WHERE id = :id", Map.of("id", id), ROW).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Question", id));
    }

    private void record(Account admin, String action, UUID id, Draft draft) {
        audit.record(new RecordAuditEvent(null, admin.getId(), AuditActorType.ADMIN, action, "SITE_FAQ", id,
                Map.of("questionEn", draft.questionEn().strip(), "visible", draft.visible())));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private Account requireAdmin(UUID accountId) {
        Account account = accounts.findById(accountId).orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        if (account.getPlatformRole() != PlatformRole.ADMIN) {
            throw new AccessDeniedException("Admin access required");
        }
        return account;
    }
}
