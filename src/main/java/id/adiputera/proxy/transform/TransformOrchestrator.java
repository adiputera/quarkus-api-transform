package id.adiputera.proxy.transform;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import id.adiputera.proxy.exception.BodyTransformException;
import id.adiputera.proxy.model.Location;
import id.adiputera.proxy.model.ParamTransform;
import id.adiputera.proxy.model.RouteDefinition;
import id.adiputera.proxy.transform.body.BodyCodec;
import id.adiputera.proxy.transform.body.BodyCodecRegistry;
import id.adiputera.proxy.transform.body.BodyParseException;
import id.adiputera.proxy.transform.body.NonFlatBodyException;
import id.adiputera.proxy.transform.pointer.JsonPointers;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Drives the full request transformation pipeline for one proxied request:
 * selects input/output body codecs, parses the body lazily, walks
 * {@code route.transforms} mutating path/query/headers/body as it goes,
 * serializes the body, and hands the final path/query maps to
 * {@link RequestTransformer} to build the outbound URI.
 *
 * <p>Route-config validation lives in {@link TransformValidator} and is run
 * by the config loader on every reload; the orchestrator trusts that routes
 * it sees are valid.</p>
 *
 * @author Yusuf F. Adiputera
 */
@ApplicationScoped
public class TransformOrchestrator {

    private final BodyCodecRegistry codecs;
    private final RequestTransformer urlBuilder;

    /**
     * Constructs a new TransformOrchestrator.
     *
     * @param codecs     The body codec registry used to parse and serialize request bodies.
     * @param urlBuilder The request transformer used to construct target URLs.
     */
    public TransformOrchestrator(BodyCodecRegistry codecs, RequestTransformer urlBuilder) {
        this.codecs = codecs;
        this.urlBuilder = urlBuilder;
    }

    /**
     * Output of {@link #apply}.
     *
     * @param targetUri          the fully resolved backend URI
     * @param forwardBody        the body to forward (raw bytes when the route has
     *                           no body transforms, otherwise codec-serialized)
     * @param forwardContentType the Content-Type to set on the outbound request
     * @param forwardHeaders     the outbound header map containing multi-valued headers
     *                           after transformations have been applied
     */
    public record Result(URI targetUri,
                         byte[] forwardBody,
                         MediaType forwardContentType,
                         Map<String, List<String>> forwardHeaders) {

        /**
         * Gets the target URI.
         *
         * @return The target URI.
         */
        @Override
        public URI targetUri() {
            return targetUri;
        }

        /**
         * Gets the forward body byte array.
         *
         * @return The forward body bytes.
         */
        @Override
        public byte[] forwardBody() {
            return forwardBody;
        }

        /**
         * Gets the forward content type.
         *
         * @return The media type.
         */
        @Override
        public MediaType forwardContentType() {
            return forwardContentType;
        }

        /**
         * Gets the forward headers map.
         *
         * @return The map of multi-valued headers to send upstream.
         */
        @Override
        public Map<String, List<String>> forwardHeaders() {
            return forwardHeaders;
        }
    }

