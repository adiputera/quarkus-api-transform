package id.adiputera.proxy.transform.pointer;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonPointersTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void setAtWritesTopLevelField() throws Exception {
        JsonNode root = mapper.readTree("{\"a\":1}");
        JsonNode result = JsonPointers.setAt(root, JsonPointer.compile("/b"), TextNode.valueOf("two"));

        assertThat(mapper.writeValueAsString(result)).isEqualTo("{\"a\":1,\"b\":\"two\"}");
    }

    @Test
    void setAtCreatesNestedObjects() throws Exception {
        JsonNode root = JsonNodeFactory.instance.objectNode();
        JsonNode result = JsonPointers.setAt(root, JsonPointer.compile("/user/profile/id"),
                TextNode.valueOf("u-1"));

        assertThat(mapper.writeValueAsString(result))
                .isEqualTo("{\"user\":{\"profile\":{\"id\":\"u-1\"}}}");
    }

    @Test
    void setAtCreatesArrayWhenNextSegmentIsNumeric() throws Exception {
        JsonNode root = JsonNodeFactory.instance.objectNode();
        JsonNode result = JsonPointers.setAt(root, JsonPointer.compile("/items/0/sku"),
                TextNode.valueOf("SKU-1"));

        assertThat(mapper.writeValueAsString(result))
                .isEqualTo("{\"items\":[{\"sku\":\"SKU-1\"}]}");
    }

    @Test
    void setAtRootReplacesDocument() throws Exception {
        JsonNode root = mapper.readTree("{\"a\":1}");
        ObjectNode wrapper = JsonNodeFactory.instance.objectNode();
        wrapper.set("data", root);

        JsonNode result = JsonPointers.setAt(root, JsonPointer.empty(), wrapper);

        assertThat(mapper.writeValueAsString(result)).isEqualTo("{\"data\":{\"a\":1}}");
    }

    @Test
    void removeAtDropsLeaf() throws Exception {
        JsonNode root = mapper.readTree("{\"a\":1,\"b\":2}");
        JsonNode result = JsonPointers.removeAt(root, JsonPointer.compile("/a"));

        assertThat(mapper.writeValueAsString(result)).isEqualTo("{\"b\":2}");
    }

    @Test
    void removeAtDropsNestedLeaf() throws Exception {
        JsonNode root = mapper.readTree("{\"user\":{\"id\":1,\"name\":\"x\"}}");
        JsonNode result = JsonPointers.removeAt(root, JsonPointer.compile("/user/name"));

        assertThat(mapper.writeValueAsString(result)).isEqualTo("{\"user\":{\"id\":1}}");
    }

    @Test
    void removeAtRootYieldsEmptyObject() throws Exception {
        JsonNode root = mapper.readTree("{\"a\":1}");
        JsonNode result = JsonPointers.removeAt(root, JsonPointer.empty());

        assertThat(mapper.writeValueAsString(result)).isEqualTo("{}");
    }

    @Test
    void removeAtMissingPathNoOps() throws Exception {
        JsonNode root = mapper.readTree("{\"a\":1}");
        JsonNode result = JsonPointers.removeAt(root, JsonPointer.compile("/missing/path"));

        assertThat(mapper.writeValueAsString(result)).isEqualTo("{\"a\":1}");
    }

    @Test
    void setAtArrayIndexAppendsPadding() throws Exception {
        JsonNode root = JsonNodeFactory.instance.objectNode();
        JsonNode result = JsonPointers.setAt(root, JsonPointer.compile("/items/2"),
                TextNode.valueOf("third"));

        assertThat(mapper.writeValueAsString(result))
                .isEqualTo("{\"items\":[null,null,\"third\"]}");
    }
}
