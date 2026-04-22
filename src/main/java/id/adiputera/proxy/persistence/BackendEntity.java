package id.adiputera.proxy.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

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
}