    /**
     * Runs the transform pipeline for one request.
     *
     * @param route              the matched route
     * @param matchedPathVars    path variables extracted by RouteMatcher
     * @param rawQueryParams     request query parameters map
     * @param requestBody        raw inbound body bytes (may be empty)
     * @param requestContentType inbound Content-Type; may be null
     * @param inboundHeaders     inbound headers, pre-strip; may be empty or null
     * @param backendBaseUrl     base URL for the route's backend
     * @return the outbound URI, body, Content-Type, and header tuple
     * @throws BodyTransformException 415 when a body transform needs a codec and
     *                                the inbound Content-Type has none; 400 when the body fails to
     *                                parse or the output codec rejects the shape
     */
    public Result apply(RouteDefinition route,
                        Map<String, String> matchedPathVars,
                        Map<String, String[]> rawQueryParams,
                        byte[] requestBody,
                        MediaType requestContentType,
                        Map<String, List<String>> inboundHeaders,
                        String backendBaseUrl) {

        boolean hasBodyTransforms = route.getTransforms().stream().anyMatch(ParamTransform::touchesBody);

        BodyCodec inputCodec = codecs.pickInput(requestContentType);
        BodyCodec outputCodec;
        if (route.getProduces() != null && !route.getProduces().isBlank()) {
            outputCodec = codecs.pickOutput(route.getProduces());
        } else {
            outputCodec = inputCodec;
        }

        if (hasBodyTransforms && inputCodec == null) {
            throw new BodyTransformException(Response.Status.UNSUPPORTED_MEDIA_TYPE,
                    "Route '" + route.getId() + "' requires a parseable request body; received Content-Type: "
                            + (requestContentType == null ? "<none>" : requestContentType.toString()));
        }

        JsonNode doc = null;
        if (hasBodyTransforms) {
            try {
                doc = inputCodec.parse(requestBody);
            } catch (BodyParseException ex) {
                throw new BodyTransformException(Response.Status.BAD_REQUEST,
                        "Route '" + route.getId() + "': " + ex.getMessage(), ex);
            }
        }

        Map<String, String> workPath = new HashMap<>(matchedPathVars);
        Map<String, String> workQuery = flatten(rawQueryParams);
        Map<String, String> outPath = new HashMap<>(workPath);
        Map<String, String> outQuery = new LinkedHashMap<>();
        Map<String, List<String>> workHeaders = new LinkedHashMap<>();
        if (inboundHeaders != null) {
            for (Map.Entry<String, List<String>> e : inboundHeaders.entrySet()) {
                if (e.getKey() != null) {
                    workHeaders.put(e.getKey(), new ArrayList<>(e.getValue()));
                }
            }
        }
        Map<String, List<String>> outHeaders = new LinkedHashMap<>();

        for (ParamTransform t : route.getTransforms()) {
            doc = applyOne(route, t, workPath, workQuery, outPath, outQuery, workHeaders, outHeaders, doc);
        }

        for (Map.Entry<String, String> entry : workQuery.entrySet()) {
            outQuery.putIfAbsent(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, List<String>> entry : workHeaders.entrySet()) {
            if (!containsHeaderIgnoreCase(outHeaders, entry.getKey())) {
                outHeaders.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }

        byte[] forwardBody;
        MediaType forwardContentType;
        if (hasBodyTransforms) {
            try {
                forwardBody = outputCodec.serialize(doc);
            } catch (NonFlatBodyException ex) {
                throw new BodyTransformException(Response.Status.BAD_REQUEST,
                        "Route '" + route.getId() + "': " + ex.getMessage(), ex);
            }
            forwardContentType = outputCodec.contentType();
        } else {
            forwardBody = requestBody;
            forwardContentType = requestContentType;
        }

        URI targetUri = urlBuilder.buildTargetUrl(backendBaseUrl, route.getTarget(), outPath, outQuery);

        return new Result(targetUri, forwardBody, forwardContentType, outHeaders);
    }

    /**
     * Applies a single transformation step to the working maps and body document.
     *
     * @param route       The route definition.
     * @param t           The parameter transform to apply.
     * @param workPath    Working path parameters map.
     * @param workQuery   Working query parameters map.
     * @param outPath     Outbound path parameters map.
     * @param outQuery    Outbound query parameters map.
     * @param workHeaders Working headers map.
     * @param outHeaders  Outbound headers map.
     * @param doc         Parsed JSON body document.
     * @return The updated JSON body document.
     * @throws BodyTransformException on illegal drop operations.
     */
    private JsonNode applyOne(RouteDefinition route,
                              ParamTransform t,
                              Map<String, String> workPath,
                              Map<String, String> workQuery,
                              Map<String, String> outPath,
                              Map<String, String> outQuery,
                              Map<String, List<String>> workHeaders,
                              Map<String, List<String>> outHeaders,
                              JsonNode doc) {
        Location from = t.getFromLocation();
        Location to = t.getToLocation();
        String fromName = t.getFromName();
        String toName = t.getToName();

        if (t.isDrop()) {
            if (from == Location.BODY) {
                return JsonPointers.removeAt(doc, JsonPointer.compile(normalizePointer(fromName)));
            }
            if (from == Location.HEADER) {
                removeHeaderIgnoreCase(workHeaders, fromName);
                return doc;
            }
            throw new BodyTransformException(Response.Status.BAD_REQUEST,
                    "Route '" + route.getId() + "': drop is only supported for body or header transforms");
        }

        switch (from) {
            case QUERY -> {
                switch (to) {
                    case PATH -> {
                        String v = workQuery.remove(fromName);
                        if (v != null) outPath.put(toName, v);
                    }
                    case QUERY -> {
                        String v = workQuery.remove(fromName);
                        if (v != null) outQuery.put(toName, v);
                    }
                    case BODY -> {
                        String v = workQuery.remove(fromName);
                        if (v != null) {
                            doc = JsonPointers.setAt(doc, JsonPointer.compile(normalizePointer(toName)),
                                    TextNode.valueOf(v));
                        }
                    }
                    case HEADER -> {
                        String v = workQuery.remove(fromName);
                        if (v != null) addHeader(outHeaders, toName, v);
                    }
                }
            }
            case PATH -> {
                switch (to) {
                    case PATH -> {
                        String v = workPath.get(fromName);
                        if (v != null) {
                            outPath.put(toName, v);
                            if (!fromName.equals(toName)) {
                                outPath.remove(fromName);
                            }
                        }
                    }
                    case QUERY -> {
                        String v = workPath.get(fromName);
                        if (v != null) {
                            outQuery.put(toName, v);
                            outPath.remove(fromName);
                        }
                    }
                    case BODY -> {
                        String v = workPath.get(fromName);
                        if (v != null) {
                            doc = JsonPointers.setAt(doc, JsonPointer.compile(normalizePointer(toName)),
                                    TextNode.valueOf(v));
                            outPath.remove(fromName);
                        }
                    }
                    case HEADER -> {
                        String v = workPath.get(fromName);
                        if (v != null) {
                            addHeader(outHeaders, toName, v);
                            outPath.remove(fromName);
                        }
                    }
                }
            }
            case BODY -> {
                JsonPointer fromPtr = JsonPointer.compile(normalizePointer(fromName));
                JsonNode read = doc == null ? null : doc.at(fromPtr);
                switch (to) {
                    case PATH -> {
                        String v = read == null || read.isMissingNode() ? null : read.asText(null);
                        doc = JsonPointers.removeAt(doc, fromPtr);
                        if (v != null) outPath.put(toName, v);
                    }
                    case QUERY -> {
                        String v = read == null || read.isMissingNode() ? null : read.asText(null);
                        doc = JsonPointers.removeAt(doc, fromPtr);
                        if (v != null) outQuery.put(toName, v);
                    }
                    case BODY -> {
                        JsonNode copy = read == null || read.isMissingNode() ? null : read.deepCopy();
                        doc = JsonPointers.removeAt(doc, fromPtr);
                        if (copy != null) {
                            doc = JsonPointers.setAt(doc, JsonPointer.compile(normalizePointer(toName)), copy);
                        }
                    }
                    case HEADER -> {
                        String v = read == null || read.isMissingNode() ? null : read.asText(null);
                        doc = JsonPointers.removeAt(doc, fromPtr);
                        if (v != null) addHeader(outHeaders, toName, v);
                    }
                }
            }
            case HEADER -> {
                switch (to) {
                    case PATH -> {
                        String v = firstValue(removeHeaderIgnoreCase(workHeaders, fromName));
                        if (v != null) outPath.put(toName, v);
                    }
                    case QUERY -> {
                        String v = firstValue(removeHeaderIgnoreCase(workHeaders, fromName));
                        if (v != null) outQuery.put(toName, v);
                    }
                    case BODY -> {
                        String v = firstValue(removeHeaderIgnoreCase(workHeaders, fromName));
                        if (v != null) {
                            doc = JsonPointers.setAt(doc, JsonPointer.compile(normalizePointer(toName)),
                                    TextNode.valueOf(v));
                        }
                    }
                    case HEADER -> {
                        if (fromName.equalsIgnoreCase(toName)) {
                            break;
                        }
                        List<String> values = removeHeaderIgnoreCase(workHeaders, fromName);
                        if (values != null && !values.isEmpty()) {
                            outHeaders.put(toName, new ArrayList<>(values));
                        }
                    }
                }
            }
        }
        return doc;
    }

    /**
     * Flattens multi-valued query parameters by picking the first element of each array.
     *
     * @param raw The raw query parameters map.
     * @return The flattened query parameters map.
     */
    private static Map<String, String> flatten(Map<String, String[]> raw) {
        Map<String, String> flat = new LinkedHashMap<>();
        if (raw == null) {
            return flat;
        }
        for (Map.Entry<String, String[]> e : raw.entrySet()) {
            if (e.getValue() != null && e.getValue().length > 0) {
                flat.put(e.getKey(), e.getValue()[0]);
            }
        }
        return flat;
    }

    /**
     * Picks the first value from a multi-valued header list when mapping to scalar locations.
     *
     * @param values The header values list.
     * @return The first value string, or null if empty or null.
     */
    private static String firstValue(List<String> values) {
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    /**
     * Normalizes a JSON pointer string.
     *
     * @param raw The raw pointer string.
     * @return The normalized pointer starting with a slash or empty string.
     */
    static String normalizePointer(String raw) {
        if (raw == null || raw.isEmpty() || raw.equals("/")) {
            return "";
        }
        return raw.startsWith("/") ? raw : "/" + raw;
    }

    /**
     * Case-insensitively checks if a header exists in the given headers map.
     *
     * @param map  The headers map.
     * @param name The header name.
     * @return True if the header exists, false otherwise.
     */
    private static boolean containsHeaderIgnoreCase(Map<String, List<String>> map, String name) {
        if (name == null || map == null) {
            return false;
        }
        for (String k : map.keySet()) {
            if (k != null && k.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Case-insensitively removes a header from the given headers map and returns its values.
     *
     * @param map  The headers map.
     * @param name The header name to remove.
     * @return The removed values list, or null if not present.
     */
    private static List<String> removeHeaderIgnoreCase(Map<String, List<String>> map, String name) {
        if (name == null || map == null) {
            return null;
        }
        String foundKey = null;
        for (String k : map.keySet()) {
            if (k != null && k.equalsIgnoreCase(name)) {
                foundKey = k;
                break;
            }
        }
        if (foundKey != null) {
            return map.remove(foundKey);
        }
        return null;
    }

    /**
     * Adds a header value to the outbound headers map, appending to existing values if present.
     *
     * @param map   The headers map.
     * @param name  The header name.
     * @param value The value string to add.
     */
    private static void addHeader(Map<String, List<String>> map, String name, String value) {
        if (value == null) {
            return;
        }
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                entry.getValue().add(value);
                return;
            }
        }
        List<String> list = new ArrayList<>();
        list.add(value);
        map.put(name, list);
    }
}
