package id.adiputera.proxy.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

/**
 * HTTP Basic auth entity: the proxy appends
 * {@code Authorization: Basic base64(username:password)} to the outbound request.
 *
 * @author Yusuf F. Adiputera
 */
@Getter
@Setter
@Entity
@DiscriminatorValue("BASIC")
public class BasicEntity extends AuthEntity {

    @Column(name = "username")
    private String username;

    @Column(name = "password")
    private String password;

    /**
     * Gets the Basic auth username.
     *
     * @return The username.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the Basic auth username.
     *
     * @param username The username to set.
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Gets the Basic auth password.
     *
     * @return The password.
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets the Basic auth password.
     *
     * @param password The password to set.
     */
    public void setPassword(String password) {
        this.password = password;
    }
}
