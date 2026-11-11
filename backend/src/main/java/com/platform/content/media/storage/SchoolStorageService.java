package com.platform.content.media.storage;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.school.service.SchoolAccessGuard;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * M11 (D-06, owner decision 2026-11-11: hybrid storage). A school's principal chooses where its teachers' new pictures
 * and recordings are kept: Bookeyond-hosted (the default, within the plan's allowance) or the school's own
 * S3-compatible storage (paid to its provider by the school; outside Bookeyond's allowance). Connecting checks the
 * storage first (write, read back, delete a marker) and keeps nothing that failed; the access key is sealed at rest and
 * never returned. Switching only changes where new files go - every file stays readable where it was written.
 */
@Service
public class SchoolStorageService {

    private static final java.util.regex.Pattern BUCKET = java.util.regex.Pattern.compile("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]");
    private static final java.util.regex.Pattern REGION = java.util.regex.Pattern.compile("[a-z0-9-]{1,64}");

    private final SchoolAccessGuard guard;
    private final StorageConnectionRepository connections;
    private final StorageSeal seal;
    private final StorageProperties properties;
    private final StorageRouter router;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public SchoolStorageService(SchoolAccessGuard guard, StorageConnectionRepository connections, StorageSeal seal,
                                StorageProperties properties, StorageRouter router, JdbcTemplate jdbc, AuditService audit) {
        this.guard = guard;
        this.connections = connections;
        this.seal = seal;
        this.properties = properties;
        this.router = router;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    /** A connection as the principal sees it: never the access key; the key id shown only by its last characters. */
    public record ConnectionView(UUID id, String endpoint, String region, String bucket, boolean pathStyle, String accessKeyIdEnding,
                                 Instant verifiedAt, Instant lastCheckedAt, Boolean lastCheckOk, String lastCheckError,
                                 long files, long bytes) {
    }

    /** {@code current}: null = Bookeyond-hosted. {@code available}: this installation can keep a school's access key. */
    public record SchoolStorage(UUID schoolId, boolean available, ConnectionView current, List<ConnectionView> earlier,
                                long hostedFiles, long hostedBytes) {
    }

    public record Connect(String endpoint, String region, String bucket, boolean pathStyle, String accessKeyId, String accessKey) {
    }

    @Transactional(readOnly = true)
    public SchoolStorage view(UUID accountId, UUID schoolId) {
        guard.requireSchoolBilling(accountId, schoolId);
        return storageOf(schoolId);
    }

    /** Checks the school's own storage and, only if it works, makes it where new files go. */
    @Transactional
    public SchoolStorage connect(UUID accountId, UUID schoolId, Connect request) {
        guard.requireSchoolBilling(accountId, schoolId);
        if (!seal.available()) {
            throw new IllegalStateException("Connecting a school's own storage isn't set up on this installation.");
        }
        URI endpoint = EndpointPolicy.check(request.endpoint(), properties.allowPrivateEndpoints());
        String region = request.region() == null || request.region().isBlank() ? "us-east-1" : request.region().strip().toLowerCase();
        String bucket = request.bucket() == null ? "" : request.bucket().strip();
        String keyId = request.accessKeyId() == null ? "" : request.accessKeyId().strip();
        String key = request.accessKey() == null ? "" : request.accessKey().strip();
        if (!REGION.matcher(region).matches()) {
            throw new IllegalArgumentException("Enter the region as it appears at your storage provider, like us-east-1.");
        }
        if (!BUCKET.matcher(bucket).matches()) {
            throw new IllegalArgumentException("Enter the bucket's name: 3 to 63 lowercase letters, numbers, dots or dashes.");
        }
        if (keyId.isEmpty() || keyId.length() > 128 || key.isEmpty() || key.length() > 256) {
            throw new IllegalArgumentException("Enter the access key ID and the secret access key from your storage provider.");
        }
        try (S3StorageProvider candidate = new S3StorageProvider(endpoint, region, bucket, request.pathStyle(), keyId, key)) {
            Optional<String> failed = candidate.check();
            if (failed.isPresent()) {
                throw new StorageCheckFailedException(failed.get());
            }
        }
        String context = StorageConnection.sealContext(schoolId, endpoint.toString(), bucket, keyId);
        StorageConnection connection = connections.saveAndFlush(StorageConnection.verified(schoolId, endpoint.toString(), region, bucket,
                request.pathStyle(), keyId, seal.seal(key, context), accountId, Instant.now()));
        jdbc.update("update schools set storage_connection_id = ? where id = ?", connection.getId(), schoolId);
        record(accountId, schoolId, "SCHOOL_STORAGE_CONNECTED", Map.of("connectionId", connection.getId().toString(),
                "endpoint", endpoint.toString(), "bucket", bucket));
        return storageOf(schoolId);
    }

    /** New files go to Bookeyond-hosted storage again; files already in the school's storage stay readable there. */
    @Transactional
    public SchoolStorage useHosted(UUID accountId, UUID schoolId) {
        guard.requireSchoolBilling(accountId, schoolId);
        UUID before = currentConnection(schoolId);
        if (before != null) {
            jdbc.update("update schools set storage_connection_id = null where id = ?", schoolId);
            record(accountId, schoolId, "SCHOOL_STORAGE_HOSTED", Map.of("previousConnectionId", before.toString()));
        }
        return storageOf(schoolId);
    }

    /** Checks the current connection again (write, read, delete a marker) and records the outcome. */
    @Transactional
    public SchoolStorage check(UUID accountId, UUID schoolId) {
        guard.requireSchoolBilling(accountId, schoolId);
        UUID current = currentConnection(schoolId);
        if (current == null) {
            return storageOf(schoolId);
        }
        StorageConnection connection = connections.findById(current).orElseThrow();
        String error;
        router.forget(current);
        try (S3StorageProvider provider = S3StorageProvider.of(connection, seal.open(connection.getAccessKeySealed(), connection.sealContext()),
                properties.allowPrivateEndpoints())) {
            error = provider.check().orElse(null);
        } catch (IllegalArgumentException | IllegalStateException e) {
            error = e.getMessage();
        }
        connection.checked(Instant.now(), error == null ? null : error.substring(0, Math.min(300, error.length())));
        return storageOf(schoolId);
    }

    private SchoolStorage storageOf(UUID schoolId) {
        UUID current = currentConnection(schoolId);
        Map<UUID, long[]> usage = new LinkedHashMap<>();
        jdbc.query("""
                SELECT a.storage_connection_id, count(*), COALESCE(SUM(a.size_bytes), 0) FROM content_assets a
                WHERE a.sha256 IS NOT NULL AND a.storage_connection_id IN (SELECT id FROM storage_connections WHERE school_id = ?)
                GROUP BY a.storage_connection_id
                """, rs -> {
            usage.put(rs.getObject(1, UUID.class), new long[]{rs.getLong(2), rs.getLong(3)});
        }, schoolId);
        List<ConnectionView> all = connections.findBySchoolIdOrderByCreatedAtDesc(schoolId).stream()
                .map(c -> view(c, usage.getOrDefault(c.getId(), new long[2]))).toList();
        // What the school's authors keep on Bookeyond-hosted storage (their own files, any school).
        long[] hosted = jdbc.queryForObject("""
                SELECT count(*), COALESCE(SUM(a.size_bytes), 0) FROM content_assets a
                WHERE a.sha256 IS NOT NULL AND a.storage_connection_id IS NULL AND a.owner_account_id IN (
                    SELECT st.account_id FROM school_staff st WHERE st.school_id = ? AND st.status = 'ACTIVE'
                    UNION SELECT s.principal_account_id FROM schools s WHERE s.id = ? AND s.principal_account_id IS NOT NULL)
                """, (rs, i) -> new long[]{rs.getLong(1), rs.getLong(2)}, schoolId, schoolId);
        return new SchoolStorage(schoolId, seal.available(),
                all.stream().filter(c -> c.id().equals(current)).findFirst().orElse(null),
                all.stream().filter(c -> !c.id().equals(current)).toList(), hosted[0], hosted[1]);
    }

    private static ConnectionView view(StorageConnection c, long[] usage) {
        String keyId = c.getAccessKeyId();
        return new ConnectionView(c.getId(), c.getEndpoint(), c.getRegion(), c.getBucket(), c.isPathStyle(),
                keyId.length() <= 4 ? "…" : "…" + keyId.substring(keyId.length() - 4), c.getVerifiedAt(), c.getLastCheckedAt(),
                c.getLastCheckOk(), c.getLastCheckError(), usage[0], usage[1]);
    }

    private UUID currentConnection(UUID schoolId) {
        // A school on Bookeyond-hosted storage has a NULL here: one row holding null, not an empty result.
        return jdbc.queryForList("select storage_connection_id from schools where id = ?", UUID.class, schoolId).stream()
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    private void record(UUID accountId, UUID schoolId, String action, Map<String, Object> details) {
        boolean admin = "ADMIN".equals(jdbc.queryForList("select platform_role from accounts where id = ?", String.class, accountId)
                .stream().findFirst().orElse(null));
        audit.record(new RecordAuditEvent(null, accountId, admin ? AuditActorType.ADMIN : AuditActorType.PRINCIPAL, action, "SCHOOL",
                schoolId, details));
    }
}
