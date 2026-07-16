package id.adiputera.proxy.persistence;

import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Backend authentication config, referenced from {@link BackendEntity#getAuth()}.
 * Single-table inheritance ({@code proxy_auth}): the {@code auth_type}
 * discriminator selects {@link Oauth2Entity} or {@link BasicEntity}.
 *
 * @author Yusuf F. Adiputera
 */
@Getter
@Setter
@Entity
@Table(name = "proxy_auth")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "auth_type", discriminatorType = DiscriminatorType.STRING)
public abstract class AuthEntity {

    @Id
    private String code;

    /**
     * Gets the auth configuration code identifier.
     *
     * @return The auth code.
     */
    public String getCode() {
        return code;
    }

    /**
     * Sets the auth configuration code identifier.
     *
     * @param code The auth code to set.
     */
    public void setCode(String code) {
        this.code = code;
    }
}
