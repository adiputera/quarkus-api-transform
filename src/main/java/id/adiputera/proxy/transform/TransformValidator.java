package id.adiputera.proxy.transform;

import com.fasterxml.jackson.core.JsonPointer;
import id.adiputera.proxy.model.Location;
import id.adiputera.proxy.model.ParamTransform;
import id.adiputera.proxy.model.RouteDefinition;
import id.adiputera.proxy.transform.body.BodyCodec;
import id.adiputera.proxy.transform.body.BodyCodecRegistry;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class TransformValidator {

    private final BodyCodecRegistry codecs;

    public TransformValidator(BodyCodecRegistry codecs) {
        this.codecs = codecs;
    }

    public void validate(List<RouteDefinition> routes) {
        for (RouteDefinition route : routes) {
            validateRoute(route);
        }
    }

    private void validateRoute(RouteDefinition route) {
        BodyCodec declaredOutputCodec = null;
        if (route.getProduces() != null && !route.getProduces().isBlank()) {
            declaredOutputCodec = codecs.pickOutput(route.getProduces());
        }

        List<ParamTransform> transforms = route.getTransforms();
        for (int i = 0; i < transforms.size(); i++) {
            ParamTransform t = transforms.get(i);
            String ref = "route '" + route.getId() + "' transform[" + i + "]";

            if (t.getFrom() == null || t.getFrom().isBlank()) {
                throw new IllegalStateException(ref + ": 'from' is required");
            }
            Location fromLoc = t.getFromLocation();
            Location toLoc = t.getToLocation();

            if (t.isDrop() && fromLoc != Location.BODY) {
                throw new IllegalStateException(
                        ref + ": 'to' may only be omitted when 'from' is a body reference");
            }

            if (fromLoc == Location.HEADER && (t.getFromName() == null || t.getFromName().isBlank())) {
                throw new IllegalStateException(ref + ": 'from' header name must not be empty");
            }
            if (!t.isDrop() && toLoc == Location.HEADER
                    && (t.getToName() == null || t.getToName().isBlank())) {
                throw new IllegalStateException(ref + ": 'to' header name must not be empty");
            }

            boolean usesNested = false;
            boolean usesRoot = false;

            if (fromLoc == Location.BODY) {
                String ptr = normalizePointer(t.getFromName());
                try {
                    JsonPointer.compile(ptr);
                } catch (IllegalArgumentException ex) {
                    throw new IllegalStateException(
                            ref + ": invalid JSON Pointer in 'from': '" + t.getFromName() + "'", ex);
                }
                if (ptr.isEmpty()) usesRoot = true;
                else if (ptr.indexOf('/', 1) >= 0) usesNested = true;
            }
            if (!t.isDrop() && toLoc == Location.BODY) {
                String ptr = normalizePointer(t.getToName());
                try {
                    JsonPointer.compile(ptr);
                } catch (IllegalArgumentException ex) {
                    throw new IllegalStateException(
                            ref + ": invalid JSON Pointer in 'to': '" + t.getToName() + "'", ex);
                }
                if (ptr.isEmpty()) usesRoot = true;
                else if (ptr.indexOf('/', 1) >= 0) usesNested = true;
            }

            if (!t.isDrop() && toLoc == Location.PATH) {
                String placeholder = "{" + t.getToName() + "}";
                if (route.getTarget() == null || !route.getTarget().contains(placeholder)) {
                    throw new IllegalStateException(
                            ref + ": target template '" + route.getTarget()
                                    + "' does not contain placeholder " + placeholder);
                }
            }

            if (usesNested || usesRoot) {
                if (declaredOutputCodec != null) {
                    if (usesNested && !declaredOutputCodec.supportsNestedPointers()) {
                        throw new IllegalStateException(
                                ref + ": nested JSON Pointer is not supported by declared produces '"
                                        + route.getProduces() + "'");
                    }
                    if (usesRoot && !declaredOutputCodec.supportsWrapUnwrap()) {
                        throw new IllegalStateException(
                                ref + ": wrap/unwrap (root pointer) is not supported by declared produces '"
                                        + route.getProduces() + "'");
                    }
                } else {
                    throw new IllegalStateException(
                            ref + ": " + (usesNested ? "nested pointer" : "wrap/unwrap")
                                    + " requires declaring 'produces: application/json' on the route,"
                                    + " because form-urlencoded cannot represent it");
                }
            }
        }
    }

    static String normalizePointer(String raw) {
        if (raw == null || raw.isEmpty() || raw.equals("/")) {
            return "";
        }
        return raw.startsWith("/") ? raw : "/" + raw;
    }
}
