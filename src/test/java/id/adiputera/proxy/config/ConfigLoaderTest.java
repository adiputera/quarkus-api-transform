package id.adiputera.proxy.config;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class ConfigLoaderTest {

    @Inject
    ConfigLoader loader;

    @Inject
    AgroalDataSource dataSource;

    @Test
    void loadsSeededRoutesAndBackends() {
        ConfigSnapshot snapshot = loader.load();

        assertThat(snapshot.backends()).containsKeys("product-api", "order-api", "auth-api");
        assertThat(snapshot.backendHttpClients()).hasSameSizeAs(snapshot.backends());
        assertThat(snapshot.compiledRoutes()).isNotEmpty();

        assertThat(snapshot.connectTimeoutMs()).isEqualTo(5000);
        assertThat(snapshot.readTimeoutMs()).isEqualTo(30000);
        assertThat(snapshot.forwardHeaders()).contains("Authorization", "Content-Type");
        assertThat(snapshot.stripHeaders()).contains("Host", "Connection", "Content-Length");
    }

    @Test
    void loadFailsOnInvalidTransformAndLeavesNoSideEffects() throws SQLException {
        exec(dataSource, """
                INSERT INTO proxy_backends (id, base_url) VALUES ('tmp-backend', 'https://example.com')
                ON CONFLICT DO NOTHING
                """);
        exec(dataSource, """
                INSERT INTO proxy_routes (id, source, target, backend_id, methods, produces)
                VALUES ('bad-route', '/bad', '/bad', 'tmp-backend', ARRAY['POST']::TEXT[], NULL)
                """);
        exec(dataSource, """
                INSERT INTO proxy_route_transforms (route_id, ordinal, from_ref, to_ref)
                VALUES ('bad-route', 0, 'query:x', 'body:/deep/nested')
                """);
        try {
            assertThatThrownBy(() -> loader.load())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("nested pointer");
        } finally {
            exec(dataSource, "DELETE FROM proxy_route_transforms WHERE route_id = 'bad-route'");
            exec(dataSource, "DELETE FROM proxy_routes WHERE id = 'bad-route'");
            exec(dataSource, "DELETE FROM proxy_backends WHERE id = 'tmp-backend'");
        }
    }

    private static void exec(DataSource ds, String sql) throws SQLException {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }
}
