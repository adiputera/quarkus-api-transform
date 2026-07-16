package id.adiputera.proxy.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Domain model defining a proxy routing rule, including source path, target URI template, backend identifier, and transformations.
 *
 * @author Yusuf F. Adiputera
 */
@Getter
@Setter
public class RouteDefinition {

    private String id;

    private String source;

    private String target;

    private String backend;

    private List<String> methods = new ArrayList<>();

    private List<ParamTransform> transforms = new ArrayList<>();

    private String produces;

    /**
     * Extracts all query parameter names that are required to be present in incoming requests based on query transformations.
     *
     * @return The list of required query parameter names.
     */
    public List<String> getRequiredQueryParams() {
        return transforms.stream()
                .filter(t -> t.getFromLocation() == Location.QUERY)
                .map(ParamTransform::getFromName)
                .toList();
    }
}
