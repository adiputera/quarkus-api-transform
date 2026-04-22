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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
public class TransformOrchestrator {

    private final BodyCodecRegistry codecs;
    private final RequestTransformer urlBuilder;

    public TransformOrchestrator(BodyCodecRegistry codecs, RequestTransformer urlBuilder) {
        this.codecs = codecs;
        this.urlBuilder = urlBuilder;
    }

    public record Result(URI targetUri, byte[] forwardBody, MediaType forwardContentType) {}

    public Result apply(RouteDefinition route,
                        Map<String, String> matchedPathVars,
                        Map<String, String[]> rawQueryParams,
                        byte[] requestBody,
                        MediaType requestContentType,
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

        for (ParamTransform t : route.getTransforms()) {
            doc = applyOne(route, t, workPath, workQuery, outPath, outQuery, doc);
        }

        for (Map.Entry<String, String> entry : workQuery.entrySet()) {
            outQuery.putIfAbsent(entry.getKey(), entry.getValue());
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

        return new Result(targetUri, forwardBody, forwardContentType);
    }

    private JsonNode applyOne(RouteDefinition route,
                              ParamTransform t,
                              Map<String, String> workPath,
                              Map<String, String> workQuery,
                              Map<String, String> outPath,
                              Map<String, String> outQuery,
                              JsonNode doc) {
        Location from = t.getFromLocation();
        Location to = t.getToLocation();
        String fromName = t.getFromName();
        String toName = t.getToName();

        if (t.isDrop()) {
            if (from != Location.BODY) {
                throw new BodyTransformException(Response.Status.BAD_REQUEST,
                        "Route '" + route.getId() + "': drop is only supported for body transforms");
            }
            return JsonPointers.removeAt(doc, JsonPointer.compile(normalizePointer(fromName)));
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
                }
            }
        }
        return doc;
    }

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

    static String normalizePointer(String raw) {
        if (raw == null || raw.isEmpty() || raw.equals("/")) {
            return "";
        }
        return raw.startsWith("/") ? raw : "/" + raw;
    }
}
