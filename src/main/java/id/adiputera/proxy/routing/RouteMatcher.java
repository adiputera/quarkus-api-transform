package id.adiputera.proxy.routing;

import id.adiputera.proxy.config.ConfigProvider;
import id.adiputera.proxy.model.RouteDefinition;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Route matching engine evaluating HTTP method, path regex, and required query parameters against compiled routes.
 *
 * @author Yusuf F. Adiputera
 */
@ApplicationScoped
public class RouteMatcher {

    private final ConfigProvider configProvider;

    /**
     * Constructs a new RouteMatcher with the configuration provider.
     *
     * @param configProvider The configuration provider holding active compiled routes.
     */
    public RouteMatcher(ConfigProvider configProvider) {
        this.configProvider = configProvider;
    }

    /**
     * Matches an incoming HTTP request against the compiled routes.
     *
     * @param method             The HTTP request method.
     * @param path               The request URI path.
     * @param presentQueryParams The set of query parameter names present in the request.
     * @return A match result containing the route definition and extracted path variables, or null if no route matches.
     */
    public MatchResult match(String method, String path, Set<String> presentQueryParams) {
        String upperMethod = method.toUpperCase();
        List<CompiledRoute> compiledRoutes = configProvider.current().compiledRoutes();

        for (CompiledRoute compiled : compiledRoutes) {
            RouteDefinition route = compiled.route();

            if (!route.getMethods().isEmpty()
                    && route.getMethods().stream().noneMatch(m -> m.equalsIgnoreCase(upperMethod))) {
                continue;
            }

            Matcher matcher = compiled.pattern().matcher(path);
            if (!matcher.matches()) {
                continue;
            }

            List<String> requiredParams = route.getRequiredQueryParams();
            if (!requiredParams.isEmpty()
                    && !requiredParams.stream().allMatch(presentQueryParams::contains)) {
                continue;
            }

            Map<String, String> vars = new LinkedHashMap<>();
            for (String name : compiled.variableNames()) {
                String groupName = "_suffix".equals(name) ? "suffix" : name;
                vars.put(name, matcher.group(groupName));
            }
            return new MatchResult(route, vars);
        }
        return null;
    }

    /**
     * Immutable record representing a compiled route with its pre-compiled regex pattern and specificity metadata.
     *
     * @author Yusuf F. Adiputera
     */
    public record CompiledRoute(
            RouteDefinition route,
            Pattern pattern,
            List<String> variableNames,
            boolean hasWildcard,
            int specificity
    ) {
    }

    /**
     * Immutable record holding the result of a successful route match.
     *
     * @author Yusuf F. Adiputera
     */
    public record MatchResult(RouteDefinition route, Map<String, String> pathVariables) {
    }
}
