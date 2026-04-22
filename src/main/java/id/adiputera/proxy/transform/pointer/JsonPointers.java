package id.adiputera.proxy.transform.pointer;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class JsonPointers {

    private static final JsonNodeFactory FACTORY = JsonNodeFactory.instance;

    private JsonPointers() {}

    public static boolean isRoot(JsonPointer ptr) {
        return ptr.matches();
    }

    public static JsonNode setAt(JsonNode root, JsonPointer ptr, JsonNode value) {
        if (ptr.matches()) {
            return value;
        }
        JsonNode current = root != null ? root : FACTORY.objectNode();
        current = ensureContainer(current, ptr);
        writeInto(current, ptr, value);
        return current;
    }

    public static JsonNode removeAt(JsonNode root, JsonPointer ptr) {
        if (ptr.matches()) {
            return FACTORY.objectNode();
        }
        if (root == null) {
            return null;
        }
        removeInto(root, ptr);
        return root;
    }

    private static JsonNode ensureContainer(JsonNode current, JsonPointer ptr) {
        if (isArrayIndex(ptr.getMatchingProperty())) {
            if (!current.isArray()) {
                return FACTORY.arrayNode();
            }
        } else {
            if (!current.isObject()) {
                return FACTORY.objectNode();
            }
        }
        return current;
    }

    private static void writeInto(JsonNode parent, JsonPointer ptr, JsonNode value) {
        JsonPointer tail = ptr.tail();
        String segment = ptr.getMatchingProperty();

        if (tail == null || tail.matches()) {
            setChild(parent, segment, value);
            return;
        }

        JsonNode child = getChild(parent, segment);
        String nextSegment = tail.getMatchingProperty();
        boolean nextIsArray = isArrayIndex(nextSegment);

        if (child == null || child.isMissingNode() || child.isNull()
                || (nextIsArray && !child.isArray())
                || (!nextIsArray && !child.isObject())) {
            child = nextIsArray ? FACTORY.arrayNode() : FACTORY.objectNode();
            setChild(parent, segment, child);
        }
        writeInto(child, tail, value);
    }

    private static void removeInto(JsonNode parent, JsonPointer ptr) {
        JsonPointer tail = ptr.tail();
        String segment = ptr.getMatchingProperty();

        if (tail == null || tail.matches()) {
            removeChild(parent, segment);
            return;
        }
        JsonNode child = getChild(parent, segment);
        if (child == null || child.isMissingNode()) {
            return;
        }
        removeInto(child, tail);
    }

    private static JsonNode getChild(JsonNode parent, String segment) {
        if (parent.isArray()) {
            int idx = parseArrayIndex(segment);
            if (idx < 0 || idx >= parent.size()) {
                return null;
            }
            return parent.get(idx);
        }
        if (parent.isObject()) {
            return parent.get(segment);
        }
        return null;
    }

    private static void setChild(JsonNode parent, String segment, JsonNode value) {
        if (parent.isArray()) {
            ArrayNode arr = (ArrayNode) parent;
            int idx = parseArrayIndex(segment);
            while (arr.size() <= idx) {
                arr.addNull();
            }
            arr.set(idx, value);
            return;
        }
        if (parent.isObject()) {
            ((ObjectNode) parent).set(segment, value);
            return;
        }
        throw new IllegalStateException(
                "Cannot write into node of type " + parent.getNodeType() + " at segment '" + segment + "'");
    }

    private static void removeChild(JsonNode parent, String segment) {
        if (parent.isArray()) {
            int idx = parseArrayIndex(segment);
            if (idx >= 0 && idx < parent.size()) {
                ((ArrayNode) parent).remove(idx);
            }
            return;
        }
        if (parent.isObject()) {
            ((ObjectNode) parent).remove(segment);
        }
    }

    private static boolean isArrayIndex(String segment) {
        if (segment == null || segment.isEmpty()) {
            return false;
        }
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static int parseArrayIndex(String segment) {
        try {
            return Integer.parseInt(segment);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}
