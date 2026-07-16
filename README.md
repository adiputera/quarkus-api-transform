# quarkus-proxy

Reverse proxy that translates legacy mobile-client requests into calls against new backend APIs. Routing, backends, and transform rules live in PostgreSQL and are swapped atomically in memory.

Quarkus 3.30 / Java 25 / JVM mode.

## Stack

- **Web**: Quarkus REST (JAX-RS on Vert.x) + Jackson
- **Persistence**: Hibernate ORM with Panache, PostgreSQL
- **Caching & Auth**: Distributed OAuth2 `client_credentials` token caching with Redis (`quarkus-redis-client`)
- **Migrations**: Flyway (`classpath:db/migration`)
- **Outbound HTTP**: `java.net.http.HttpClient` — one instance per backend, connect timeout baked in, read timeout applied per request, with automatic `Authorization` header injection via `BackendAuthResolver`
- **Observability**: SmallRye Health at `/q/health`, Micrometer Prometheus at `/q/metrics`, and structured request/response logging (`OperationLoggingFilter`) propagating `X-Correlation-ID` headers across all operations

## Prerequisites

- Java 25 (with `--add-opens` flags preconfigured in `pom.xml` for ByteBuddy/Surefire compatibility)
- Maven 3.8+
- PostgreSQL 13+ with two empty databases: `quarkus_proxy` (runtime) and `quarkus_proxy_test` (tests). Flyway creates the schema on first connect.
- Redis 6+ (or Docker/Podman so Quarkus DevServices can automatically spin up ephemeral PostgreSQL and Redis containers during dev and test execution).

```bash
createdb -h localhost -U postgres quarkus_proxy
createdb -h localhost -U postgres quarkus_proxy_test
```

## Build and run

```bash
# Compile + build the Quarkus app
mvn -DskipTests package

# Run in JVM mode
java -jar target/quarkus-app/quarkus-run.jar

# Or dev mode (hot reload)
mvn quarkus:dev
```

Defaults bind to `http://localhost:8080`. Override via `quarkus.http.port` or `QUARKUS_HTTP_PORT`.

## Configuration

`src/main/resources/application.properties`. Key knobs:

| Property | Default |
|---|---|
| `quarkus.http.port` | `8080` |
| `quarkus.datasource.jdbc.url` | `jdbc:postgresql://localhost:5432/quarkus_proxy` |
| `quarkus.datasource.username` / `.password` | `postgres` / `postgres` |
| `quarkus.flyway.migrate-at-start` | `true` |

The `%test.` profile points at `quarkus_proxy_test`.

## Routing model

Config is stored across five tables:

- `proxy_globals` (single row, `id=1`) — global connect/read timeouts, forward/strip header allowlists.
- `proxy_auth` — `(code, auth_type, url?, client_id?, client_secret?, grant_type?, oauth2_type?)`. Defines outbound authentication definitions (`BASIC` or `OAUTH2`). For `OAUTH2`, `oauth2_type` specifies whether client credentials are sent in the HTTP `Authorization` header (`HEADER`) or form body (`BODY`).
- `proxy_backends` — `(id, base_url, connect_timeout_ms?, read_timeout_ms?, auth_code?)`. Timeouts null → inherit globals. If `auth_code` is provided, `BackendAuthResolver` automatically resolves and injects the outbound `Authorization` header before forwarding requests. OAuth2 `client_credentials` tokens are fetched on demand and cached in Redis with proactive TTL margin refreshes and per-instance concurrency locking.
- `proxy_routes` — `(id, source, target, backend_id, methods[], produces?)`. `source` and `target` are path templates; `{name}` captures/substitutes a path variable; trailing `/**` is a wildcard suffix.
- `proxy_route_transforms` — `(route_id, ordinal, from_ref, to_ref?)`. Refs use `location:name` grammar where location is `path`, `query`, `body`, or `header`, and `name` is a parameter name for path/query, an RFC 6901 JSON Pointer for body, or a header name (case-insensitive on read, casing preserved on write) for header. `to_ref` null on a `body:` source means "drop"; drop is not supported for other locations — remove inbound headers via `proxy_globals.strip_headers` instead. When a transform writes to a header name that the inbound request also carries, the transform value overrides the inbound one.

On startup and on every admin reload, `ConfigLoader` reads all tables in one read-only transaction, validates the transforms (`TransformValidator`), compiles source patterns to regex (`RouteCompiler`), builds per-backend `HttpClient` instances, and publishes an immutable `ConfigSnapshot`. Request handlers read the snapshot once at entry and finish consistently even if a reload swaps the reference mid-flight.

### Request flow

1. JAX-RS `ProxyResource` (`@Path("/{any:.*}")`) receives any method
2. `RouteMatcher` finds the compiled route (method → path → required query params disambiguation)
3. `TransformOrchestrator` walks `route.transforms` mutating path/query maps, a header add/remove pair, and (if a body-touching transform exists) a parsed `JsonNode`; serializes the outbound body through the matching `BodyCodec` (`JsonBodyCodec` or `FormUrlEncodedBodyCodec`); assembles the target URI
4. `ProxyService` forwards via the backend's `HttpClient`, resolving and injecting the backend's `Authorization` header (`BackendAuthResolver`), filtering headers on both sides (inbound strip list + transform-remove list + JDK-restricted names; outbound drop HTTP/2 pseudo-headers and rewrite redirect `Location` headers where applicable), and applying any headers written by transforms
5. Response status, body, and headers (along with `X-Correlation-ID`) are returned to the caller

