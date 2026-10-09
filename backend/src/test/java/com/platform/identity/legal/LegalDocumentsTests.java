package com.platform.identity.legal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The legal documents' settings: none, or all three, with real addresses. */
class LegalDocumentsTests {

    @Test
    void noneConfiguredMeansNothingIsShownOrAsked() {
        LegalDocuments none = new LegalDocuments("", " ", null);
        assertThat(none.configured()).isFalse();
        assertThat(none.version()).isNull();
    }

    @Test
    void allThreeTogetherWithWebAddresses() {
        LegalDocuments set = new LegalDocuments("https://example.org/privacy", "https://example.org/terms", "2026-10");
        assertThat(set.configured()).isTrue();

        assertThatThrownBy(() -> new LegalDocuments("https://example.org/privacy", null, "2026-10"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("set together");
        assertThatThrownBy(() -> new LegalDocuments("privacy.html", "https://example.org/terms", "2026-10"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("LEGAL_PRIVACY_URL");
        assertThatThrownBy(() -> new LegalDocuments("https://example.org/privacy", "ftp://example.org/terms", "1"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("LEGAL_TERMS_URL");
    }

}
