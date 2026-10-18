package com.platform.content.media;

import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.audit.dto.RecordAuditEvent;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.content.entity.ContentAsset;
import com.platform.content.repository.ContentAssetRepository;
import com.platform.content.service.ContentAccessGuard;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import com.platform.subscription.service.SchoolEntitlementService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Storage, the media that authors keep (Y35; PLATFORM-EXPANSION.md §3).
 *
 * <ul>
 *   <li>What counts: an owner's own pictures and recordings ({@code content_assets} rows), by their
 *       recorded size. Platform media (no owner) is shared by every learner and counts against no one.
 *       One row per owner and file, so uploading the same file again takes no more space; deleting a
 *       file frees it at once (usage is read from the rows, never kept as a counter).</li>
 *   <li>The allowance: an administrator's override for that account, else the most generous
 *       {@code TEACHER_STORAGE_MB} among the teacher's schools' plans, else the schools' default plan's.</li>
 *   <li>The limit: an upload that would go past it is refused before anything is stored. Over the
 *       allowance (after a plan change or an override is lowered), everything already kept stays in use
 *       and can be deleted or archived; only new uploads wait until there is room again.</li>
 * </ul>
 */
@Service
public class StorageService {

    static final long MB = 1024L * 1024L;
    /** Warn from here: "nearly full". */
    static final double NEARLY_FULL = 0.8;

    public enum Source { PLATFORM, OVERRIDE, SCHOOL_PLAN, DEFAULT_PLAN }

    /** {@code limitMb} null = unlimited. */
    public record Allowance(Integer limitMb, Source source) {

        public Long limitBytes() {
            return limitMb == null ? null : limitMb * MB;
        }
    }

    public record Slice(String type, boolean archived, boolean unused, long files, long bytes) {
    }

    /** Whose storage, how much of it is used and allowed, and what takes the space. */
    public record StorageView(UUID ownerId, long usedBytes, Allowance allowance, List<Slice> slices, List<ContentAsset> largest) {

        public Long remainingBytes() {
            Long limit = allowance.limitBytes();
            return limit == null ? null : Math.max(0, limit - usedBytes);
        }

        public String state() {
            return stateOf(usedBytes, allowance.limitBytes());
        }
    }

    /** OK, NEARLY_FULL (80% or more), FULL (no room left) or OVER (more kept than allowed, after a change). */
    static String stateOf(long usedBytes, Long limitBytes) {
        if (limitBytes == null) {
            return "OK";
        }
        if (usedBytes > limitBytes) {
            return "OVER";
        }
        if (usedBytes == limitBytes) {
            return "FULL";
        }
        return usedBytes >= limitBytes * NEARLY_FULL ? "NEARLY_FULL" : "OK";
    }

    public record OwnerRow(UUID accountId, String email, PlatformRole role, long files, long usedBytes, Allowance allowance,
                           String state) {
    }

    /** The platform's storage, as an administrator sees it. */
    public record Overview(long storedBytes, long platformBytes, long platformFiles, long authorBytes, List<OwnerRow> owners) {
    }

    private final ContentAssetRepository assets;
    private final StorageAllowanceRepository overrides;
    private final AccountRepository accounts;
    private final ContentAccessGuard guard;
    private final SchoolEntitlementService schools;
    private final AuditService audit;

    public StorageService(ContentAssetRepository assets, StorageAllowanceRepository overrides, AccountRepository accounts,
                          ContentAccessGuard guard, SchoolEntitlementService schools, AuditService audit) {
        this.assets = assets;
        this.overrides = overrides;
        this.accounts = accounts;
        this.guard = guard;
        this.schools = schools;
        this.audit = audit;
    }

    /** What an owner may keep (null owner: the platform's own media, unlimited). */
    @Transactional(readOnly = true)
    public Allowance allowanceOf(UUID ownerId) {
        if (ownerId == null) {
            return new Allowance(null, Source.PLATFORM);
        }
        return overrides.findByAccountId(ownerId)
                .map(override -> new Allowance(override.getLimitMb(), Source.OVERRIDE))
                .orElseGet(() -> {
                    OptionalInt mb = schools.teacherStorageMb(ownerId);
                    Source source = schools.inAnySchool(ownerId) ? Source.SCHOOL_PLAN : Source.DEFAULT_PLAN;
                    return new Allowance(mb.isPresent() ? mb.getAsInt() : null, source);
                });
    }

    /**
     * Called by the upload, after it has locked the owner's account row (so two uploads at once can't
     * both pass the last free space) and found no copy of the same file the owner already keeps.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireRoom(UUID ownerId, long bytes) {
        if (ownerId == null) {
            return;
        }
        Long limit = allowanceOf(ownerId).limitBytes();
        if (limit == null) {
            return;
        }
        long used = assets.sumSizeByOwner(ownerId);
        if (used + bytes > limit) {
            throw new StorageLimitException(used, limit, bytes);
        }
    }

    /** The signed-in author's storage: a teacher's own media, or for an administrator the platform's. */
    @Transactional(readOnly = true)
    public StorageView ownStorage(UUID accountId) {
        Account author = guard.requireAuthor(accountId);
        return view(guard.authoringOwner(author));
    }

