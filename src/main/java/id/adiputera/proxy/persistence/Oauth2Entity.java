package id.adiputera.proxy.persistence;

import id.adiputera.proxy.model.Oauth2Type;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

/**
 * OAuth2 {@code client_credentials} auth entity: the proxy fetches a token from
 * {@link #getUrl()} and sends it as the outbound {@code Authorization} header.
 *
 * @author Yusuf F. Adiputera
 */
@Getter
@Setter
@Entity
@DiscriminatorValue("OAUTH2")
public class Oauth2Entity extends AuthEntity {

    @Column(name = "url")
    private String url;

    @Column(name = "client_id")
    private String clientId;

    @Column(name = "client_secret")
    private String clientSecret;

    @Column(name = "scope")
    private String scope;

    @Column(name = "grant_type")
    private String grantType;

    @Enumerated(EnumType.STRING)
    @Column(name = "oauth2_type")
    private Oauth2Type oauth2Type;

    /**
     * Gets the token endpoint URL.
     *
     * @return The token endpoint URL.
     */
    public String getUrl() {
        return url;
    }

    /**
     * Sets the token endpoint URL.
     *
     * @param url The token endpoint URL to set.
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * Gets the OAuth2 client ID.
     *
     * @return The client ID.
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Sets the OAuth2 client ID.
     *
     * @param clientId The client ID to set.
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    /**
     * Gets the OAuth2 client secret.
     *
     * @return The client secret.
     */
    public String getClientSecret() {
        return clientSecret;
    }

    /**
     * Sets the OAuth2 client secret.
     *
     * @param clientSecret The client secret to set.
     */
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    /**
     * Gets the requested OAuth2 scope.
     *
     * @return The OAuth2 scope.
     */
    public String getScope() {
        return scope;
    }

    /**
     * Sets the requested OAuth2 scope.
     *
     * @param scope The OAuth2 scope to set.
     */
    public void setScope(String scope) {
        this.scope = scope;
    }

    /**
     * Gets the OAuth2 grant type.
     *
     * @return The grant type string.
     */
    public String getGrantType() {
        return grantType;
    }

    /**
     * Sets the OAuth2 grant type.
     *
     * @param grantType The grant type string to set.
     */
    public void setGrantType(String grantType) {
        this.grantType = grantType;
    }

    /**
     * Gets the credential presentation type.
     *
     * @return The OAuth2 credential presentation type.
     */
    public Oauth2Type getOauth2Type() {
        return oauth2Type;
    }

    /**
     * Sets the credential presentation type.
     *
     * @param oauth2Type The OAuth2 credential presentation type to set.
     */
    public void setOauth2Type(Oauth2Type oauth2Type) {
        this.oauth2Type = oauth2Type;
    }
}
