package com.platform.content.media.storage;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * M11: what a school's own storage endpoint may be - a public HTTPS address on a usual port, never one that resolves
 * to the platform's own network (loopback, private, link-local, unique-local, multicast, unspecified). Checked when
 * connecting and again before every use (DNS can change). {@code allowPrivate}: local and test servers only.
 */
public final class EndpointPolicy {

    private static final Set<Integer> PORTS = Set.of(-1, 443, 9000, 9443);

    private EndpointPolicy() {
    }

    /** The endpoint as a URI, or a refusal (400) saying why in plain words. */
    public static URI check(String endpoint, boolean allowPrivate) {
        URI uri;
        try {
            uri = URI.create(endpoint == null ? "" : endpoint.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Enter the storage address as https://...");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (uri.getHost() == null || uri.getUserInfo() != null || (uri.getPath() != null && !uri.getPath().isEmpty() && !uri.getPath().equals("/"))
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Enter only the storage address, like https://storage.example.com.");
        }
        if (!scheme.equals("https") && !(allowPrivate && scheme.equals("http"))) {
            throw new IllegalArgumentException("The storage address must start with https://.");
        }
        if (!allowPrivate && !PORTS.contains(uri.getPort())) {
            throw new IllegalArgumentException("That storage port isn't allowed. Use the standard HTTPS port.");
        }
        if (!allowPrivate) {
            InetAddress[] addresses;
            try {
                addresses = InetAddress.getAllByName(uri.getHost());
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("That storage address can't be found.");
            }
            for (InetAddress address : addresses) {
                if (internal(address)) {
                    throw new IllegalArgumentException("That storage address points to a private network, which isn't allowed.");
                }
            }
        }
        return URI.create(scheme + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort()));
    }

    static boolean internal(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] raw = address.getAddress();
        // IPv6 unique-local (fc00::/7) and IPv4 carrier-grade NAT (100.64.0.0/10), which the JDK doesn't call site-local.
        if (raw.length == 16 && (raw[0] & 0xfe) == 0xfc) return true;
        if (raw.length == 4 && (raw[0] & 0xff) == 100 && (raw[1] & 0xc0) == 64) return true;
        // 0.0.0.0/8 and the metadata-style 169.254/16 (link-local, above) are covered; 192.0.0.0/24 too.
        return raw.length == 4 && ((raw[0] & 0xff) == 0 || ((raw[0] & 0xff) == 192 && raw[1] == 0 && raw[2] == 0));
    }
}
