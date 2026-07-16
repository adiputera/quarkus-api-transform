package id.adiputera.proxy.model;

/**
 * Resolved authentication config attached to a {@link BackendDefinition}.
 *
 * <p>A backend with no {@code AuthConfig} forwards the inbound {@code Authorization}
 * header untouched. When present, the proxy replaces it: {@link Oauth2Auth} fetches
 * a token from the authorization server first; {@link BasicAuth} simply appends an
 * HTTP Basic header.</p>
 *
 * @author Yusuf F. Adiputera
 */
public sealed interface AuthConfig permits Oauth2Auth, BasicAuth {

    /**
     * Gets the stable identifier of this auth config; also the token cache key for OAuth2.
     *
     * @return The auth config code string.
     */
    String code();
}
