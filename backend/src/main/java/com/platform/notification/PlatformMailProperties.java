package com.platform.notification;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.platform.common.config.ConfigurationContract;

/**
 * {@code from}: the sender address on every email (MAIL_FROM).
 * {@code publicUrl}: where families open the app, used to build links in emails
 * (APP_PUBLIC_URL) - an exact http(s) origin, so a link can only ever point at the app.
 */
@ConfigurationProperties(prefix = "platform.mail")
public record PlatformMailProperties(String from, String publicUrl) {

    public PlatformMailProperties {
        ConfigurationContract.requireText(from, "MAIL_FROM");
        ConfigurationContract.require(from.contains("@"), "MAIL_FROM must be an email address, was " + from);
        ConfigurationContract.requireText(publicUrl, "APP_PUBLIC_URL");
        URI uri = URI.create(publicUrl);
        ConfigurationContract.require(("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                        && uri.getHost() != null && (uri.getRawPath() == null || uri.getRawPath().isEmpty())
                        && uri.getRawQuery() == null && uri.getRawFragment() == null,
                "APP_PUBLIC_URL must be an exact origin such as https://app.example.org, was " + publicUrl);
    }

    /** A link to an app page, with a single-use token in the fragment (never sent to servers or logged by proxies). */
    public String link(String path, String token) {
        return publicUrl + path + "#token=" + token;
    }

}
