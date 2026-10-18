package com.platform.content.media;

import com.platform.common.web.ResourceNotFoundException;
import com.platform.content.entity.AssetType;
import com.platform.content.entity.ContentAsset;
import com.platform.content.repository.ContentAssetRepository;
import com.platform.content.service.ContentAccessGuard;
import com.platform.content.service.ContentOwnership;
import com.platform.identity.entity.Account;
import com.platform.security.SecurityProperties;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Pictures and recordings educators upload for questions (X11).
 *
 * <p>Uploading: staff only, inspected from the bytes ({@link MediaInspector}), owned as the
 * author's content is (N7: an administrator's uploads are platform media) and deduplicated per
 * owner. Attaching: an asset may be used by content its owner's content could use
 * ({@link ContentOwnership#usableBy}), of the kind the field needs.
 *
 * <p>Serving: a child's browser loads media with a plain {@code <img>}/{@code <audio>}, which can't
 * carry the access token, so media is reached through a URL signed by the server - issued only
 * inside a response the caller was already allowed to read - that expires within hours. The
 * expiry is rounded to the hour so a URL stays the same, and cacheable, for an hour.
 */
@Service
public class MediaService {

    static final long URL_LIFETIME_HOURS = 7;

    private final ContentAssetRepository assets;
    private final MediaStorage storage;
    private final ContentAccessGuard guard;
    private final StorageService storageAllowance;
    private final com.platform.identity.repository.AccountRepository accounts;
    private final byte[] urlKey;

    public MediaService(ContentAssetRepository assets, MediaStorage storage, ContentAccessGuard guard, SecurityProperties security,
                        StorageService storageAllowance, com.platform.identity.repository.AccountRepository accounts) {
        this.storageAllowance = storageAllowance;
        this.accounts = accounts;
        this.assets = assets;
        this.storage = storage;
        this.guard = guard;
        // A key of its own, derived from the token secret: a media URL can never be mistaken for a token.
        this.urlKey = hmac(security.jwt().secret().getBytes(StandardCharsets.UTF_8),
                "bookeyond-media-url-v1".getBytes(StandardCharsets.UTF_8));
    }

    /** A file as content shows it: what it is and where the browser fetches it. */
    public record MediaRef(UUID id, AssetType type, String mimeType, String url) {
    }

    @Transactional
    public MediaRef upload(UUID accountId, byte[] bytes) {
        return ref(uploadAsset(accountId, bytes, null));
    }

    /**
     * Uploads, or finds the owner's asset for the same bytes (X13: uploading a file again reuses it, and
     * brings it back to the library if it was archived). A new asset is titled after its file name.
     */
    @Transactional
    public ContentAsset uploadAsset(UUID accountId, byte[] bytes, String fileName) {
        Account author = guard.requireAuthor(accountId);
        MediaInspector.Inspected inspected = MediaInspector.inspect(bytes);
        String sha256 = sha256(bytes);
        UUID owner = guard.authoringOwner(author);
        if (owner != null) {
            // One upload at a time per owner (Y35): two at once can't both take the last free space.
            accounts.findForUpdate(owner);
        }
        ContentAsset asset = (owner == null ? assets.findBySha256AndOwnerAccountIdIsNull(sha256)
                : assets.findBySha256AndOwnerAccountId(sha256, owner))
                .orElseGet(() -> {
                    // A file the owner doesn't keep yet: it must fit their allowance, checked before it is stored.
                    storageAllowance.requireRoom(owner, bytes.length);
                    ContentAsset created = ContentAsset.uploaded(inspected.type(), sha256, storage.store(sha256, bytes),
                            inspected.mimeType(), bytes.length, owner, author.getId())
                            .withDimensions(inspected.width(), inspected.height());
                    created.describe(titleFrom(fileName), null);
                    return created;
                });
        asset.restore();
        return assets.saveAndFlush(asset);
    }

    /** "three-apples_v2.png" -> "three apples v2": a readable starting title, at most 200 characters. */
    static String titleFrom(String fileName) {
        if (fileName == null) {
            return null;
        }
        String normalized = fileName.replace('\\', '/');
        String base = normalized.substring(normalized.lastIndexOf('/') + 1);
        int dot = base.lastIndexOf('.');
        String title = (dot > 0 ? base.substring(0, dot) : base).replaceAll("[_\\-]+", " ").replaceAll("\\s+", " ").strip();
        return title.isEmpty() ? null : title.substring(0, Math.min(200, title.length()));
    }

    /**
     * The asset a field of content owned by {@code contentOwner} may use, or refusal: an unknown
     * asset (404), another author's (403), or the wrong kind - a recording where a picture goes (400).
     */
    @Transactional(readOnly = true)
    public void requireAttachable(UUID assetId, AssetType expected, UUID contentOwner) {
        requireAttachable(assetId, expected, contentOwner, java.util.Set.of());
    }

    /**
     * X13: an archived asset can't be newly attached, but a draft that already uses it keeps it when it is
     * saved again ({@code alreadyOnDraft}) - archiving never blocks editing the content that uses it.
     */
    @Transactional(readOnly = true)
    public void requireAttachable(UUID assetId, AssetType expected, UUID contentOwner, java.util.Set<UUID> alreadyOnDraft) {
        if (assetId == null) {
            return;
        }
        ContentAsset asset = assets.findById(assetId).orElseThrow(() -> new ResourceNotFoundException("Media", assetId));
        if (asset.getSha256() == null || !ContentOwnership.usableBy(asset.getOwnerAccountId(), contentOwner)) {
            throw new AccessDeniedException("That media belongs to another author");
        }
        if (asset.isArchived() && !alreadyOnDraft.contains(assetId)) {
            throw new IllegalStateException("That media is archived. Restore it in the media library to use it again.");
        }
        if (asset.getAssetType() != expected) {
            throw new IllegalArgumentException(expected == AssetType.IMAGE ? "That file is a recording, not a picture."
                    : "That file is a picture, not a recording.");
        }
    }

    /** The assets' references, read in one query (a question's prompt and options together). */
    @Transactional(readOnly = true)
    public Map<UUID, MediaRef> refs(Collection<UUID> assetIds) {
        // A HashMap: callers look up fields that may hold no asset (get(null) is simply absent).
        Map<UUID, MediaRef> refs = new HashMap<>();
        var ids = assetIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (!ids.isEmpty()) {
            assets.findAllById(ids).forEach(asset -> refs.put(asset.getId(), ref(asset)));
        }
        return refs;
    }

    /** The stored file behind a signed URL, or empty for anything not exactly as issued and unexpired. */
    @Transactional(readOnly = true)
    public java.util.Optional<Served> open(UUID assetId, long expiresAt, String signature) {
        if (expiresAt < Instant.now().getEpochSecond() || signature == null
                || !MessageDigest.isEqual(sign(assetId, expiresAt).getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII))) {
            return java.util.Optional.empty();
        }
        return assets.findById(assetId).filter(asset -> asset.getSha256() != null)
                .map(asset -> new Served(storage.resolve(asset.getStorageKey()), asset.getMimeType(), expiresAt));
    }

    public record Served(Path file, String mimeType, long expiresAt) {
    }

    MediaRef ref(ContentAsset asset) {
        long expiresAt = (Instant.now().getEpochSecond() / 3600 + URL_LIFETIME_HOURS) * 3600;
        return new MediaRef(asset.getId(), asset.getAssetType(), asset.getMimeType(),
                "/api/v1/media/" + asset.getId() + "?e=" + expiresAt + "&s=" + sign(asset.getId(), expiresAt));
    }

    private String sign(UUID assetId, long expiresAt) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(hmac(urlKey, (assetId + ":" + expiresAt).getBytes(StandardCharsets.US_ASCII)));
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

}
