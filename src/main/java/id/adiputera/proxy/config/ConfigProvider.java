package id.adiputera.proxy.config;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Startup
@ApplicationScoped
public class ConfigProvider {

    private final ConfigLoader loader;
    private final AtomicReference<ConfigSnapshot> current = new AtomicReference<>();

    public ConfigProvider(ConfigLoader loader) {
        this.loader = loader;
    }

    @PostConstruct
    public void init() {
        ConfigSnapshot snapshot = loader.load();
        current.set(snapshot);
        log.info("Initial config loaded: {} routes, {} backends",
                snapshot.compiledRoutes().size(), snapshot.backends().size());
    }

    public ConfigSnapshot current() {
        return current.get();
    }

    public ReloadResult reload() {
        ConfigSnapshot next = loader.load();
        ConfigSnapshot prev = current.getAndSet(next);
        log.info("Config reloaded: {} routes, {} backends (was {} routes, {} backends)",
                next.compiledRoutes().size(), next.backends().size(),
                prev == null ? 0 : prev.compiledRoutes().size(),
                prev == null ? 0 : prev.backends().size());
        return new ReloadResult(next.compiledRoutes().size(), next.backends().size());
    }
}
