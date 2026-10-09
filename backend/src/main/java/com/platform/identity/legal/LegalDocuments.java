package com.platform.identity.legal;

import com.platform.common.config.ConfigurationContract;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

/**
 * Where the privacy notice and terms of use live, and which version of them is current (Y15).
 * The texts are legal content the owner provides (PD-18); the product only links to them. All
 * three empty - the default - means none exists yet, so nothing is shown and nothing is asked
 * (a placeholder would be fake). Set together: both addresses and a version name.
 *
 * @param privacyUrl the privacy notice (absolute http(s) address)
 * @param termsUrl   the terms of use
 * @param version    a name for this version (e.g. "2026-11"); changing it asks every account to agree again
 */
@ConfigurationProperties(prefix = "platform.legal")
public record LegalDocuments(String privacyUrl, String termsUrl, String version) {

    public LegalDocuments {
        privacyUrl = blankToNull(privacyUrl);
        termsUrl = blankToNull(termsUrl);
        version = blankToNull(version);
        boolean any = privacyUrl != null || termsUrl != null || version != null;
        boolean all = privacyUrl != null && termsUrl != null && version != null;
        ConfigurationContract.require(!any || all,
                "LEGAL_PRIVACY_URL, LEGAL_TERMS_URL and LEGAL_VERSION are set together or not at all");
        if (all) {
            ConfigurationContract.require(isWebAddress(privacyUrl), "LEGAL_PRIVACY_URL must be an http(s) address, was " + privacyUrl);
            ConfigurationContract.require(isWebAddress(termsUrl), "LEGAL_TERMS_URL must be an http(s) address, was " + termsUrl);
            ConfigurationContract.require(version.length() <= 40, "LEGAL_VERSION must be at most 40 characters");
        }
    }

    public boolean configured() {
        return version != null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static boolean isWebAddress(String value) {
        try {
            URI uri = URI.create(value);
            return ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) && uri.getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

}
