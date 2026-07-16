package id.adiputera.proxy.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Domain model representing a transformation rule that maps a parameter from a source location to a target location.
 *
 * @author Yusuf F. Adiputera
 */
@Getter
@Setter
public class ParamTransform {

    private String from;

    private String to;

    /**
     * Resolves the source parameter {@link Location} parsed from the {@code from} definition string.
     *
     * @return The source location.
     */
    public Location getFromLocation() {
        return Location.parse(splitPrefix(from));
    }

    /**
     * Resolves the target parameter {@link Location} parsed from the {@code to} definition string.
     *
     * @return The target location, or null if this is a drop transformation.
     */
    public Location getToLocation() {
        if (isDrop()) {
            return null;
        }
        return Location.parse(splitPrefix(to));
    }

    /**
     * Extracts the source parameter name or path parsed from the {@code from} definition string.
     *
     * @return The source parameter name.
     */
    public String getFromName() {
        return splitName(from);
    }

    /**
     * Extracts the target parameter name or path parsed from the {@code to} definition string.
     *
     * @return The target parameter name, or null if this is a drop transformation.
     */
    public String getToName() {
        if (isDrop()) {
            return null;
        }
        return splitName(to);
    }

    /**
     * Checks if this transformation drops (removes) the parameter without mapping it anywhere.
     *
     * @return True if dropped, false otherwise.
     */
    public boolean isDrop() {
        return to == null || to.isBlank();
    }

    /**
     * Checks if this transformation reads from or writes to the HTTP request/response body.
     *
     * @return True if touching body, false otherwise.
     */
    public boolean touchesBody() {
        return getFromLocation() == Location.BODY
                || (!isDrop() && getToLocation() == Location.BODY);
    }

    /**
     * Checks if this transformation reads from or writes to HTTP headers.
     *
     * @return True if touching headers, false otherwise.
     */
    public boolean touchesHeader() {
        return getFromLocation() == Location.HEADER
                || (!isDrop() && getToLocation() == Location.HEADER);
    }

    /**
     * Splits and returns the location prefix before the colon from a transform reference string.
     *
     * @param value The transform reference string (e.g., "query:paramName").
     * @return The prefix string.
     */
    private static String splitPrefix(String value) {
        int colon = value.indexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException(
                    "Invalid transform reference '" + value + "'; expected 'location:name'");
        }
        return value.substring(0, colon);
    }

    /**
     * Splits and returns the parameter name after the colon from a transform reference string.
     *
     * @param value The transform reference string (e.g., "query:paramName").
     * @return The parameter name string.
     */
    private static String splitName(String value) {
        int colon = value.indexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException(
                    "Invalid transform reference '" + value + "'; expected 'location:name'");
        }
        return value.substring(colon + 1);
    }
}
