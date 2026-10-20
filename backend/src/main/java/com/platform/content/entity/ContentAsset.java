package com.platform.content.entity;

import com.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.UUID;

/**
 * A media file in the library (image, audio...): where it is stored, its type, size, checksum, owner and
 * description. An author's upload counts against their storage; platform media has no owner. Archiving hides it
 * from new use but keeps it for content already using it.
 */
@Entity
@Table(name = "content_assets")
public class ContentAsset extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, updatable = false, length = 30)
    private AssetType assetType;

    @NotBlank
    @Column(name = "storage_key", nullable = false, updatable = false, length = 500)
    private String storageKey;

    @NotBlank
    @Column(name = "mime_type", nullable = false, updatable = false, length = 100)
    private String mimeType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    /** The bytes' SHA-256 (V46): their identity, and the stored file's name. Null only on pre-X11 rows. */
    @Column(name = "sha256", updatable = false, length = 64)
    private String sha256;

    /** N7, as for content: null = platform media; otherwise the account whose content may use it. */
    @Column(name = "owner_account_id", updatable = false)
    private UUID ownerAccountId;

    @Column(name = "uploaded_by_account_id", updatable = false)
    private UUID uploadedByAccountId;

    /** X13: how authors find it in the library. */
    @Column(name = "title", length = 200)
    private String title;

    /** What it shows, offered as the screen-reader text wherever it is used. */
    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "width", updatable = false)
    private Integer width;

    @Column(name = "height", updatable = false)
    private Integer height;

    /** Out of the library and pickers; whatever already uses it keeps working. */
    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected ContentAsset() {
        // JPA
    }

    private ContentAsset(AssetType assetType, String storageKey, String mimeType, Long sizeBytes) {
        this.assetType = assetType;
        this.storageKey = storageKey;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
    }

    public static ContentAsset register(AssetType assetType, String storageKey, String mimeType, Long sizeBytes) {
        Assert.notNull(assetType, "assetType must not be null");
        Assert.hasText(storageKey, "storageKey must not be blank");
        Assert.hasText(mimeType, "mimeType must not be blank");
        Assert.isTrue(sizeBytes == null || sizeBytes >= 0, "sizeBytes must not be negative");
        return new ContentAsset(assetType, storageKey, mimeType, sizeBytes);
    }

    /** An uploaded file (X11): content-addressed and owned like the content that may use it. */
    public static ContentAsset uploaded(AssetType assetType, String sha256, String storageKey, String mimeType, long sizeBytes,
                                        UUID ownerAccountId, UUID uploadedByAccountId) {
        Assert.isTrue(sha256 != null && sha256.matches("[0-9a-f]{64}"), "sha256 must be 64 lowercase hex characters");
        Assert.notNull(uploadedByAccountId, "uploadedByAccountId must not be null");
        ContentAsset asset = register(assetType, storageKey, mimeType, sizeBytes);
        asset.sha256 = sha256;
        asset.ownerAccountId = ownerAccountId;
        asset.uploadedByAccountId = uploadedByAccountId;
        return asset;
    }

    /** A picture's size as inspected at upload (null for recordings). */
    public ContentAsset withDimensions(Integer width, Integer height) {
        Assert.isTrue((width == null) == (height == null), "width and height go together");
        this.width = width;
        this.height = height;
        return this;
    }

    public void describe(String title, String description) {
        this.title = title == null || title.isBlank() ? null : title.strip();
        this.description = description == null || description.isBlank() ? null : description.strip();
    }

    public void archive(Instant at) {
        if (archivedAt == null) {
            archivedAt = at;
        }
    }

    public void restore() {
        archivedAt = null;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Integer getWidth() {
        return width;
    }

    public Integer getHeight() {
        return height;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public AssetType getAssetType() {
        return assetType;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getMimeType() {
        return mimeType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public UUID getOwnerAccountId() {
        return ownerAccountId;
    }

    public UUID getUploadedByAccountId() {
        return uploadedByAccountId;
    }

}