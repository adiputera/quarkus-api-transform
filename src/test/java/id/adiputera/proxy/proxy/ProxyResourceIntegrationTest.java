package id.adiputera.proxy.proxy;

import io.quarkus.test.junit.QuarkusTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

@QuarkusTest
class ProxyResourceIntegrationTest {

    @Test
    void unknownRouteReturns404WithErrorJson() {
        given()
                .when().get("/unknown-path")
                .then()
                .statusCode(404)
                .contentType("application/json")
                .body("errors[0].type", equalTo("RouteNotFoundError"))
                .body("errors[0].message", equalTo("No route matched for GET /unknown-path"));
    }

    @Test
    void contextLoads() {
    }

    @Test
    void bodyTransformRouteIsRecognized() {
        given()
                .contentType("application/json")
                .body("{\"code\":\"ABC\",\"name\":\"Widget\"}")
                .when().post("/update-product")
                .then()
                .statusCode(Matchers.anyOf(equalTo(502), equalTo(504)));
    }

    @Test
    void bodyTransformRejectsUnsupportedContentTypeWith415() {
        given()
                .contentType("text/plain")
                .body("not parseable")
                .when().post("/update-product")
                .then()
                .statusCode(415)
                .body("errors[0].type", equalTo("UnsupportedMediaTypeError"));
    }

    @Test
    void adminReloadReturnsCounts() {
        given()
                .when().post("/admin/config/reload")
                .then()
                .statusCode(200)
                .body("routes", greaterThan(0))
                .body("backends", greaterThan(0));
    }
}
