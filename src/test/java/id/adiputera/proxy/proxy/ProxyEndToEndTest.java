package id.adiputera.proxy.proxy;

import id.adiputera.proxy.config.ConfigProvider;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;

/**
 * End-to-end proof that every seeded proxy route forwards to the in-process
 * sample resources and that the response body reflects the configured transform.
 *
 * <p>Setup: rewrites every {@code proxy_backends.base_url} to point at the test
 * server itself, reloads config, runs the tests, then restores the original URLs.
 * Unlike {@code ProxyServiceTest} (WireMock, service layer) this exercises the
 * full stack: JAX-RS resource → ProxyService → java.net.http.HttpClient →
 * loopback HTTP → sample resource → response pass-through.</p>
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProxyEndToEndTest {

    @Inject
    AgroalDataSource dataSource;

    @Inject
    ConfigProvider configProvider;

    @TestHTTPResource
    URL baseUrl;

    private final Map<String, String> originalUrls = new HashMap<>();

    @BeforeAll
    void pointBackendsAtSelf() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery("SELECT id, base_url FROM proxy_backends")) {
                while (rs.next()) {
                    originalUrls.put(rs.getString(1), rs.getString(2));
                }
            }
            String self = "http://localhost:" + baseUrl.getPort();
            s.execute("UPDATE proxy_backends SET base_url='" + self + "'");
        }
        configProvider.reload();
    }

    @AfterAll
    void restoreBackends() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            for (Map.Entry<String, String> e : originalUrls.entrySet()) {
                s.execute("UPDATE proxy_backends SET base_url='" + e.getValue()
                        + "' WHERE id='" + e.getKey() + "'");
            }
        }
        configProvider.reload();
    }

    @Test
    void getProductsList() {
        given().when().get("/get-products")
                .then().statusCode(200)
                .body("path", equalTo("/products"));
    }

    @Test
    void getProductByCode() {
        given().when().get("/get-products/XYZ")
                .then().statusCode(200)
                .body("path", equalTo("/products/{code}"))
                .body("code", equalTo("XYZ"));
    }

    @Test
    void getProductByQueryParamRewritesToPath() {
        given().when().get("/get-products?code=QUERY01")
                .then().statusCode(200)
                .body("path", equalTo("/products/{code}"))
                .body("code", equalTo("QUERY01"));
    }

    @Test
    void searchPathParamBecomesQuery() {
        given().when().get("/search-products/shoes")
                .then().statusCode(200)
                .body("path", equalTo("/products/search"))
                .body("q", equalTo("shoes"))
                .body("query.q", hasItem("shoes"));
    }

    @Test
    void ordersByUserGet() {
        given().when().get("/get-orders/42")
                .then().statusCode(200)
                .body("method", equalTo("GET"))
                .body("path", equalTo("/orders"))
                .body("user_id", equalTo("42"));
    }

    @Test
    void ordersByUserPostPreservesBody() {
        given()
                .contentType("application/json")
                .body("{\"item\":\"book\"}")
                .when().post("/get-orders/42")
                .then().statusCode(200)
                .body("method", equalTo("POST"))
                .body("path", equalTo("/orders"))
                .body("user_id", equalTo("42"))
                .body("body.item", equalTo("book"));
    }

    @Test
    void authLoginQueryRename() {
        given().when().post("/do-login?username=bob")
                .then().statusCode(200)
                .body("path", equalTo("/auth/token"))
                .body("user", equalTo("bob"));
    }

    @Test
    void oldServicesCatchAllPreservesSuffix() {
        given().when().get("/api/old-services/orders/123/items")
                .then().statusCode(200)
                .body("method", equalTo("GET"))
                .body("path", equalTo("/api/new-services/orders/123/items"))
                .body("suffix", equalTo("orders/123/items"));
    }

    @Test
    void updateProductBodyCodeBecomesPath() {
        given()
                .contentType("application/json")
                .body("{\"code\":\"ABC\",\"name\":\"Widget\",\"price\":100}")
                .when().post("/update-product")
                .then().statusCode(200)
                .body("path", equalTo("/products/{code}"))
                .body("code", equalTo("ABC"))
                .body("body.name", equalTo("Widget"))
                .body("body.price", equalTo(100))
                .body("body.code", nullValue());
    }

    @Test
    void createProductWrapsBodyInEnvelope() {
        given()
                .contentType("application/json")
                .body("{\"sku\":\"X\",\"qty\":2}")
                .when().post("/create-product")
                .then().statusCode(200)
                .body("path", equalTo("/v2/products"))
                .body("body.data.sku", equalTo("X"))
                .body("body.data.qty", equalTo(2));
    }

    @Test
    void legacyFormLoginConvertsFormToJsonWithNestedRename() {
        given()
                .contentType("application/x-www-form-urlencoded")
                .body("username=bob&password=secret123")
                .when().post("/form-login")
                .then().statusCode(200)
                .body("path", equalTo("/auth/token"))
                .body("body.user", equalTo("bob"))
                .body("body.credentials.secret", equalTo("secret123"));
    }

}
