package id.adiputera.proxy.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "proxy_routes")
public class RouteEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private String target;

    @Column(name = "backend_id", nullable = false)
    private String backendId;

    @Column(columnDefinition = "text[]", nullable = false)
    private String[] methods;

    @Column
    private String produces;

    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "route_id")
    @OrderBy("ordinal ASC")
    private List<RouteTransformEntity> transforms = new ArrayList<>();
}
