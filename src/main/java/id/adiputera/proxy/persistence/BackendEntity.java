package id.adiputera.proxy.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A backend server that the proxy can forward requests to.
 * Keyed by a human-readable ID referenced from routes.
 *
 * @author Yusuf F. Adiputera
 */
@Getter
@Setter
@Entity
@Table(name = "proxy_backends")
public class BackendEntity {

    @Id
    private String id;

    @Column(name = "base_url", nullable = false)
    private String baseUrl;

    @Column(name = "connect_timeout_ms")
    private Integer connectTimeoutMs;

    @Column(name = "read_timeout_ms")
    private Integer readTimeoutMs;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "auth_code")
    private AuthEntity auth;

    /**
     * Gets the backend ID.
     *
     * @return The backend ID.
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the backend ID.
     *
     * @param id The backend ID to set.
     */
    public void setId(String id) {
        this.id = id;
    }

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
     * @return The connect timeout override, or null if falling back to globals.
     */
    public Integer getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    /**
     * Sets the connection timeout in milliseconds.
     *
     * @param connectTimeoutMs The connect timeout in ms to set.
     */
    public void setConnectTimeoutMs(Integer connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    /**
     * Gets the read timeout in milliseconds.
     *
     * @return The read timeout override, or null if falling back to globals.
     */
    public Integer getReadTimeoutMs() {
        return readTimeoutMs;
    }

    /**
     * Sets the read timeout in milliseconds.
     *
     * @param readTimeoutMs The read timeout in ms to set.
     */
    public void setReadTimeoutMs(Integer readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    /**
     * Gets the authentication configuration for this backend.
     *
     * @return The auth entity, or null if no auth is configured.
     */
    public AuthEntity getAuth() {
        return auth;
    }

    /**
     * Sets the authentication configuration for this backend.
     *
     * @param auth The auth entity to set.
     */
    public void setAuth(AuthEntity auth) {
        this.auth = auth;
    }
}
