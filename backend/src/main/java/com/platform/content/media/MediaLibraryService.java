package com.platform.content.media;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.common.entity.PublicationStatus;
import com.platform.common.web.PageResponse;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.common.web.SearchText;
import com.platform.content.entity.AssetType;
import com.platform.content.entity.ContentAsset;
import com.platform.content.entity.Question;
import com.platform.content.entity.QuestionVersion;
import com.platform.content.repository.ContentAssetRepository;
import com.platform.content.repository.QuestionRepository;
import com.platform.content.repository.QuestionVersionRepository;
import com.platform.content.service.ContentAccessGuard;
import com.platform.content.service.ContentOwnership;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The media library (X13): pictures and recordings authors reuse instead of uploading again. An author
 * sees what their content may use - platform media and their own - finds it by title or description,
 * sees where each file is used, describes it once (the description is offered as screen-reader text
 * wherever it goes), and takes it out of the library (archive) or deletes it when nothing uses it.
 *
 * <p>Nothing here changes content: a published question keeps exactly the files it was published with
 * (V46 RESTRICT foreign keys), archiving only stops new attachments, and a file in use can't be deleted.
 */
@Service
public class MediaLibraryService {

    public record LibraryFilter(AssetType type, String text, boolean archived, boolean unused) {
    }

    /** {@code usedBy}: how many question versions use it; {@code editable}: whether this author may change it. */
    public record MediaItem(UUID id, AssetType type, String mimeType, String url, long sizeBytes, Integer width, Integer height,
                            String title, String description, boolean archived, Instant createdAt, long usedBy, boolean editable) {
    }

    public enum UseField {
        PROMPT_IMAGE,
        PROMPT_AUDIO,
        ANSWER_IMAGE,
        ANSWER_AUDIO
    }

    /** One question version that uses the file, and where. */
    public record MediaUse(UUID questionId, UUID subjectId, UUID versionId, int versionNumber, PublicationStatus status, String prompt,
                           UseField field) {
    }

    public record MediaDetail(MediaItem item, List<MediaUse> uses) {
    }

    private final ContentAssetRepository assets;
    private final MediaService media;
    private final ContentAccessGuard guard;
    private final QuestionVersionRepository questionVersions;
    private final QuestionRepository questions;
    private final AuditService audit;

