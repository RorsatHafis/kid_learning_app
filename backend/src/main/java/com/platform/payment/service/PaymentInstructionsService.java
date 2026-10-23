package com.platform.payment.service;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.content.entity.AssetType;
import com.platform.content.entity.ContentAsset;
import com.platform.content.repository.ContentAssetRepository;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import com.platform.payment.config.PaymentProperties;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * M8b: how a customer pays by bank transfer - the owner's own instructions in English and Khmer, and optionally the
 * owner's genuine KHQR image - set by an administrator. Append-only versions: an order keeps the version it was started
 * with, so a change never rewrites an invoice already issued. Until the first version is saved, the PAYMENTS_MANUAL_*
 * environment variables apply. Saving instructions never confirms a payment or grants anything: a transfer is still
 * confirmed only by an administrator against the real receipt (PaymentService#confirmManual).
 */
@Service
public class PaymentInstructionsService {

    static final int MAX_LENGTH = 2000;
    private static final String METHOD = "MANUAL";

    /**
     * One version of the instructions; {@code id} and {@code version} are null for the environment's (not yet saved in
     * the app).
     */
    public record Instructions(UUID id, Integer version, boolean enabled, String instructionsEn, String instructionsKm,
                               UUID qrAssetId, Instant createdAt) {

        public boolean available() {
            return enabled && instructionsEn != null && !instructionsEn.isBlank();
        }

        public boolean fromEnvironment() {
            return id == null;
        }
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final AccountRepository accounts;
    private final ContentAssetRepository assets;
    private final AuditService audit;
    private final PaymentProperties properties;

    public PaymentInstructionsService(NamedParameterJdbcTemplate jdbc, AccountRepository accounts, ContentAssetRepository assets,
                                      AuditService audit, PaymentProperties properties) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.assets = assets;
        this.audit = audit;
        this.properties = properties;
    }

    /** What applies now: the latest saved version, or the environment's until one is saved. */
    @Transactional(readOnly = true)
    public Instructions current() {
        return latest().orElseGet(() -> new Instructions(null, null, properties.manual().enabled(),
                blankToNull(properties.manual().instructions()), null, null, null));
    }

    /** The version an order was started with (null: before M8b, or the environment's - then what applies now). */
    @Transactional(readOnly = true)
    public Instructions forOrder(UUID versionId) {
        if (versionId == null) {
            return current();
        }
        return query("WHERE id = :id", Map.of("id", versionId)).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Payment instructions", versionId));
    }

    /** Every saved version, newest first (administrators). */
    @Transactional(readOnly = true)
    public List<Instructions> history(UUID adminId) {
        requireAdmin(adminId);
        return query("WHERE method = :m ORDER BY version DESC", Map.of("m", METHOD));
    }

    /**
     * Saves a new version. {@code expectedVersion} is the version the administrator edited (null for the first), so two
     * administrators saving at once can't silently overwrite each other (409). The image must be one the platform keeps
     * (uploaded in the media library, not archived).
     */
    @Transactional
    public Instructions save(UUID adminId, Integer expectedVersion, boolean enabled, String instructionsEn, String instructionsKm,
                             UUID qrAssetId) {
        requireAdmin(adminId);
        String en = blankToNull(instructionsEn);
        String km = blankToNull(instructionsKm);
        if (enabled && en == null) {
            throw new IllegalArgumentException("Write the instructions in English before offering bank transfer");
        }
        if ((en != null && en.length() > MAX_LENGTH) || (km != null && km.length() > MAX_LENGTH)) {
            throw new IllegalArgumentException("Payment instructions can be at most 2000 characters");
        }
        if (qrAssetId != null) {
            ContentAsset image = assets.findById(qrAssetId).orElseThrow(() -> new ResourceNotFoundException("Media", qrAssetId));
            if (image.getAssetType() != AssetType.IMAGE || image.getOwnerAccountId() != null || image.isArchived()) {
                throw new IllegalArgumentException("The QR code must be a picture in the platform's media library");
            }
        }
        // One save at a time (the table may still be empty, so no row to lock).
        jdbc.queryForObject("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended('payment-instructions:' || :m, 0))) locked",
                Map.of("m", METHOD), Integer.class);
        Integer latest = latest().map(Instructions::version).orElse(null);
        if (!java.util.Objects.equals(latest, expectedVersion)) {
            throw new IllegalStateException("These payment instructions were changed meanwhile: reload them before saving");
        }
        UUID id = UUID.randomUUID();
        int version = latest == null ? 1 : latest + 1;
        jdbc.update("""
                INSERT INTO payment_instruction_versions
                    (id, method, version, enabled, instructions_en, instructions_km, qr_asset_id, created_by_account_id)
                VALUES (:id, :m, :v, :enabled, :en, :km, :qr, :by)
                """, new MapSqlParameterSource("id", id).addValue("m", METHOD).addValue("v", version).addValue("enabled", enabled)
                .addValue("en", en).addValue("km", km).addValue("qr", qrAssetId).addValue("by", adminId));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("version", version);
        details.put("enabled", enabled);
        details.put("qrImage", qrAssetId == null ? "none" : qrAssetId.toString());
        details.put("englishCharacters", en == null ? 0 : en.length());
        details.put("khmerCharacters", km == null ? 0 : km.length());
        audit.record(new RecordAuditEvent(null, adminId, AuditActorType.ADMIN, "PAYMENT_INSTRUCTIONS_CHANGED", "PAYMENT_METHOD", id, details));
        return latest().orElseThrow();
    }

    // ------------------------------------------------------------------ helpers

    private Optional<Instructions> latest() {
        return query("WHERE method = :m ORDER BY version DESC LIMIT 1", Map.of("m", METHOD)).stream().findFirst();
    }

    private List<Instructions> query(String where, Map<String, Object> params) {
        return jdbc.query("SELECT id, version, enabled, instructions_en, instructions_km, qr_asset_id, created_at "
                        + "FROM payment_instruction_versions " + where, params,
                (rs, i) -> new Instructions(rs.getObject(1, UUID.class), rs.getInt(2), rs.getBoolean(3), rs.getString(4),
                        rs.getString(5), rs.getObject(6, UUID.class), rs.getTimestamp(7).toInstant()));
    }

    private void requireAdmin(UUID accountId) {
        Account account = accounts.findById(accountId).orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        if (account.getPlatformRole() != PlatformRole.ADMIN) {
            throw new AccessDeniedException("Admin access required");
        }
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

}