    @Transactional(readOnly = true)
    public Overview overview(UUID adminId) {
        requireAdmin(adminId);
        long platformBytes = 0;
        long platformFiles = 0;
        long authorBytes = 0;
        List<OwnerRow> rows = new ArrayList<>();
        for (ContentAssetRepository.OwnerUsage usage : assets.usageByOwner()) {
            if (usage.getOwnerId() == null) {
                platformBytes = usage.getBytes();
                platformFiles = usage.getFiles();
                continue;
            }
            authorBytes += usage.getBytes();
            rows.add(row(usage.getOwnerId(), usage.getFiles(), usage.getBytes()));
        }
        // Authors with an override but nothing kept yet are worth seeing too.
        for (StorageAllowance override : overrides.findAll()) {
            if (rows.stream().noneMatch(r -> r.accountId().equals(override.getAccountId()))) {
                rows.add(row(override.getAccountId(), 0, 0));
            }
        }
        return new Overview(assets.storedBytes(), platformBytes, platformFiles, authorBytes, rows);
    }

    @Transactional(readOnly = true)
    public StorageView storageOf(UUID adminId, UUID accountId) {
        requireAdmin(adminId);
        return view(requireAuthorAccount(accountId).getId());
    }

    /** An exception to an author's allowance (null = unlimited), with the reason; audited. */
    @Transactional
    public StorageView setOverride(UUID adminId, UUID accountId, Integer limitMb, String note) {
        requireAdmin(adminId);
        Account author = accounts.findForUpdate(requireAuthorAccount(accountId).getId()).orElseThrow();
        Allowance before = allowanceOf(author.getId());
        StorageAllowance override = overrides.findByAccountId(author.getId()).orElseGet(() -> StorageAllowance.of(author.getId()));
        override.set(limitMb, note, adminId);
        overrides.saveAndFlush(override);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("from", before.limitMb() == null ? "unlimited" : before.limitMb() + " MB");
        metadata.put("fromSource", before.source().name());
        metadata.put("to", limitMb == null ? "unlimited" : limitMb + " MB");
        metadata.put("note", note.strip());
        record(adminId, "STORAGE_ALLOWANCE_SET", author.getId(), metadata);
        return view(author.getId());
    }

    /** Back to what the author's schools' plans give; audited. */
    @Transactional
    public StorageView clearOverride(UUID adminId, UUID accountId, String note) {
        requireAdmin(adminId);
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("A note is required when changing a storage allowance");
        }
        Account author = accounts.findForUpdate(requireAuthorAccount(accountId).getId()).orElseThrow();
        overrides.findByAccountId(author.getId()).ifPresent(override -> {
            overrides.delete(override);
            overrides.flush();
            record(adminId, "STORAGE_ALLOWANCE_CLEARED", author.getId(), Map.of("was",
                    override.getLimitMb() == null ? "unlimited" : override.getLimitMb() + " MB", "note", note.strip()));
        });
        return view(author.getId());
    }

    // ------------------------------------------------------------------

    private StorageView view(UUID ownerId) {
        List<Slice> slices = assets.storageSlices(ownerId).stream()
                .map(s -> new Slice(s.getAssetType(), s.getArchived(), s.getUnused(), s.getFiles(), s.getBytes()))
                .toList();
        long used = slices.stream().mapToLong(Slice::bytes).sum();
        List<ContentAsset> largest = assets.findAllById(assets.largestOf(ownerId, 5)).stream()
                .sorted((a, b) -> Long.compare(b.getSizeBytes() == null ? 0 : b.getSizeBytes(), a.getSizeBytes() == null ? 0 : a.getSizeBytes()))
                .toList();
        return new StorageView(ownerId, used, allowanceOf(ownerId), slices, largest);
    }

    private OwnerRow row(UUID accountId, long files, long bytes) {
        Account account = accounts.findById(accountId).orElse(null);
        Allowance allowance = allowanceOf(accountId);
        String state = stateOf(bytes, allowance.limitBytes());
        return new OwnerRow(accountId, account == null ? null : account.getEmail(), account == null ? null : account.getPlatformRole(),
                files, bytes, allowance, state);
    }

    private Account requireAuthorAccount(UUID accountId) {
        Account account = accounts.findById(accountId).orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        if (account.getPlatformRole() != PlatformRole.TEACHER && account.getPlatformRole() != PlatformRole.PRINCIPAL) {
            throw new IllegalArgumentException("Only teachers and principals keep media of their own");
        }
        return account;
    }

    private void requireAdmin(UUID accountId) {
        Account account = accounts.findById(accountId).orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        if (account.getPlatformRole() != PlatformRole.ADMIN) {
            throw new AccessDeniedException("Admin access required");
        }
    }

    private void record(UUID adminId, String action, UUID accountId, Map<String, Object> metadata) {
        audit.record(new RecordAuditEvent(null, adminId, AuditActorType.ADMIN, action, "ACCOUNT", accountId, metadata));
    }
}
