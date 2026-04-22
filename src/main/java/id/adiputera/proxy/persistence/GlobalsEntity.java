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
@Table(name = "proxy_globals")
public class GlobalsEntity {

    @Id
    private Short id;

    @Column(name = "connect_timeout_ms", nullable = false)
    private int connectTimeoutMs;

    @Column(name = "read_timeout_ms", nullable = false)
    private int readTimeoutMs;

    @Column(name = "forward_headers", columnDefinition = "text[]", nullable = false)
    private String[] forwardHeaders;

    @Column(name = "strip_headers", columnDefinition = "text[]", nullable = false)
    private String[] stripHeaders;
}
