package com.platform.content.media;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    private final MediaService media;

    public MediaController(MediaService media) {
        this.media = media;
    }

    @PostMapping(path = "/api/v1/studio/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MediaService.MediaRef upload(@AuthenticationPrincipal UUID accountId, @RequestParam("file") MultipartFile file) {
        try {
            return media.upload(accountId, file.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
                .filter(served -> Files.isRegularFile(served.file()))
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
