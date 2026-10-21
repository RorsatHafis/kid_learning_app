package com.platform.content.media;

import com.platform.common.web.PageResponse;
import com.platform.content.entity.AssetType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Uploading media in the Studio (staff), and fetching it by a signed URL (anyone holding one:
 * {@code <img>} and {@code <audio>} send no token). See {@link MediaService}.
 */
@RestController
public class MediaController {

    private static final Logger log = LoggerFactory.getLogger(MediaController.class);

    private final MediaService media;
    private final MediaLibraryService library;

    public MediaController(MediaService media, MediaLibraryService library) {
        this.media = media;
        this.library = library;
    }

    /** Uploads a file into the library, or finds the author's copy of the same file (X13). */
    @PostMapping(path = "/api/v1/studio/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MediaLibraryService.MediaItem upload(@AuthenticationPrincipal UUID accountId, @RequestParam("file") MultipartFile file) {
        try {
            return library.upload(accountId, file.getBytes(), file.getOriginalFilename());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The library: platform media and the author's own; newest first; every filter optional. */
    @GetMapping("/api/v1/studio/media")
    public PageResponse<MediaLibraryService.MediaItem> library(@AuthenticationPrincipal UUID accountId,
                                                               @RequestParam(required = false) AssetType type,
                                                               @RequestParam(required = false) @Size(max = 200) String q,
                                                               @RequestParam(defaultValue = "false") boolean archived,
                                                               @RequestParam(defaultValue = "false") boolean unused,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "24") int size) {
        return library.library(accountId, new MediaLibraryService.LibraryFilter(type, q, archived, unused), new PageResponse.Request(page, size));
    }

    @GetMapping("/api/v1/studio/media/{assetId}")
    public MediaLibraryService.MediaDetail detail(@AuthenticationPrincipal UUID accountId, @PathVariable UUID assetId) {
        return library.detail(accountId, assetId);
    }

    public record DescribeRequest(@Size(max = 200) String title, @Size(max = 500) String description) {
    }

    @PutMapping("/api/v1/studio/media/{assetId}")
    public MediaLibraryService.MediaItem describe(@AuthenticationPrincipal UUID accountId, @PathVariable UUID assetId,
                                                  @Valid @RequestBody DescribeRequest request) {
        return library.describe(accountId, assetId, request.title(), request.description());
    }

    @PostMapping("/api/v1/studio/media/{assetId}/archive")
    public MediaLibraryService.MediaItem archive(@AuthenticationPrincipal UUID accountId, @PathVariable UUID assetId) {
        return library.archive(accountId, assetId);
    }

    @PostMapping("/api/v1/studio/media/{assetId}/restore")
    public MediaLibraryService.MediaItem restore(@AuthenticationPrincipal UUID accountId, @PathVariable UUID assetId) {
        return library.restore(accountId, assetId);
    }

    @DeleteMapping("/api/v1/studio/media/{assetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UUID accountId, @PathVariable UUID assetId) {
        library.delete(accountId, assetId);
    }

    /**
     * Unknown, tampered with or expired: 404 alike, so a URL reveals nothing beyond what it was
     * issued for. Served as the inspected type, never sniffed, and sandboxed should a browser
     * open it as a document; ranges are honoured (Safari plays audio only with them).
     */
    @GetMapping("/api/v1/media/{assetId}")
    public ResponseEntity<Resource> fetch(@PathVariable UUID assetId, @RequestParam("e") long expiresAt,
                                          @RequestParam("s") String signature) {
        return media.open(assetId, expiresAt, signature)
                .filter(served -> {
                    // A valid link to a file that isn't there is lost media (TD-26): the same 404, but said in the log.
                    boolean present = Files.isRegularFile(served.file());
                    if (!present) {
                        log.warn("Media file for asset {} is missing from storage", assetId);
                    }
                    return present;
                })
                .map(served -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(served.mimeType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofSeconds(Math.max(0,
                                served.expiresAt() - Instant.now().getEpochSecond()))).cachePrivate())
                        .header("X-Content-Type-Options", "nosniff")
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                        .header("Content-Security-Policy", "default-src 'none'; sandbox")
                        .header("Cross-Origin-Resource-Policy", "same-origin")
                        .<Resource>body(new FileSystemResource(served.file())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

}
