package id.adiputera.proxy.transform.body;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.MediaType;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;

@ApplicationScoped
public class FormUrlEncodedBodyCodec implements BodyCodec {

    @Override
    public boolean supports(MediaType contentType) {
        return contentType != null
                && MediaType.APPLICATION_FORM_URLENCODED_TYPE.isCompatible(contentType);
    }

    @Override
    public MediaType contentType() {
        return MediaType.APPLICATION_FORM_URLENCODED_TYPE;
    }

    @Override
    public JsonNode parse(byte[] body) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        if (body == null || body.length == 0) {
            return root;
        }
        String raw = new String(body, StandardCharsets.UTF_8);
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            String decodedKey = URLDecoder.decode(key, StandardCharsets.UTF_8);
            String decodedValue = URLDecoder.decode(value, StandardCharsets.UTF_8);
            if (!root.has(decodedKey)) {
                root.put(decodedKey, decodedValue);
            }
        }
        return root;
    }

    @Override
    public byte[] serialize(JsonNode document) {
        if (document == null || document.isMissingNode() || document.isNull()) {
            return new byte[0];
        }
        if (!document.isObject()) {
            throw new NonFlatBodyException(
                    "application/x-www-form-urlencoded requires a flat object at the root; got "
                            + document.getNodeType());
        }
        StringBuilder sb = new StringBuilder();
        Iterator<Map.Entry<String, JsonNode>> it = document.fields();
        boolean first = true;
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            JsonNode value = entry.getValue();
            if (value.isObject() || value.isArray()) {
                throw new NonFlatBodyException(
                        "application/x-www-form-urlencoded does not support nested values at field '"
                                + entry.getKey() + "'");
            }
            if (!first) {
                sb.append('&');
            }
            first = false;
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            sb.append('=');
            String textValue = value.isNull() ? "" : value.asText();
            sb.append(URLEncoder.encode(textValue, StandardCharsets.UTF_8));
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean supportsNestedPointers() {
        return false;
    }

    @Override
    public boolean supportsWrapUnwrap() {
        return false;
    }
}
