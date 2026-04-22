package id.adiputera.proxy.model;

public enum Location {
    PATH,
    QUERY,
    BODY;

    public static Location parse(String prefix) {
        return switch (prefix) {
            case "path" -> PATH;
            case "query" -> QUERY;
            case "body" -> BODY;
            default -> throw new IllegalArgumentException(
                    "Unknown transform location prefix '" + prefix + "'; expected one of: path, query, body");
        };
    }
}
