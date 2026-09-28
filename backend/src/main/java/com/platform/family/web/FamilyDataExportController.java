package com.platform.family.web;

import com.platform.family.service.FamilyDataExportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/** The signed-in account's own data, as a file to keep (see {@link FamilyDataExportService}). */
@RestController
public class FamilyDataExportController {

    private final FamilyDataExportService service;

    public FamilyDataExportController(FamilyDataExportService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/account/data-export")
    public ResponseEntity<FamilyDataExportService.Export> export(@AuthenticationPrincipal UUID accountId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("bookeyond-kid-data-" + LocalDate.now() + ".json").build().toString())
                // A personal file: never kept by a browser or proxy cache.
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(service.export(accountId));
    }

}
