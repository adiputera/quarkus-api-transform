package id.adiputera.proxy.config;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
class ConfigProviderReloadTest {

    @Inject
    ConfigProvider provider;

    @Inject
    AgroalDataSource dataSource;

    @AfterEach
    void cleanup() throws SQLException {
        exec("DELETE FROM proxy_route_transforms WHERE route_id IN ('runtime-route', 'bad-runtime-route')");
        exec("DELETE FROM proxy_routes WHERE id IN ('runtime-route', 'bad-runtime-route')");
        exec("DELETE FROM proxy_backends WHERE id = 'runtime-backend'");
        provider.reload();
    }

    @Test
    void reloadPicksUpNewlyInsertedRoute() throws SQLException {
        int before = provider.current().compiledRoutes().size();

        exec("INSERT INTO proxy_backends (id, base_url) VALUES ('runtime-backend', 'https://example.com')");
        exec("""
                INSERT INTO proxy_routes (id, source, target, backend_id, methods)
                VALUES ('runtime-route', '/runtime', '/runtime', 'runtime-backend', ARRAY['GET']::TEXT[])
                """);

        ReloadResult result = provider.reload();

        assertThat(result.routes()).isEqualTo(before + 1);
        assertThat(provider.current().backends()).containsKey("runtime-backend");
    }

    @Test
    void reloadFailsAndKeepsPreviousSnapshot() throws SQLException {
        ConfigSnapshot before = provider.current();

        exec("INSERT INTO proxy_backends (id, base_url) VALUES ('runtime-backend', 'https://example.com')");
        exec("""
                INSERT INTO proxy_routes (id, source, target, backend_id, methods, produces)
                VALUES ('bad-runtime-route', '/bad', '/bad', 'runtime-backend', ARRAY['POST']::TEXT[], NULL)
                """);
        exec("""
                INSERT INTO proxy_route_transforms (route_id, ordinal, from_ref, to_ref)
                VALUES ('bad-runtime-route', 0, 'query:x', 'body:/deep/nested')
                """);

        assertThatThrownBy(() -> provider.reload()).isInstanceOf(IllegalStateException.class);

        assertThat(provider.current()).isSameAs(before);
    }

    private void exec(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private static DataSource ds() {
        return null;
    }
}
