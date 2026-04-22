package id.adiputera.proxy.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "proxy_route_transforms")
public class RouteTransformEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ordinal", nullable = false)
    private int ordinal;

    @Column(name = "from_ref", nullable = false)
    private String fromRef;

    @Column(name = "to_ref")
    private String toRef;
}
