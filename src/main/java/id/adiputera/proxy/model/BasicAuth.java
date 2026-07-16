package id.adiputera.proxy.model;

/**
 * HTTP Basic auth: the proxy appends {@code Authorization: Basic base64(username:password)}
 * to the outbound request. No token fetch, no caching.
 *
 * @param code     identifier
 * @param username Basic auth username
 * @param password Basic auth password
 * @author Yusuf F. Adiputera
 */
public record BasicAuth(
        String code,
        String username,
        String password) implements AuthConfig {

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
     * Gets the Basic auth username.
     *
     * @return The username string.
     */
    @Override
    public String username() {
        return username;
    }

    /**
     * Gets the Basic auth password.
     *
     * @return The password string.
     */
    @Override
    public String password() {
        return password;
    }
}
