package com.platform.content.media.storage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** M11: where a school's new pictures and recordings are kept - its principal's choice (see {@link SchoolStorageService}). */
@RestController
public class SchoolStorageController {

    private final SchoolStorageService storage;

    public SchoolStorageController(SchoolStorageService storage) {
        this.storage = storage;
    }

    /** The access key is sent once, here, and never comes back in any response. */
    public record ConnectRequest(@Size(max = 300) String endpoint, @Size(max = 64) String region, @Size(max = 63) String bucket,
                                 Boolean pathStyle, @Size(max = 128) String accessKeyId, @Size(max = 256) String secretAccessKey) {
    }

    @GetMapping("/api/v1/schools/{schoolId}/storage")
    public SchoolStorageService.SchoolStorage get(@AuthenticationPrincipal UUID accountId, @PathVariable UUID schoolId) {
        return storage.view(accountId, schoolId);
    }

    @PostMapping("/api/v1/schools/{schoolId}/storage/own")
    public SchoolStorageService.SchoolStorage connect(@AuthenticationPrincipal UUID accountId, @PathVariable UUID schoolId,
                                                      @Valid @RequestBody ConnectRequest request) {
        return storage.connect(accountId, schoolId, new SchoolStorageService.Connect(request.endpoint(), request.region(),
                request.bucket(), request.pathStyle() == null || request.pathStyle(), request.accessKeyId(), request.secretAccessKey()));
    }

    @PostMapping("/api/v1/schools/{schoolId}/storage/hosted")
    public SchoolStorageService.SchoolStorage useHosted(@AuthenticationPrincipal UUID accountId, @PathVariable UUID schoolId) {
        return storage.useHosted(accountId, schoolId);
    }

    @PostMapping("/api/v1/schools/{schoolId}/storage/check")
    public SchoolStorageService.SchoolStorage check(@AuthenticationPrincipal UUID accountId, @PathVariable UUID schoolId) {
        return storage.check(accountId, schoolId);
    }
}
