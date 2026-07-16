package id.adiputera.proxy.proxy;

import id.adiputera.proxy.config.ConfigSnapshot;
import id.adiputera.proxy.model.BackendDefinition;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rewrites response headers from a backend before they reach the client.
 *
 * <p>Today the only rewrite is the {@code Location} header on 3xx responses:
 * if it points at a configured backend, the scheme and authority are stripped
 * so the redirect resolves back through the proxy instead of leaking the
 * backend hostname to the client. Locations to anywhere else (CDNs, OAuth
 * providers, etc.) are left untouched so the client can follow them. Relative
 * Locations are also left untouched — they already resolve relative to the
 * proxy URL the client just hit.</p>
 *
 * @author Yusuf F. Adiputera
 */
@Slf4j
@ApplicationScoped
public class ResponseHeaderRewriter {

    /**
     * Returns a headers map with {@code Location} rewritten when it matches a configured
     * backend, or the original headers reference when no rewrite applies.
     *
     * @param backendHeaders The map of response headers from the upstream backend.
     * @param snapshot       The active configuration snapshot containing backend definitions.
     * @return A map of headers with any matching Location header stripped of scheme and authority.
     */
    public Map<String, List<String>> rewriteLocation(Map<String, List<String>> backendHeaders, ConfigSnapshot snapshot) {
        if (backendHeaders == null || backendHeaders.isEmpty()) {
            return backendHeaders;
        }

        String locationKey = null;
        List<String> locationValues = null;
        for (Map.Entry<String, List<String>> entry : backendHeaders.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase("location")) {
                locationKey = entry.getKey();
                locationValues = entry.getValue();
                break;
            }
        }

        if (locationValues == null || locationValues.isEmpty()) {
            return backendHeaders;
        }

        URI location = parseQuiet(locationValues.get(0));
        if (location == null || !location.isAbsolute()) {
            return backendHeaders;
        }

        if (!matchesAnyBackend(location, snapshot)) {
            log.debug("Location header points outside any configured backend; passing through: {}", location);
            return backendHeaders;
        }

        URI rewritten = stripAuthority(location);
        if (rewritten == null) {
            return backendHeaders;
        }

        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : backendHeaders.entrySet()) {
            if (!entry.getKey().equalsIgnoreCase("location")) {
                out.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }
        out.put("Location", List.of(rewritten.toString()));
        return out;
    }

    /**
     * Checks whether the given URI matches the scheme, host, and port of any configured backend.
     *
     * @param location The URI to check.
     * @param snapshot The configuration snapshot.
     * @return True if matches any backend authority, false otherwise.
     */
    private static boolean matchesAnyBackend(URI location, ConfigSnapshot snapshot) {
        if (snapshot == null || snapshot.backends() == null) {
            return false;
        }
        for (BackendDefinition backend : snapshot.backends().values()) {
            URI base = parseQuiet(backend.getBaseUrl());
            if (base == null) {
                continue;
            }
            if (sameAuthority(location, base)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether two URIs share the same scheme, host, and effective port.
     *
     * @param a First URI.
     * @param b Second URI.
     * @return True if scheme, host, and port match, false otherwise.
     */
    private static boolean sameAuthority(URI a, URI b) {
        if (a.getScheme() == null || b.getScheme() == null) {
            return false;
        }
        if (!a.getScheme().equalsIgnoreCase(b.getScheme())) {
            return false;
        }
        if (a.getHost() == null || b.getHost() == null) {
            return false;
        }
        if (!a.getHost().equalsIgnoreCase(b.getHost())) {
            return false;
        }
        return effectivePort(a) == effectivePort(b);
    }

    /**
     * Determines the effective port for a URI, defaulting to 443 for HTTPS and 80 for HTTP.
     *
     * @param uri The URI.
     * @return The integer port number.
     */
    private static int effectivePort(URI uri) {
        int port = uri.getPort();
        if (port != -1) {
            return port;
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    /**
     * Strips the scheme, host, and port from an absolute URI, returning only path, query, and fragment.
     *
     * @param absolute The absolute URI.
     * @return The stripped relative URI, or null if syntax is invalid.
     */
    private static URI stripAuthority(URI absolute) {
        try {
            return new URI(null, null, null, -1,
                    absolute.getPath() == null || absolute.getPath().isEmpty() ? "/" : absolute.getPath(),
                    absolute.getQuery(),
                    absolute.getFragment());
        } catch (URISyntaxException ex) {
            log.warn("Failed to strip authority from Location {}: {}", absolute, ex.getMessage());
            return null;
        }
    }

    /**
     * Safely parses a string into a URI without throwing exceptions.
     *
     * @param raw The raw URI string.
     * @return The parsed URI, or null on illegal argument.
     */
    private static URI parseQuiet(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return URI.create(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
