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
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * F1a: the public pages' words, editable by administrators without a code change. The frontend's message catalogs hold
 * the default text; an override for one entry (English, Khmer or both) is stored here and laid over it, and removing it
 * puts the default back. Only the public pages' catalogs can be overridden; text is plain (React renders it as text,
 * and control characters are refused), with {1}, {2}... standing for an entry's values.
 */
@Service
public class SiteCopyService {

    /** The catalogs of the landing page, the trust page and the public demo. */
    static final Set<String> NAMESPACES = Set.of("features/marketing", "features/marketing/trust", "features/marketing/try",
            "features/marketing/parentDemo");
    static final int MAX_TEXT = 600;
    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*$");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\n]]");

    public record Copy(String namespace, String key, String textEn, String textKm, long version, Instant updatedAt) {
    }

    /** What the public pages need: the overrides, nothing about who wrote them. */
    public record PublicCopy(String namespace, String key, String textEn, String textKm) {
    }

    private static final RowMapper<Copy> ROW = (rs, i) -> new Copy(rs.getString("namespace"), rs.getString("copy_key"),
            rs.getString("text_en"), rs.getString("text_km"), rs.getLong("version"), rs.getTimestamp("updated_at").toInstant());

    private final NamedParameterJdbcTemplate jdbc;
    private final AccountRepository accounts;
    private final AuditService audit;

    public SiteCopyService(NamedParameterJdbcTemplate jdbc, AccountRepository accounts, AuditService audit) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<PublicCopy> published() {
        return jdbc.query("SELECT * FROM site_copy ORDER BY namespace, copy_key", Map.of(), ROW).stream()
                .map(c -> new PublicCopy(c.namespace(), c.key(), c.textEn(), c.textKm())).toList();
    }

    @Transactional(readOnly = true)
    public List<Copy> all(UUID adminId) {
        requireAdmin(adminId);
        return jdbc.query("SELECT * FROM site_copy ORDER BY namespace, copy_key", Map.of(), ROW);
    }

    /**
     * Sets one entry's words (a blank language keeps its default). {@code expectedVersion}: null to create, else the
     * version the administrator edited - a stale one is refused rather than overwriting someone else's change.
     */
    @Transactional
    public Copy put(UUID adminId, String namespace, String key, String textEn, String textKm, Long expectedVersion) {
        Account admin = requireAdmin(adminId);
        requireEditable(namespace, key);
        String en = clean(textEn);
        String km = clean(textKm);
        if (en == null && km == null) {
            throw new IllegalArgumentException("Write the text in English, Khmer or both - or reset the entry to its default.");
        }
        MapSqlParameterSource params = new MapSqlParameterSource("ns", namespace).addValue("key", key).addValue("en", en)
                .addValue("km", km).addValue("by", admin.getId());
        int changed;
        if (expectedVersion == null) {
            changed = jdbc.update("""
                    INSERT INTO site_copy (namespace, copy_key, text_en, text_km, updated_by) VALUES (:ns, :key, :en, :km, :by)
                    ON CONFLICT (namespace, copy_key) DO NOTHING
                    """, params);
        } else {
            changed = jdbc.update("""
                    UPDATE site_copy SET text_en = :en, text_km = :km, updated_by = :by, version = version + 1
                    WHERE namespace = :ns AND copy_key = :key AND version = :version
                    """, params.addValue("version", expectedVersion));
        }
        if (changed == 0) {
            throw new IllegalStateException("This text was changed by someone else meanwhile. Reload it and try again.");
        }
        record(admin, "SITE_COPY_CHANGED", namespace, key);
        return find(namespace, key);
    }

    /** Puts the default back. */
    @Transactional
    public void reset(UUID adminId, String namespace, String key) {
        Account admin = requireAdmin(adminId);
        requireEditable(namespace, key);
        if (jdbc.update("DELETE FROM site_copy WHERE namespace = :ns AND copy_key = :key", Map.of("ns", namespace, "key", key)) == 0) {
            throw new ResourceNotFoundException("Site copy", namespace + ":" + key);
        }
        record(admin, "SITE_COPY_RESET", namespace, key);
    }

    private Copy find(String namespace, String key) {
        return jdbc.query("SELECT * FROM site_copy WHERE namespace = :ns AND copy_key = :key", Map.of("ns", namespace, "key", key), ROW)
                .stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Site copy", namespace + ":" + key));
    }

    static void requireEditable(String namespace, String key) {
        if (!NAMESPACES.contains(namespace)) {
            throw new IllegalArgumentException("Only the public pages' words can be edited here.");
        }
        if (key == null || key.length() > 120 || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Not a text entry's key.");
        }
    }

    static String clean(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String stripped = text.strip();
        if (stripped.length() > MAX_TEXT) {
            throw new IllegalArgumentException("A text can be at most 600 characters in each language.");
        }
        if (CONTROL.matcher(stripped).find()) {
            throw new IllegalArgumentException("The text contains characters that can't be shown.");
        }
        return stripped;
    }

    private void record(Account admin, String action, String namespace, String key) {
        Map<String, Object> details = new HashMap<>();
        details.put("namespace", namespace);
        details.put("key", key);
        audit.record(new RecordAuditEvent(null, admin.getId(), AuditActorType.ADMIN, action, "SITE_COPY", null, details));
    }

    private Account requireAdmin(UUID accountId) {
        Account account = accounts.findById(accountId).orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        if (account.getPlatformRole() != PlatformRole.ADMIN) {
            throw new AccessDeniedException("Admin access required");
        }
        return account;
    }
}
