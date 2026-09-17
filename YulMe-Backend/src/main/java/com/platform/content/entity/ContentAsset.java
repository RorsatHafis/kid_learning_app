package com.platform.content.entity;

import com.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import org.springframework.util.Assert;

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

}