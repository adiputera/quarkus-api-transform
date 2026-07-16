package id.adiputera.proxy.transform.pointer;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Utility functions for navigating, reading, setting, and removing values within Jackson JSON structures using {@link JsonPointer}.
 *
 * @author Yusuf F. Adiputera
 */
public final class JsonPointers {

    private static final JsonNodeFactory FACTORY = JsonNodeFactory.instance;

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private JsonPointers() {}

    /**
     * Checks if the given JSON pointer points to the root node.
     *
     * @param ptr The JSON pointer.
     * @return True if pointing to root, false otherwise.
     */
    public static boolean isRoot(JsonPointer ptr) {
        return ptr.matches();
    }

    /**
     * Sets a JSON node value at the path specified by the JSON pointer within the root node.
     *
     * @param root  The root JSON node.
     * @param ptr   The JSON pointer path.
     * @param value The value to insert or update.
     * @return The modified root JSON node.
     */
    public static JsonNode setAt(JsonNode root, JsonPointer ptr, JsonNode value) {
        if (ptr.matches()) {
            return value;
        }
        JsonNode current = root != null ? root : FACTORY.objectNode();
        current = ensureContainer(current, ptr);
        writeInto(current, ptr, value);
        return current;
    }

    /**
     * Removes the JSON node at the path specified by the JSON pointer from the root node.
     *
     * @param root The root JSON node.
     * @param ptr  The JSON pointer path to remove.
     * @return The modified root node after removal.
     */
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

    /**
     * Ensures that the current node matches the required container structure (array vs object) for the pointer.
     *
     * @param current The current JSON node.
     * @param ptr     The JSON pointer.
     * @return The verified or newly constructed container node.
     */
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

    /**
     * Recursively writes a value into the JSON structure following the pointer path.
     *
     * @param parent The parent JSON node.
     * @param ptr    The JSON pointer path segment.
     * @param value  The value to write.
     */
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

    /**
     * Recursively removes a node from the JSON structure following the pointer path.
     *
     * @param parent The parent JSON node.
     * @param ptr    The JSON pointer path segment.
     */
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

    /**
     * Retrieves a child node by segment from an array or object parent.
     *
     * @param parent  The parent JSON node.
     * @param segment The property name or array index segment.
     * @return The child node, or null if not found.
     */
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

    /**
     * Sets a child node value on an array or object parent.
     *
     * @param parent  The parent JSON node.
     * @param segment The property name or array index segment.
     * @param value   The value node to insert.
     */
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

    /**
     * Removes a child node from an array or object parent.
     *
     * @param parent  The parent JSON node.
     * @param segment The property name or array index segment.
     */
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

    /**
     * Checks if a segment string represents a non-negative integer array index.
     *
     * @param segment The segment string.
     * @return True if the segment is numeric, false otherwise.
     */
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

    /**
     * Parses an integer index from a segment string safely.
     *
     * @param segment The segment string.
     * @return The parsed index, or -1 if invalid.
     */
    private static int parseArrayIndex(String segment) {
        try {
            return Integer.parseInt(segment);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}
