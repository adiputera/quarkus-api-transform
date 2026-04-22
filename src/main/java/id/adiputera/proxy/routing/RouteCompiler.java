package id.adiputera.proxy.routing;

import id.adiputera.proxy.model.RouteDefinition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

public final class RouteCompiler {

    private RouteCompiler() {}

    public static List<RouteMatcher.CompiledRoute> compile(List<RouteDefinition> routes) {
        List<RouteMatcher.CompiledRoute> compiled = new ArrayList<>(routes.size());
        for (RouteDefinition route : routes) {
            compiled.add(compileOne(route));
        }
        compiled.sort(Comparator
                .<RouteMatcher.CompiledRoute, Integer>comparing(cr -> cr.route().getTransforms().isEmpty() ? 1 : 0)
                .thenComparing(cr -> cr.hasWildcard() ? 1 : 0)
                .thenComparing(Comparator.comparingInt(RouteMatcher.CompiledRoute::specificity).reversed()));
        return compiled;
    }

    private static RouteMatcher.CompiledRoute compileOne(RouteDefinition route) {
        String source = route.getSource();
        boolean wildcard = false;
        if (source.endsWith("/**")) {
            source = source.substring(0, source.length() - 3);
            wildcard = true;
        }

        List<String> variableNames = new ArrayList<>();
        StringBuilder regex = new StringBuilder("^");
        int literalChars = 0;

        int i = 0;
        while (i < source.length()) {
            int braceStart = source.indexOf('{', i);
            if (braceStart < 0) {
                String literal = source.substring(i);
                if (!literal.isEmpty()) {
                    regex.append(Pattern.quote(literal));
                    literalChars += literal.length();
                }
                break;
            }
            if (braceStart > i) {
                String literal = source.substring(i, braceStart);
                regex.append(Pattern.quote(literal));
                literalChars += literal.length();
            }
            int braceEnd = source.indexOf('}', braceStart);
            if (braceEnd < 0) {
                throw new IllegalStateException(
                        "Route '" + route.getId() + "': unbalanced '{' in source pattern '" + route.getSource() + "'");
            }
            String name = source.substring(braceStart + 1, braceEnd);
            if (name.isEmpty()) {
                throw new IllegalStateException(
                        "Route '" + route.getId() + "': empty variable name in source pattern '" + route.getSource() + "'");
            }
            variableNames.add(name);
            regex.append("(?<").append(name).append(">[^/]+)");
            i = braceEnd + 1;
        }

        if (wildcard) {
            variableNames.add("_suffix");
            regex.append("(?<suffix>(?:/.*)?)");
        }
        regex.append("$");

        Pattern pattern = Pattern.compile(regex.toString());
        return new RouteMatcher.CompiledRoute(route, pattern, List.copyOf(variableNames), wildcard, literalChars);
    }
}
