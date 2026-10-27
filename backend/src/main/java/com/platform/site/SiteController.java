package com.platform.site;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** The landing page's statements (M2): read by anyone, managed by administrators. */
@RestController
public class SiteController {

    private final SiteStatementService statements;
    private final SiteCopyService copy;

    public SiteController(SiteStatementService statements, SiteCopyService copy) {
        this.statements = statements;
        this.copy = copy;
    }

    /** F1a: one entry's words; a blank language keeps its default. {@code version}: absent to create. */
    public record CopyRequest(@Size(max = 600) String textEn, @Size(max = 600) String textKm, Long version) {
    }

    /** The public pages' word overrides (F1a), read by anyone - the pages lay them over their defaults. */
    @GetMapping("/api/v1/site/copy")
    public List<SiteCopyService.PublicCopy> publishedCopy() {
        return copy.published();
    }

    @GetMapping("/api/v1/admin/site-copy")
    public List<SiteCopyService.Copy> allCopy(@AuthenticationPrincipal UUID adminId) {
        return copy.all(adminId);
    }

    // The namespace has slashes (features/marketing/try), so it travels as a query parameter.
    @PutMapping("/api/v1/admin/site-copy")
    public SiteCopyService.Copy putCopy(@AuthenticationPrincipal UUID adminId, @RequestParam String namespace,
                                        @RequestParam String key, @Valid @RequestBody CopyRequest request) {
        return copy.put(adminId, namespace, key, request.textEn(), request.textKm(), request.version());
    }

    @DeleteMapping("/api/v1/admin/site-copy")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetCopy(@AuthenticationPrincipal UUID adminId, @RequestParam String namespace, @RequestParam String key) {
        copy.reset(adminId, namespace, key);
    }

    public record StatementRequest(@NotNull SiteStatementService.Kind kind, @NotNull @Size(max = 280) String textEn,
                                   @NotNull @Size(max = 280) String textKm, @Size(max = 120) String attributionEn,
                                   @Size(max = 120) String attributionKm, @Size(max = 500) String sourceNote,
                                   @Min(0) Integer position, Boolean visible, Boolean confirmedGenuine, Long version) {
        SiteStatementService.Draft draft() {
            return new SiteStatementService.Draft(kind, textEn, textKm, attributionEn, attributionKm, sourceNote,
                    position == null ? 0 : position, Boolean.TRUE.equals(visible), Boolean.TRUE.equals(confirmedGenuine));
        }
    }

    @GetMapping("/api/v1/site/statements")
    public List<SiteStatementService.PublicStatement> visible() {
        return statements.visible();
    }

    @GetMapping("/api/v1/admin/site-statements")
    public List<SiteStatementService.Statement> all(@AuthenticationPrincipal UUID adminId) {
        return statements.all(adminId);
    }

    @PostMapping("/api/v1/admin/site-statements")
    @ResponseStatus(HttpStatus.CREATED)
    public SiteStatementService.Statement create(@AuthenticationPrincipal UUID adminId, @Valid @RequestBody StatementRequest request) {
        return statements.create(adminId, request.draft());
    }

    @PutMapping("/api/v1/admin/site-statements/{id}")
    public SiteStatementService.Statement update(@AuthenticationPrincipal UUID adminId, @PathVariable UUID id,
                                                 @Valid @RequestBody StatementRequest request) {
        if (request.version() == null) {
            throw new IllegalArgumentException("version is required");
        }
        return statements.update(adminId, id, request.version(), request.draft());
    }

    @DeleteMapping("/api/v1/admin/site-statements/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UUID adminId, @PathVariable UUID id) {
        statements.delete(adminId, id);
    }

}
