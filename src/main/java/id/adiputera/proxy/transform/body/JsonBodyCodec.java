package id.adiputera.proxy.transform.body;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.MediaType;

@ApplicationScoped
public class JsonBodyCodec implements BodyCodec {

    private final ObjectMapper objectMapper = new ObjectMapper();

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

    @Override
    public MediaType contentType() {
        return MediaType.APPLICATION_JSON_TYPE;
    }

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

    @Override
    public byte[] serialize(JsonNode document) {
        try {
            return objectMapper.writeValueAsBytes(document);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize JSON body", ex);
        }
    }

    @Override
    public boolean supportsNestedPointers() {
        return true;
    }

    @Override
    public boolean supportsWrapUnwrap() {
        return true;
    }
}
