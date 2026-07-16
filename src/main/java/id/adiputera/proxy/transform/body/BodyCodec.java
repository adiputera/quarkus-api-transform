package id.adiputera.proxy.transform.body;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.core.MediaType;

/**
 * Contract defining serialization, deserialization, and structural capability checks for HTTP request/response bodies.
 *
 * @author Yusuf F. Adiputera
 */
public interface BodyCodec {

    /**
     * Checks if this codec supports the given media type.
     *
     * @param contentType The media type to check.
     * @return True if supported, false otherwise.
     */
    boolean supports(MediaType contentType);

    /**
     * Gets the canonical media type produced or handled by this codec.
     *
     * @return The canonical media type.
     */
    MediaType contentType();

    /**
     * Parses raw body bytes into an intermediate {@link JsonNode} document representation.
     *
     * @param body The raw body bytes.
     * @return The parsed intermediate document node.
     */
    JsonNode parse(byte[] body);

    /**
     * Serializes an intermediate {@link JsonNode} document into raw bytes.
     *
     * @param document The document node to serialize.
     * @return The serialized byte array.
     */
    byte[] serialize(JsonNode document);

    /**
     * Checks whether this codec supports nested JSON pointer paths.
     *
     * @return True if nested pointers are supported, false otherwise.
     */
    boolean supportsNestedPointers();

    /**
     * Checks whether this codec supports structural wrap/unwrap transformations.
     *
     * @return True if wrap and unwrap are supported, false otherwise.
     */
    boolean supportsWrapUnwrap();
}
