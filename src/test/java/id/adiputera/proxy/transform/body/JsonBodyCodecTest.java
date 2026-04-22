package id.adiputera.proxy.transform.body;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonBodyCodecTest {

    private final JsonBodyCodec codec = new JsonBodyCodec();

    @Test
    void supportsJsonMediaTypes() {
        assertThat(codec.supports(MediaType.APPLICATION_JSON_TYPE)).isTrue();
        assertThat(codec.supports(MediaType.valueOf("application/json;charset=UTF-8"))).isTrue();
        assertThat(codec.supports(MediaType.valueOf("application/vnd.api+json"))).isTrue();
        assertThat(codec.supports(MediaType.TEXT_PLAIN_TYPE)).isFalse();
        assertThat(codec.supports(null)).isFalse();
    }

    @Test
    void roundTripsJson() {
        byte[] input = "{\"a\":1,\"b\":\"x\"}".getBytes(StandardCharsets.UTF_8);
        JsonNode parsed = codec.parse(input);
        byte[] output = codec.serialize(parsed);

        assertThat(new String(output, StandardCharsets.UTF_8)).isEqualTo("{\"a\":1,\"b\":\"x\"}");
    }

    @Test
    void parseEmptyReturnsEmptyObject() {
        JsonNode parsed = codec.parse(new byte[0]);

        assertThat(parsed.isObject()).isTrue();
        assertThat(parsed.size()).isEqualTo(0);
    }

    @Test
    void parseInvalidJsonThrowsBodyParseException() {
        assertThatThrownBy(() -> codec.parse("not json".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BodyParseException.class);
    }

    @Test
    void featureFlagsAreBothTrue() {
        assertThat(codec.supportsNestedPointers()).isTrue();
        assertThat(codec.supportsWrapUnwrap()).isTrue();
    }
}
