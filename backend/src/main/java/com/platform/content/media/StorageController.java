package com.platform.content.media;

import com.platform.content.entity.ContentAsset;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Storage (Y35): an author's own view in the Studio, and the administrator's overview and
 * allowance exceptions. Every admin operation re-checks the ADMIN role in {@link StorageService}.
 */
@RestController
public class StorageController {

    private final StorageService storage;

    public StorageController(StorageService storage) {
        this.storage = storage;
    }

    /** {@code limitMb} null = unlimited; {@code source}: where the allowance comes from. */
    public record AllowanceResponse(Integer limitMb, Long limitBytes, String source) {

        static AllowanceResponse of(StorageService.Allowance allowance) {
            return new AllowanceResponse(allowance.limitMb(), allowance.limitBytes(), allowance.source().name());
        }
    }

    public record SliceResponse(String type, boolean archived, boolean unused, long files, long bytes) {
    }

    public record LargestFile(UUID id, String title, String type, long sizeBytes, boolean archived) {

        static LargestFile of(ContentAsset asset) {
            return new LargestFile(asset.getId(), asset.getTitle(), asset.getAssetType().name(),
                    asset.getSizeBytes() == null ? 0 : asset.getSizeBytes(), asset.isArchived());
        }
    }

    /**
     * @param state OK, NEARLY_FULL, FULL or OVER (more kept than allowed: nothing is removed, new
     *              uploads wait until there is room)
     */
    public record StorageResponse(long usedBytes, AllowanceResponse allowance, Long remainingBytes, String state,
                                  List<SliceResponse> slices, List<LargestFile> largest) {

        static StorageResponse of(StorageService.StorageView view) {
            return new StorageResponse(view.usedBytes(), AllowanceResponse.of(view.allowance()), view.remainingBytes(), view.state(),
                    view.slices().stream().map(s -> new SliceResponse(s.type(), s.archived(), s.unused(), s.files(), s.bytes())).toList(),
                    view.largest().stream().map(LargestFile::of).toList());
        }
    }

    public record OwnerResponse(UUID accountId, String email, String role, long files, long usedBytes,
                                AllowanceResponse allowance, String state) {
    }

    /**
     * @param storedBytes   what the files take on disk (each file once, however many authors keep it)
     * @param platformBytes the platform's own media, counted against no one
     */
    public record OverviewResponse(long storedBytes, long platformBytes, long platformFiles, long authorBytes,
                                   List<OwnerResponse> owners) {
    }

    /** {@code limitMb} null = unlimited. */
    public record AllowanceRequest(@Min(0) Integer limitMb, @NotBlank @Size(max = 500) String note) {
    }

    public record ClearAllowanceRequest(@NotBlank @Size(max = 500) String note) {
    }

    @GetMapping("/api/v1/studio/storage")
    public StorageResponse ownStorage(@AuthenticationPrincipal UUID accountId) {
        return StorageResponse.of(storage.ownStorage(accountId));
    }

    @GetMapping("/api/v1/admin/storage")
    public OverviewResponse overview(@AuthenticationPrincipal UUID adminId) {
        StorageService.Overview overview = storage.overview(adminId);
        return new OverviewResponse(overview.storedBytes(), overview.platformBytes(), overview.platformFiles(), overview.authorBytes(),
                overview.owners().stream().map(o -> new OwnerResponse(o.accountId(), o.email(), o.role() == null ? null : o.role().name(),
                        o.files(), o.usedBytes(), AllowanceResponse.of(o.allowance()), o.state())).toList());
    }

    @GetMapping("/api/v1/admin/accounts/{accountId}/storage")
    public StorageResponse storageOf(@AuthenticationPrincipal UUID adminId, @PathVariable UUID accountId) {
        return StorageResponse.of(storage.storageOf(adminId, accountId));
    }

    @PutMapping("/api/v1/admin/accounts/{accountId}/storage-allowance")
    public StorageResponse setAllowance(@AuthenticationPrincipal UUID adminId, @PathVariable UUID accountId,
                                        @Valid @RequestBody AllowanceRequest request) {
        return StorageResponse.of(storage.setOverride(adminId, accountId, request.limitMb(), request.note()));
    }

    @PostMapping("/api/v1/admin/accounts/{accountId}/storage-allowance/clear")
    public StorageResponse clearAllowance(@AuthenticationPrincipal UUID adminId, @PathVariable UUID accountId,
                                          @Valid @RequestBody ClearAllowanceRequest request) {
        return StorageResponse.of(storage.clearOverride(adminId, accountId, request.note()));
    }
}
