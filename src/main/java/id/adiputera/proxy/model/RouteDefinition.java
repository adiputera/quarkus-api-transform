package id.adiputera.proxy.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

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

    public List<String> getRequiredQueryParams() {
        return transforms.stream()
                .filter(t -> t.getFromLocation() == Location.QUERY)
                .map(ParamTransform::getFromName)
                .toList();
    }
}
