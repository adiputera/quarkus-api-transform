package id.adiputera.proxy.transform;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.UriBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Transformer component constructing target backend URLs from path variables and query parameters.
 *
 * @author Yusuf F. Adiputera
 */
@ApplicationScoped
public class RequestTransformer {

    /**
     * Builds a complete target URI by substituting path variables and appending query parameters.
     *
     * @param backendBaseUrl The base URL of the target backend.
     * @param targetTemplate The URI template for the target endpoint.
     * @param pathVariables  The path variable replacements extracted from the route match.
     * @param queryParams    The query parameters to append.
     * @return The constructed target URI.
     */
    public URI buildTargetUrl(String backendBaseUrl,
                              String targetTemplate,
                              Map<String, String> pathVariables,
                              Map<String, String> queryParams) {

        Map<String, String> pathVars = new HashMap<>(pathVariables);

        String targetPath = targetTemplate;
        if (targetPath.endsWith("/**")) {
            String suffix = pathVars.getOrDefault("_suffix", "");
            targetPath = targetPath.substring(0, targetPath.length() - 3) + suffix;
            pathVars.remove("_suffix");
        }

        for (Map.Entry<String, String> entry : pathVars.entrySet()) {
            targetPath = targetPath.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        UriBuilder builder = UriBuilder.fromUri(backendBaseUrl).path(targetPath);
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            builder.queryParam(entry.getKey(), entry.getValue());
        }
        return builder.build();
    }
}
