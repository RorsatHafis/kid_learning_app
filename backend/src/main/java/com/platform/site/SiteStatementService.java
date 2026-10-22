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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the landing page says in its statements section (M2), managed by administrators.
 *
 * <p>A BRAND statement is Bookeyond's own words. A TESTIMONIAL is a real person's words: it needs who said it, a note of
 * where their permission is recorded, and the administrator's confirmation that it is genuine - the product never
 * shows an invented endorsement. Every change is audited. Visitors see only visible statements, never source notes.
 */
@Service
public class SiteStatementService {

    public enum Kind { BRAND, TESTIMONIAL }

    public record Statement(UUID id, Kind kind, String textEn, String textKm, String attributionEn, String attributionKm,
                            String sourceNote, int position, boolean visible, long version, Instant updatedAt) {
    }

    /** What a visitor sees: no source note, no bookkeeping. */
    public record PublicStatement(UUID id, Kind kind, String textEn, String textKm, String attributionEn, String attributionKm) {
    }

    public record Draft(Kind kind, String textEn, String textKm, String attributionEn, String attributionKm, String sourceNote,
                        int position, boolean visible, boolean confirmedGenuine) {
    }

    private static final RowMapper<Statement> ROW = (rs, i) -> new Statement(rs.getObject("id", UUID.class),
            Kind.valueOf(rs.getString("kind")), rs.getString("text_en"), rs.getString("text_km"), rs.getString("attribution_en"),
            rs.getString("attribution_km"), rs.getString("source_note"), rs.getInt("position"), rs.getBoolean("visible"),
            rs.getLong("version"), rs.getTimestamp("updated_at").toInstant());

    private final NamedParameterJdbcTemplate jdbc;
    private final AccountRepository accounts;
    private final AuditService audit;

    public SiteStatementService(NamedParameterJdbcTemplate jdbc, AccountRepository accounts, AuditService audit) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<PublicStatement> visible() {
        return jdbc.query("SELECT * FROM site_statements WHERE visible ORDER BY position, created_at", Map.of(), ROW).stream()
                .map(s -> new PublicStatement(s.id(), s.kind(), s.textEn(), s.textKm(), s.attributionEn(), s.attributionKm()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Statement> all(UUID adminId) {
        requireAdmin(adminId);
        return jdbc.query("SELECT * FROM site_statements ORDER BY position, created_at", Map.of(), ROW);
    }

    @Transactional
    public Statement create(UUID adminId, Draft draft) {
        Account admin = requireAdmin(adminId);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO site_statements (id, kind, text_en, text_km, attribution_en, attribution_km, source_note, position, visible)
                VALUES (:id, :kind, :textEn, :textKm, :attributionEn, :attributionKm, :sourceNote, :position, :visible)
                """, params(validated(draft)).addValue("id", id));
        record(admin, "SITE_STATEMENT_CREATED", id, draft);
        return find(id);
    }

    /** Replaces a statement's fields; {@code expectedVersion} refuses an edit made over someone else's (409). */
    @Transactional
    public Statement update(UUID adminId, UUID id, long expectedVersion, Draft draft) {
        Account admin = requireAdmin(adminId);
        int changed = jdbc.update("""
                UPDATE site_statements SET kind = :kind, text_en = :textEn, text_km = :textKm, attribution_en = :attributionEn,
                       attribution_km = :attributionKm, source_note = :sourceNote, position = :position, visible = :visible,
                       version = version + 1
                WHERE id = :id AND version = :version
                """, params(validated(draft)).addValue("id", id).addValue("version", expectedVersion));
        if (changed == 0) {
            find(id); // 404 when it doesn't exist
            throw new IllegalStateException("This statement was changed by someone else meanwhile. Reload it and try again.");
        }
        record(admin, "SITE_STATEMENT_UPDATED", id, draft);
        return find(id);
    }

    @Transactional
    public void delete(UUID adminId, UUID id) {
        Account admin = requireAdmin(adminId);
        Statement existing = find(id);
        jdbc.update("DELETE FROM site_statements WHERE id = :id", Map.of("id", id));
        Map<String, Object> details = new HashMap<>();
        details.put("kind", existing.kind().name());
        details.put("textEn", existing.textEn());
        audit.record(new RecordAuditEvent(null, admin.getId(), AuditActorType.ADMIN, "SITE_STATEMENT_DELETED", "SITE_STATEMENT", id,
                details));
    }

    // ------------------------------------------------------------------ helpers

    private Draft validated(Draft draft) {
        if (draft.kind() == null) {
            throw new IllegalArgumentException("Choose whether this is Bookeyond's own statement or a testimonial");
        }
        if (blank(draft.textEn()) || blank(draft.textKm())) {
            throw new IllegalArgumentException("A statement needs its English and Khmer text");
        }
        if (draft.textEn().strip().length() > 280 || draft.textKm().strip().length() > 280) {
            throw new IllegalArgumentException("A statement can be at most 280 characters in each language");
        }
        if (draft.position() < 0) {
            throw new IllegalArgumentException("The position can't be negative");
        }
        if (draft.kind() == Kind.TESTIMONIAL && (blank(draft.attributionEn()) || blank(draft.attributionKm())
                || blank(draft.sourceNote()) || !draft.confirmedGenuine())) {
            throw new IllegalArgumentException("A testimonial needs who said it (in both languages), where their permission is "
                    + "recorded, and your confirmation that it is genuine");
        }
        return draft;
    }

    private static MapSqlParameterSource params(Draft draft) {
        boolean testimonial = draft.kind() == Kind.TESTIMONIAL;
        return new MapSqlParameterSource()
                .addValue("kind", draft.kind().name())
                .addValue("textEn", draft.textEn().strip())
                .addValue("textKm", draft.textKm().strip())
                .addValue("attributionEn", strippedOrNull(testimonial ? draft.attributionEn() : null))
                .addValue("attributionKm", strippedOrNull(testimonial ? draft.attributionKm() : null))
                .addValue("sourceNote", strippedOrNull(testimonial ? draft.sourceNote() : null))
                .addValue("position", draft.position())
                .addValue("visible", draft.visible());
    }

    private Statement find(UUID id) {
        return jdbc.query("SELECT * FROM site_statements WHERE id = :id", Map.of("id", id), ROW).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Statement", id));
    }

    private void record(Account admin, String action, UUID id, Draft draft) {
        Map<String, Object> details = new HashMap<>();
        details.put("kind", draft.kind().name());
        details.put("visible", draft.visible());
        if (draft.kind() == Kind.TESTIMONIAL) {
            details.put("confirmedGenuine", true);
        }
        audit.record(new RecordAuditEvent(null, admin.getId(), AuditActorType.ADMIN, action, "SITE_STATEMENT", id, details));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String strippedOrNull(String value) {
        return blank(value) ? null : value.strip();
    }

    private Account requireAdmin(UUID accountId) {
        Account account = accounts.findById(accountId).orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        if (account.getPlatformRole() != PlatformRole.ADMIN) {
            throw new AccessDeniedException("Admin access required");
        }
        return account;
    }

}
