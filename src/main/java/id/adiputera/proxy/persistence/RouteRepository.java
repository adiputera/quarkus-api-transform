package id.adiputera.proxy.persistence;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RouteRepository implements PanacheRepositoryBase<RouteEntity, String> {
}
