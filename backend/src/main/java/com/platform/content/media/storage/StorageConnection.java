package com.platform.content.media.storage;

import com.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * M11 (V94): a school's own S3-compatible storage, as connected once - never edited (connecting again makes a new
 * one), so every file stays readable from where it was written. The access key is sealed ({@link StorageSeal}).
 */
@Entity
@Table(name = "storage_connections")
public class StorageConnection extends BaseEntity {

    @Column(name = "school_id", nullable = false, updatable = false)
    private UUID schoolId;

    @Column(name = "endpoint", nullable = false, updatable = false)
    private String endpoint;

    @Column(name = "region", nullable = false, updatable = false)
    private String region;

    @Column(name = "bucket", nullable = false, updatable = false)
    private String bucket;

    @Column(name = "path_style", nullable = false, updatable = false)
    private boolean pathStyle;

    @Column(name = "access_key_id", nullable = false, updatable = false)
    private String accessKeyId;

    @Column(name = "access_key_sealed", nullable = false, updatable = false, columnDefinition = "text")
    private String accessKeySealed;

    @Column(name = "connected_by")
    private UUID connectedBy;

    @Column(name = "verified_at", nullable = false, updatable = false)
    private Instant verifiedAt;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "last_check_ok")
    private Boolean lastCheckOk;

    @Column(name = "last_check_error")
    private String lastCheckError;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected StorageConnection() {
    }

    /** The sealed key is bound to {@link #sealContext}: the school, address, bucket and key id it was given for. */
    public static StorageConnection verified(UUID schoolId, String endpoint, String region, String bucket, boolean pathStyle,
                                             String accessKeyId, String accessKeySealed, UUID connectedBy, Instant at) {
        StorageConnection c = new StorageConnection();
        c.schoolId = schoolId;
        c.endpoint = endpoint;
        c.region = region;
        c.bucket = bucket;
        c.pathStyle = pathStyle;
        c.accessKeyId = accessKeyId;
        c.accessKeySealed = accessKeySealed;
        c.connectedBy = connectedBy;
        c.verifiedAt = at;
        c.lastCheckedAt = at;
        c.lastCheckOk = true;
        return c;
    }

    /** What a sealed key is bound to: it opens only for the connection it was sealed for. */
    public static String sealContext(UUID schoolId, String endpoint, String bucket, String accessKeyId) {
        return schoolId + "|" + endpoint + "|" + bucket + "|" + accessKeyId;
    }

    public String sealContext() {
        return sealContext(schoolId, endpoint, bucket, accessKeyId);
    }

    public void checked(Instant at, String error) {
        this.lastCheckedAt = at;
        this.lastCheckOk = error == null;
        this.lastCheckError = error;
    }

    public UUID getSchoolId() { return schoolId; }
    public String getEndpoint() { return endpoint; }
    public String getRegion() { return region; }
    public String getBucket() { return bucket; }
    public boolean isPathStyle() { return pathStyle; }
    public String getAccessKeyId() { return accessKeyId; }
    public String getAccessKeySealed() { return accessKeySealed; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public Instant getLastCheckedAt() { return lastCheckedAt; }
    public Boolean getLastCheckOk() { return lastCheckOk; }
    public String getLastCheckError() { return lastCheckError; }
}
