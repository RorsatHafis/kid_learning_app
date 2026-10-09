package com.platform.identity.legal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Where the privacy notice and terms of use are (Y15). Public: shown before anyone signs up. */
@RestController
public class LegalController {

    private final LegalDocuments legal;

    public LegalController(LegalDocuments legal) {
        this.legal = legal;
    }

    /** All null while none are configured: nothing is shown and nothing is asked. */
    public record LegalResponse(String privacyUrl, String termsUrl, String version) {
    }

    @GetMapping("/api/v1/auth/legal")
    public LegalResponse legal() {
        return new LegalResponse(legal.privacyUrl(), legal.termsUrl(), legal.version());
    }

}
