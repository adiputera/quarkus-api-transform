package id.adiputera.proxy.transform.body;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.core.MediaType;

public interface BodyCodec {

    boolean supports(MediaType contentType);

    MediaType contentType();

    JsonNode parse(byte[] body);

    byte[] serialize(JsonNode document);

    boolean supportsNestedPointers();

    boolean supportsWrapUnwrap();
}
