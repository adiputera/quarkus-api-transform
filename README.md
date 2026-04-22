# quarkus-proxy

Reverse proxy that translates legacy mobile-client requests into calls against new backend APIs. Routing, backends, and transform rules live in PostgreSQL and are swapped atomically in memory.

Quarkus 3.30 / Java 25 / JVM mode. Ported from a Spring Boot 4 service (same HTTP contract, same schema, same config semantics).

## Stack

- **Web**: Quarkus REST (JAX-RS on Vert.x) + Jackson
- **Persistence**: Hibernate ORM with Panache, PostgreSQL
- **Migrations**: Flyway (`classpath:db/migration`)
- **Outbound HTTP**: `java.net.http.HttpClient` — one instance per backend, connect timeout baked in, read timeout applied per request
- **Observability**: SmallRye Health at `/q/health`, Micrometer Prometheus at `/q/metrics`

## Prerequisites

- Java 25
- Maven 3.8+
- PostgreSQL 13+ with two empty databases: `quarkus_proxy` (runtime) and `quarkus_proxy_test` (tests). Flyway creates the schema on first connect.

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

Config is stored across four tables:

- `proxy_globals` (single row, `id=1`) — global connect/read timeouts, forward/strip header allowlists.
- `proxy_backends` — `(id, base_url, connect_timeout_ms?, read_timeout_ms?)`. Timeouts null → inherit globals.
- `proxy_routes` — `(id, source, target, backend_id, methods[], produces?)`. `source` and `target` are path templates; `{name}` captures/substitutes a path variable; trailing `/**` is a wildcard suffix.
- `proxy_route_transforms` — `(route_id, ordinal, from_ref, to_ref?)`. Refs use `location:name` grammar where location is `path`, `query`, or `body`, and `name` is a parameter name for path/query or an RFC 6901 JSON Pointer for body. `to_ref` null on a `body:` source means "drop".

On startup and on every admin reload, `ConfigLoader` reads all four tables in one read-only transaction, validates the transforms (`TransformValidator`), compiles source patterns to regex (`RouteCompiler`), builds per-backend `HttpClient` instances, and publishes an immutable `ConfigSnapshot`. Request handlers read the snapshot once at entry and finish consistently even if a reload swaps the reference mid-flight.

### Request flow

1. JAX-RS `ProxyResource` (`@Path("/{any:.*}")`) receives any method
2. `RouteMatcher` finds the compiled route (method → path → required query params disambiguation)
3. `TransformOrchestrator` walks `route.transforms` mutating path/query maps and (if a body-touching transform exists) a parsed `JsonNode`; serializes the outbound body through the matching `BodyCodec` (`JsonBodyCodec` or `FormUrlEncodedBodyCodec`); assembles the target URI
4. `ProxyService` forwards via the backend's `HttpClient`, filtering headers on both sides (inbound strip list + JDK-restricted names; outbound drop HTTP/2 pseudo-headers)
5. Response status, body, and headers are returned to the caller

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

`id.adiputera.proxy.sample.*` hosts in-process JAX-RS resources matching every seeded route's target path: `ProductResource`, `V2ProductResource`, `OrderResource`, `AuthResource`, `NewServicesResource`. Each echoes what it received so transforms are observable.

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
```

## Testing

```bash
mvn test
```

Layout:

- **Unit tests** — pure-logic (routing, transforms, codecs, pointer math). No network, no DB.
- **`ProxyServiceTest`** — service layer behavior matrix, backend mocked with WireMock.
- **`@QuarkusTest` integration** — `ConfigLoaderTest`, `ConfigProviderReloadTest` hit the real Postgres.
- **`ProxyResourceIntegrationTest`** — error paths through the JAX-RS surface.
- **`ProxyEndToEndTest`** — rewrites `proxy_backends` to point at the test server itself, reloads, exercises every seeded route through the full wire (JAX-RS → ProxyService → HttpClient → loopback → sample resource), and restores URLs on teardown.

Integration tests require `quarkus_proxy_test` to exist locally.

## Project layout

```
src/main/java/id/adiputera/proxy/
├── admin/      AdminResource              (POST /admin/config/reload)
├── config/     ConfigProvider/Snapshot/Loader/ReloadResult
├── exception/  ErrorResponse + 5 JAX-RS ExceptionMappers
├── model/      RouteDefinition, BackendDefinition, ParamTransform, Location
├── persistence/ Entities + Panache repositories
├── proxy/      ProxyResource (catch-all), ProxyService
├── routing/    RouteCompiler (regex), RouteMatcher
├── sample/     In-process demo resources
└── transform/  TransformOrchestrator, TransformValidator, RequestTransformer
                body/{JsonBodyCodec, FormUrlEncodedBodyCodec, BodyCodecRegistry, ...}
                pointer/JsonPointers
```