    public MediaLibraryService(ContentAssetRepository assets, MediaService media, ContentAccessGuard guard,
                               QuestionVersionRepository questionVersions, QuestionRepository questions, AuditService audit) {
        this.assets = assets;
        this.media = media;
        this.guard = guard;
        this.questionVersions = questionVersions;
        this.questions = questions;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<MediaItem> library(UUID accountId, LibraryFilter filter, PageResponse.Request request) {
        Account author = guard.requireAuthor(accountId);
        UUID owner = guard.authoringOwner(author);
        String type = filter.type() == null ? null : filter.type().name();
        String pattern = SearchText.containsPattern(filter.text());
        long total = assets.countLibrary(owner, type, pattern, filter.archived(), filter.unused());
        List<UUID> ids = total == 0 ? List.of()
                : assets.searchLibrary(owner, type, pattern, filter.archived(), filter.unused(), request.size(), request.offset());
        return new PageResponse<>(items(author, ids), request.page(), request.size(), total);
    }

    @Transactional
    public MediaItem upload(UUID accountId, byte[] bytes, String fileName) {
        Account author = guard.requireAuthor(accountId);
        ContentAsset asset = media.uploadAsset(accountId, bytes, fileName);
        return items(author, List.of(asset.getId())).getFirst();
    }

    @Transactional(readOnly = true)
    public MediaDetail detail(UUID accountId, UUID assetId) {
        Account author = guard.requireAuthor(accountId);
        ContentAsset asset = visible(author, assetId);
        List<ContentAssetRepository.Use> uses = assets.usesOf(asset.getId());
        Map<UUID, QuestionVersion> versions = questionVersions.findAllById(uses.stream().map(ContentAssetRepository.Use::getVersionId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(QuestionVersion::getId, Function.identity()));
        Map<UUID, Question> byQuestion = questions.findAllById(versions.values().stream().map(QuestionVersion::getQuestionId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(Question::getId, Function.identity()));
        List<MediaUse> detail = uses.stream()
                .map(use -> {
                    QuestionVersion version = versions.get(use.getVersionId());
                    Question question = version == null ? null : byQuestion.get(version.getQuestionId());
                    return version == null || question == null ? null
                            : new MediaUse(question.getId(), question.getSubjectId(), version.getId(), version.getVersionNumber(),
                            version.getStatus(), version.getPrompt(), UseField.valueOf(use.getField()));
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(MediaUse::prompt, String.CASE_INSENSITIVE_ORDER).thenComparing(MediaUse::versionNumber))
                .toList();
        return new MediaDetail(items(author, List.of(asset.getId())).getFirst(), detail);
    }

    @Transactional
    public MediaItem describe(UUID accountId, UUID assetId, String title, String description) {
        ContentAsset asset = writable(accountId, assetId);
        asset.describe(title, description);
        return items(guard.requireAuthor(accountId), List.of(assets.saveAndFlush(asset).getId())).getFirst();
    }

    @Transactional
    public MediaItem archive(UUID accountId, UUID assetId) {
        ContentAsset asset = writable(accountId, assetId);
        asset.archive(Instant.now());
        assets.saveAndFlush(asset);
        record(accountId, "MEDIA_ARCHIVED", asset);
        return items(guard.requireAuthor(accountId), List.of(asset.getId())).getFirst();
    }

    @Transactional
    public MediaItem restore(UUID accountId, UUID assetId) {
        ContentAsset asset = writable(accountId, assetId);
        asset.restore();
        assets.saveAndFlush(asset);
        record(accountId, "MEDIA_RESTORED", asset);
        return items(guard.requireAuthor(accountId), List.of(asset.getId())).getFirst();
    }

    /**
     * Deletes a file nothing uses, ever - in any version, draft or published (409 otherwise: archive it
     * instead). The stored bytes go too unless another owner's copy still holds them, and only once the
     * deletion has committed, so a rollback never leaves a record without its file. Whether another copy
     * holds them is decided then, under the bytes' lock (TD-26), not before: an upload of the same bytes
     * racing this deletion keeps its file.
     */
    @Transactional
    public void delete(UUID accountId, UUID assetId) {
        ContentAsset asset = writable(accountId, assetId);
        if (!assets.usesOf(asset.getId()).isEmpty()) {
            throw new IllegalStateException("This file is used by questions, so it can't be deleted. Archive it instead.");
        }
        record(accountId, "MEDIA_DELETED", asset);
        assets.delete(asset);
        assets.flush();
        String sha256 = asset.getSha256();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                media.deleteStoredFileIfUnused(sha256);
            }
        });
    }

    // ------------------------------------------------------------------ helpers

    /** The page's items in the given order: their URLs and use counts in two queries. */
    private List<MediaItem> items(Account author, List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, ContentAsset> byId = assets.findAllById(ids).stream().collect(Collectors.toMap(ContentAsset::getId, Function.identity()));
        Map<UUID, Long> used = assets.useCounts(ids).stream()
                .collect(Collectors.toMap(ContentAssetRepository.UseCount::getAssetId, ContentAssetRepository.UseCount::getVersions));
        Map<UUID, MediaService.MediaRef> refs = media.refs(ids);
        boolean admin = author.getPlatformRole() == PlatformRole.ADMIN;
        return ids.stream().map(byId::get).filter(Objects::nonNull).map(asset -> new MediaItem(asset.getId(), asset.getAssetType(),
                asset.getMimeType(), refs.get(asset.getId()).url(), asset.getSizeBytes() == null ? 0 : asset.getSizeBytes(),
                asset.getWidth(), asset.getHeight(), asset.getTitle(), asset.getDescription(), asset.isArchived(), asset.getCreatedAt(),
                used.getOrDefault(asset.getId(), 0L),
                admin || (asset.getOwnerAccountId() != null && asset.getOwnerAccountId().equals(author.getId())))).toList();
    }

    /** An asset this author's content may use (an administrator sees any); others read as not found. */
    private ContentAsset visible(Account author, UUID assetId) {
        ContentAsset asset = assets.findById(assetId).filter(a -> a.getSha256() != null)
                .orElseThrow(() -> new ResourceNotFoundException("Media", assetId));
        if (author.getPlatformRole() != PlatformRole.ADMIN
                && !ContentOwnership.usableBy(asset.getOwnerAccountId(), guard.authoringOwner(author))) {
            throw new ResourceNotFoundException("Media", assetId);
        }
        return asset;
    }

    private ContentAsset writable(UUID accountId, UUID assetId) {
        Account author = guard.requireAuthor(accountId);
        ContentAsset asset = visible(author, assetId);
        try {
            guard.requireOwnedWrite(accountId, asset.getOwnerAccountId());
        } catch (AccessDeniedException e) {
            throw new AccessDeniedException(asset.getOwnerAccountId() == null
                    ? "Platform media is managed by an administrator" : "This file belongs to another author");
        }
        return asset;
    }

    private void record(UUID accountId, String action, ContentAsset asset) {
        Account actor = guard.requireAuthor(accountId);
        audit.record(new RecordAuditEvent(null, actor.getId(), AuditActorType.valueOf(actor.getPlatformRole().name()), action, "MEDIA",
                asset.getId(), Map.of("title", asset.getTitle() == null ? "" : asset.getTitle(), "type", asset.getAssetType().name())));
    }

}
