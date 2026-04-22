package id.adiputera.proxy.transform.body;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormUrlEncodedBodyCodecTest {

    private final FormUrlEncodedBodyCodec codec = new FormUrlEncodedBodyCodec();

    @Test
    void supportsFormMediaType() {
        assertThat(codec.supports(MediaType.APPLICATION_FORM_URLENCODED_TYPE)).isTrue();
        assertThat(codec.supports(MediaType.valueOf(
                "application/x-www-form-urlencoded;charset=UTF-8"))).isTrue();
        assertThat(codec.supports(MediaType.APPLICATION_JSON_TYPE)).isFalse();
        assertThat(codec.supports(null)).isFalse();
    }

    @Test
    void parsesSimplePairs() {
        JsonNode node = codec.parse("username=bob&password=x".getBytes(StandardCharsets.UTF_8));

        assertThat(node.get("username").asText()).isEqualTo("bob");
        assertThat(node.get("password").asText()).isEqualTo("x");
    }

    @Test
    void urlDecodesPercentAndPlus() {
        JsonNode node = codec.parse("a=hello+world&b=%C3%A9".getBytes(StandardCharsets.UTF_8));

        assertThat(node.get("a").asText()).isEqualTo("hello world");
        assertThat(node.get("b").asText()).isEqualTo("é");
    }

    @Test
    void repeatedKeyFirstValueWins() {
        JsonNode node = codec.parse("k=first&k=second".getBytes(StandardCharsets.UTF_8));

        assertThat(node.size()).isEqualTo(1);
        assertThat(node.get("k").asText()).isEqualTo("first");
    }

    @Test
    void emptyValuePreserved() {
        JsonNode node = codec.parse("k=&j=v".getBytes(StandardCharsets.UTF_8));

        assertThat(node.get("k").asText()).isEmpty();
        assertThat(node.get("j").asText()).isEqualTo("v");
    }

    @Test
    void parseEmptyReturnsEmptyObject() {
        JsonNode node = codec.parse(new byte[0]);

        assertThat(node.isObject()).isTrue();
        assertThat(node.size()).isEqualTo(0);
    }

    @Test
    void serializeRoundTrip() {
        byte[] input = "a=1&b=x".getBytes(StandardCharsets.UTF_8);
        JsonNode node = codec.parse(input);

        byte[] out = codec.serialize(node);
        assertThat(new String(out, StandardCharsets.UTF_8)).isEqualTo("a=1&b=x");
    }

    @Test
    void serializeEscapesSpecialChars() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("greeting", "hello world");
        node.put("token", "a/b+c");

        byte[] out = codec.serialize(node);
        String s = new String(out, StandardCharsets.UTF_8);

        assertThat(s).contains("greeting=hello+world");
        assertThat(s).contains("token=a%2Fb%2Bc");
    }

    @Test
    void serializeRejectsNestedObject() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        ObjectNode nested = root.putObject("user");
        nested.put("id", "1");

        assertThatThrownBy(() -> codec.serialize(root))
                .isInstanceOf(NonFlatBodyException.class);
    }

    @Test
    void serializeRejectsArrayRoot() {
        assertThatThrownBy(() -> codec.serialize(JsonNodeFactory.instance.arrayNode()))
                .isInstanceOf(NonFlatBodyException.class);
    }

    @Test
    void featureFlagsAreBothFalse() {
        assertThat(codec.supportsNestedPointers()).isFalse();
        assertThat(codec.supportsWrapUnwrap()).isFalse();
    }
}
