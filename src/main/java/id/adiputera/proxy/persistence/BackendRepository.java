package id.adiputera.proxy.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Panache repository providing database operations for {@link BackendEntity}.
 *
 * @author Yusuf F. Adiputera
 */
@ApplicationScoped
public class BackendRepository implements PanacheRepositoryBase<BackendEntity, String> {
}
