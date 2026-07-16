package id.adiputera.proxy.transform;

import com.fasterxml.jackson.core.JsonPointer;
import id.adiputera.proxy.model.Location;
import id.adiputera.proxy.model.ParamTransform;
import id.adiputera.proxy.model.RouteDefinition;
import id.adiputera.proxy.transform.body.BodyCodec;
import id.adiputera.proxy.transform.body.BodyCodecRegistry;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Validates route configuration before it becomes active.
 *
 * <p>Enforces:</p>
 * <ol>
 *   <li>{@code from} is present on every transform.</li>
 *   <li>Dropping ({@code to} omitted) is only legal when {@code from} is a {@code body:} or {@code header:} reference.</li>
 *   <li>Every {@code body:} pointer parses as valid RFC 6901.</li>
 *   <li>Every {@code header:} name is non-empty and contains only RFC 7230 token characters.</li>
 *   <li>Every {@code path:} target has a matching {@code {name}} placeholder in the route's target template.</li>
 *   <li>Features the codec can't represent (nested pointer, wrap/unwrap on form-urlencoded)
 *       are rejected unless capable {@code produces} is declared.</li>
 * </ol>
 *
 * @author Yusuf F. Adiputera
 */
@ApplicationScoped
public class TransformValidator {

    private final BodyCodecRegistry codecs;

    /**
     * Constructs a new TransformValidator.
     *
     * @param codecs The body codec registry used to check output capabilities.
     */
    public TransformValidator(BodyCodecRegistry codecs) {
        this.codecs = codecs;
    }

    /**
     * Validates every route in the candidate list. Throws on the first problem encountered.
     *
     * @param routes Candidate routes to validate.
     * @throws IllegalStateException on any rule violation.
     */
    public void validate(List<RouteDefinition> routes) {
        for (RouteDefinition route : routes) {
            validateRoute(route);
        }
    }

    /**
     * Validates an individual route and all of its declared parameter transformations.
     *
     * @param route The route definition to validate.
     * @throws IllegalStateException if any transform or target template is invalid.
     */
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

            if (t.isDrop() && fromLoc != Location.BODY && fromLoc != Location.HEADER) {
                throw new IllegalStateException(
                        ref + ": 'to' may only be omitted when 'from' is a body or header reference");
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

            if (fromLoc == Location.HEADER) {
                validateHeaderName(ref, "from", t.getFromName());
            }
            if (!t.isDrop() && toLoc == Location.HEADER) {
                validateHeaderName(ref, "to", t.getToName());
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

    /**
     * Normalizes a raw JSON Pointer string so that it starts with a leading slash,
     * or is empty for the root reference (`/` or `""`).
     *
     * @param raw The raw JSON Pointer string.
     * @return The normalized pointer string.
     */
    static String normalizePointer(String raw) {
        if (raw == null || raw.isEmpty() || raw.equals("/")) {
            return "";
        }
        return raw.startsWith("/") ? raw : "/" + raw;
    }

    /**
     * Enforces RFC 7230 token grammar on header names: non-empty, no whitespace or
     * control characters, and none of {@code "(),/:;<=>?@[\]{}}. Catches operator
     * typos at reload time.
     *
     * @param ref  The context reference string for error messages.
     * @param side Whether validating the "from" or "to" side.
     * @param name The candidate header name.
     * @throws IllegalStateException if the header name violates RFC 7230 grammar.
     */
    private static void validateHeaderName(String ref, String side, String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalStateException(ref + ": '" + side + "' header name is empty");
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c <= 0x20 || c >= 0x7F || "()<>@,;:\\\"/[]?={}".indexOf(c) >= 0) {
                throw new IllegalStateException(ref + ": '" + side + "' header name '" + name
                        + "' contains invalid character '" + c + "' (RFC 7230 token)");
            }
        }
    }
}