## Admin

- `POST /admin/config/reload` — rebuild the `ConfigSnapshot` from the current DB state. Returns `{"routes":N,"backends":M}` on success, `400 ConfigReloadError` on validation failure (previous snapshot stays active).

## Error responses

All errors return JSON of the shape:

```json
{"errors":[{"type":"<ErrorType>","message":"..."}]}
```

| Type | Status | When |
|---|---|---|
| `RouteNotFoundError` | 404 | No route matched |
| `UnsupportedMediaTypeError` | 415 | Route has body transforms, inbound Content-Type not parseable |
| `BodyTransformError` | 400 | Body fails to parse, or output codec can't represent the shape |
| `ConfigReloadError` | 400 | Validation rejected the new snapshot |
| `GatewayTimeoutError` | 504 | Backend request timed out |
| `BadGatewayError` | 502 | Backend connection failed (refused, DNS, I/O) |
| `InternalProxyError` | 502 | Fallback for unexpected errors |

## Sample resources (for demo/testing)

`id.adiputera.proxy.sample.*` hosts in-process JAX-RS resources matching every seeded route's target path: `ProductResource`, `V2ProductResource`, `OrderResource`, `AuthResource`, `NewAuthResource`, `NewServicesResource`, `DummyOauth2Resource`, and `DummySecuredResource`. Most echo what they received so transforms are observable; `NewAuthResource` additionally enforces its contract (401 if `X-API-Key` is missing) so header-transform success paths can be distinguished from silent no-ops. `DummyOauth2Resource` (`POST /sample/oauth2/token`) issues `client_credentials` bearer tokens, and `DummySecuredResource` (`GET/POST /sample/secured`) verifies bearer tokens to demonstrate automated OAuth2 outbound injection.

To loop the proxy through these resources, point the backends at self:

```sql
UPDATE proxy_backends SET base_url='http://localhost:8080';
```

Then `POST /admin/config/reload` and hit any seeded source path — e.g.:

```bash
curl 'http://localhost:8080/get-products?code=ABC'
# → {"path":"/products/{code}","code":"ABC",...}

curl -X POST -H 'Content-Type: application/x-www-form-urlencoded' \
     -d 'username=bob&password=x' \
     http://localhost:8080/form-login
# → {"path":"/auth/token","body":{"user":"bob","credentials":{"secret":"x"}}}

# Legacy client sends apiKey in the body; new backend expects it in X-API-Key.
curl -X POST -H 'Content-Type: application/json' \
     -d '{"apiKey":"secret-123","username":"bob"}' \
     http://localhost:8080/header-login
# → {"path":"/auth/v2/token","apiKey":"secret-123","body":{"user":"bob"},"token":"issued-for-secret-123"}

# Test automated OAuth2 client credentials resolution and Bearer token injection
curl -i http://localhost:8080/secure-test
# → {"status":"success","message":"Access granted to secured endpoint",...}
```

## Testing

```bash
mvn test
```

Layout:

- **Unit tests** — pure-logic (routing, transforms, codecs, pointer math, `BackendAuthResolverTest`, `ResponseHeaderRewriterTest`). No network, no DB.
- **`ProxyServiceTest`** — service layer behavior matrix, backend mocked with WireMock.
- **`@QuarkusTest` integration** — `ConfigLoaderTest`, `ConfigProviderReloadTest`, and `DummyOauth2ResourceTest` / `DummySecuredResourceTest` verify real database integration and OAuth2 token issuance/validation.
- **`ProxyResourceIntegrationTest`** — error paths through the JAX-RS surface.
- **`ProxyEndToEndTest`** — exercises full end-to-end proxying across all seeded routes, including header transforms (`/header-login → /auth/v2/token`) and secure OAuth2 flows (`/secure-test → /sample/secured`).

Integration tests require `quarkus_proxy_test` (or Docker/Podman for Quarkus DevServices to start PostgreSQL and Redis containers automatically).

## Project layout

```
src/main/java/id/adiputera/proxy/
├── admin/      AdminResource              (POST /admin/config/reload)
├── auth/       BackendAuthResolver        (Outbound Basic & OAuth2 token resolution + Redis caching)
├── config/     ConfigProvider/Snapshot/Loader/ReloadResult
├── exception/  ErrorResponse + 8 JAX-RS ExceptionMappers (Generic, Timeout, IO, etc.)
├── model/      RouteDefinition, BackendDefinition, AuthConfig (BasicAuth, Oauth2Auth), ParamTransform, Location
├── persistence/ Entities (Route, Backend, Auth, Globals, RouteTransform) + Panache repositories
├── proxy/      ProxyResource (catch-all), ProxyService, OperationLoggingFilter, ResponseHeaderRewriter
├── routing/    RouteCompiler (regex), RouteMatcher
├── sample/     In-process demo resources (Product, Order, Auth, NewServices, DummyOauth2, DummySecured)
└── transform/  TransformOrchestrator, TransformValidator, RequestTransformer
                body/{JsonBodyCodec, FormUrlEncodedBodyCodec, BodyCodecRegistry, ...}
                pointer/JsonPointers
```
