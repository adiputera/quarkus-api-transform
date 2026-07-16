package id.adiputera.proxy.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Panache repository providing database operations for {@link GlobalsEntity}.
 *
 * @author Yusuf F. Adiputera
 */
@ApplicationScoped
public class GlobalsRepository implements PanacheRepositoryBase<GlobalsEntity, Short> {
}
