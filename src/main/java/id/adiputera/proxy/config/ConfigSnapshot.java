package id.adiputera.proxy.config;

import id.adiputera.proxy.model.BackendDefinition;
import id.adiputera.proxy.routing.RouteMatcher;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;

public record ConfigSnapshot(
        List<RouteMatcher.CompiledRoute> compiledRoutes,
        Map<String, BackendDefinition> backends,
        Map<String, HttpClient> backendHttpClients,
        Map<String, Integer> backendReadTimeoutsMs,
        List<String> forwardHeaders,
        List<String> stripHeaders,
        int connectTimeoutMs,
        int readTimeoutMs
) {
}
