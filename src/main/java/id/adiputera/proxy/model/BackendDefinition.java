package id.adiputera.proxy.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Domain model representing a target backend server and its resolved settings.
 *
 * @author Yusuf F. Adiputera
 */
@Getter
@Setter
public class BackendDefinition {

    private String baseUrl;

    private int connectTimeout;

    private int readTimeout;

    private AuthConfig auth;

    /**
     * Gets the base URL of the backend server.
     *
     * @return The base URL string.
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Sets the base URL of the backend server.
     *
     * @param baseUrl The base URL to set.
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * Gets the connection timeout in milliseconds.
     *
     * @return The connect timeout in ms.
     */
    public int getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * Sets the connection timeout in milliseconds.
     *
     * @param connectTimeout The connect timeout in ms to set.
     */
    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    /**
     * Gets the read timeout in milliseconds.
     *
     * @return The read timeout in ms.
     */
    public int getReadTimeout() {
        return readTimeout;
    }

    /**
     * Sets the read timeout in milliseconds.
     *
     * @param readTimeout The read timeout in ms to set.
     */
    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    /**
     * Gets the authentication configuration attached to this backend.
     *
     * @return The auth configuration, or null if no auth is configured.
     */
    public AuthConfig getAuth() {
        return auth;
    }

    /**
     * Sets the authentication configuration attached to this backend.
     *
     * @param auth The auth configuration to set.
     */
    public void setAuth(AuthConfig auth) {
        this.auth = auth;
    }
}
