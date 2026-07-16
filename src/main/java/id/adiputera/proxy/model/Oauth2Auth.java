package id.adiputera.proxy.model;

/**
 * OAuth2 client-credentials auth: the proxy fetches an access token from
 * {@link #url()} and sends it as the outbound {@code Authorization} header.
 *
 * @param code       identifier + token cache key
 * @param url        token endpoint
 * @param clientId   OAuth2 client id
 * @param clientSecret OAuth2 client secret
 * @param scope      requested scope (optional)
 * @param grantType  grant type; defaults to {@code client_credentials} when blank
 * @param oauth2Type where the client credentials go on the token request (see {@link Oauth2Type})
 * @author Yusuf F. Adiputera
 */
public record Oauth2Auth(
        String code,
        String url,
        String clientId,
        String clientSecret,
        String scope,
        String grantType,
        Oauth2Type oauth2Type) implements AuthConfig {

    /**
     * Gets the auth configuration code identifier.
     *
     * @return The auth code.
     * @see AuthConfig#code()
     */
    @Override
    public String code() {
        return code;
    }

    /**
     * Gets the token endpoint URL.
     *
     * @return The URL string.
     */
    @Override
    public String url() {
        return url;
    }

    /**
     * Gets the OAuth2 client ID.
     *
     * @return The client ID string.
     */
    @Override
    public String clientId() {
        return clientId;
    }

    /**
     * Gets the OAuth2 client secret.
     *
     * @return The client secret string.
     */
    @Override
    public String clientSecret() {
        return clientSecret;
    }

    /**
     * Gets the requested OAuth2 scope.
     *
     * @return The scope string, or null if not specified.
     */
    @Override
    public String scope() {
        return scope;
    }

    /**
     * Gets the OAuth2 grant type.
     *
     * @return The grant type string.
     */
    @Override
    public String grantType() {
        return grantType;
    }

    /**
     * Gets the credential presentation mode.
     *
     * @return The presentation mode enum value.
     */
    @Override
    public Oauth2Type oauth2Type() {
        return oauth2Type;
    }
}
