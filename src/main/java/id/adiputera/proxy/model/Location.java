package id.adiputera.proxy.model;

/**
 * Enumeration of parameter locations within HTTP requests and responses.
 *
 * @author Yusuf F. Adiputera
 */
public enum Location {
    PATH,
    QUERY,
    BODY,
    HEADER;

    /**
     * Parses a location prefix string into its corresponding {@link Location} enum value.
     *
     * @param prefix The lowercase prefix string (e.g., "path", "query", "body", "header").
     * @return The corresponding {@link Location} enum value.
     * @throws IllegalArgumentException if the prefix is unknown.
     */
    public static Location parse(String prefix) {
        return switch (prefix) {
            case "path" -> PATH;
            case "query" -> QUERY;
            case "body" -> BODY;
            case "header" -> HEADER;
            default -> throw new IllegalArgumentException(
                    "Unknown transform location prefix '" + prefix + "'; expected one of: path, query, body, header");
        };
    }
}
