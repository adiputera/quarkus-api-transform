package id.adiputera.proxy.config;

import id.adiputera.proxy.model.BackendDefinition;
import id.adiputera.proxy.model.ParamTransform;
import id.adiputera.proxy.model.RouteDefinition;
import id.adiputera.proxy.persistence.BackendEntity;
import id.adiputera.proxy.persistence.BackendRepository;
import id.adiputera.proxy.persistence.GlobalsEntity;
import id.adiputera.proxy.persistence.GlobalsRepository;
import id.adiputera.proxy.persistence.RouteEntity;
import id.adiputera.proxy.persistence.RouteRepository;
import id.adiputera.proxy.persistence.RouteTransformEntity;
import id.adiputera.proxy.routing.RouteCompiler;
import id.adiputera.proxy.routing.RouteMatcher;
import id.adiputera.proxy.transform.TransformValidator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ConfigLoader {

    private final GlobalsRepository globalsRepo;
    private final BackendRepository backendRepo;
    private final RouteRepository routeRepo;
    private final TransformValidator validator;

    public ConfigLoader(GlobalsRepository globalsRepo,
                        BackendRepository backendRepo,
                        RouteRepository routeRepo,
                        TransformValidator validator) {
        this.globalsRepo = globalsRepo;
        this.backendRepo = backendRepo;
        this.routeRepo = routeRepo;
        this.validator = validator;
    }

    @Transactional
    public ConfigSnapshot load() {
        GlobalsEntity globals = globalsRepo.findByIdOptional((short) 1)
                .orElseThrow(() -> new IllegalStateException(
                        "proxy_globals row (id=1) is missing; run migrations"));

        Map<String, BackendDefinition> backends = new LinkedHashMap<>();
        for (BackendEntity e : backendRepo.listAll()) {
            BackendDefinition def = new BackendDefinition();
            def.setBaseUrl(e.getBaseUrl());
            def.setConnectTimeout(e.getConnectTimeoutMs() != null ? e.getConnectTimeoutMs() : 0);
            def.setReadTimeout(e.getReadTimeoutMs() != null ? e.getReadTimeoutMs() : 0);
            backends.put(e.getId(), def);
        }

        List<RouteDefinition> routes = new ArrayList<>();
        for (RouteEntity e : routeRepo.listAll()) {
            RouteDefinition route = new RouteDefinition();
            route.setId(e.getId());
            route.setSource(e.getSource());
            route.setTarget(e.getTarget());
            route.setBackend(e.getBackendId());
            route.setMethods(e.getMethods() == null ? List.of() : Arrays.asList(e.getMethods()));
            route.setProduces(e.getProduces());
            List<ParamTransform> transforms = new ArrayList<>(e.getTransforms().size());
            for (RouteTransformEntity t : e.getTransforms()) {
                ParamTransform pt = new ParamTransform();
                pt.setFrom(t.getFromRef());
                pt.setTo(t.getToRef());
                transforms.add(pt);
            }
            route.setTransforms(transforms);
            routes.add(route);
        }

        validator.validate(routes);

        List<RouteMatcher.CompiledRoute> compiled = RouteCompiler.compile(routes);

        Map<String, HttpClient> httpClients = new LinkedHashMap<>();
        Map<String, Integer> readTimeouts = new LinkedHashMap<>();
        for (Map.Entry<String, BackendDefinition> entry : backends.entrySet()) {
            BackendDefinition b = entry.getValue();
            int connectMs = b.getConnectTimeout() > 0 ? b.getConnectTimeout() : globals.getConnectTimeoutMs();
            int readMs = b.getReadTimeout() > 0 ? b.getReadTimeout() : globals.getReadTimeoutMs();

            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(connectMs))
                    .build();
            httpClients.put(entry.getKey(), httpClient);
            readTimeouts.put(entry.getKey(), readMs);
        }

        return new ConfigSnapshot(
                compiled,
                Map.copyOf(backends),
                Map.copyOf(httpClients),
                Map.copyOf(readTimeouts),
                List.copyOf(Arrays.asList(globals.getForwardHeaders())),
                List.copyOf(Arrays.asList(globals.getStripHeaders())),
                globals.getConnectTimeoutMs(),
                globals.getReadTimeoutMs()
        );
    }
}
