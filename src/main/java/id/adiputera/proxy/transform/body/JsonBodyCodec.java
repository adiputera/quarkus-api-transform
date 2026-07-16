package id.adiputera.proxy.transform.body;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.MediaType;

/**
 * JSON implementation of {@link BodyCodec} using Jackson to parse and serialize {@code application/json} payloads.
 *
 * @author Yusuf F. Adiputera
 */
@ApplicationScoped
public class JsonBodyCodec implements BodyCodec {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Checks if this codec supports the given media type.
     *
     * @param contentType The media type to check.
     * @return True if supported, false otherwise.
     * @see BodyCodec#supports(MediaType)
     */
    @Override
    public boolean supports(MediaType contentType) {
        if (contentType == null) {
            return false;
        }
        if (MediaType.APPLICATION_JSON_TYPE.isCompatible(contentType)) {
            return true;
        }
        String subtype = contentType.getSubtype();
        return "application".equalsIgnoreCase(contentType.getType()) && subtype != null && subtype.endsWith("+json");
    }

    /**
     * Gets the canonical media type produced or handled by this codec.
     *
     * @return The canonical media type.
     * @see BodyCodec#contentType()
     */
    @Override
    public MediaType contentType() {
        return MediaType.APPLICATION_JSON_TYPE;
    }

    /**
     * Parses raw body bytes into an intermediate {@link JsonNode} document representation.
     *
     * @param body The raw body bytes.
     * @return The parsed intermediate document node.
     * @see BodyCodec#parse(byte[])
     */
    @Override
    public JsonNode parse(byte[] body) {
        try {
            if (body == null || body.length == 0) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(body);
        } catch (JsonProcessingException ex) {
            throw new BodyParseException("Failed to parse request body as JSON: " + ex.getOriginalMessage(), ex);
        } catch (Exception ex) {
            throw new BodyParseException("Failed to parse request body as JSON", ex);
        }
    }

    /**
     * Serializes an intermediate {@link JsonNode} document into raw bytes.
     *
     * @param document The document node to serialize.
     * @return The serialized byte array.
     * @see BodyCodec#serialize(JsonNode)
     */
    @Override
    public byte[] serialize(JsonNode document) {
        try {
            return objectMapper.writeValueAsBytes(document);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize JSON body", ex);
        }
    }

    /**
     * Checks whether this codec supports nested JSON pointer paths.
     *
     * @return True if nested pointers are supported, false otherwise.
     * @see BodyCodec#supportsNestedPointers()
     */
    @Override
    public boolean supportsNestedPointers() {
        return true;
    }

    /**
     * Checks whether this codec supports structural wrap/unwrap transformations.
     *
     * @return True if wrap and unwrap are supported, false otherwise.
     * @see BodyCodec#supportsWrapUnwrap()
     */
    @Override
    public boolean supportsWrapUnwrap() {
        return true;
    }
}
