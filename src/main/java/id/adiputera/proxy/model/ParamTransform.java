package id.adiputera.proxy.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ParamTransform {

    private String from;

    private String to;

    public Location getFromLocation() {
        return Location.parse(splitPrefix(from));
    }

    public Location getToLocation() {
        if (isDrop()) {
            return null;
        }
        return Location.parse(splitPrefix(to));
    }

    public String getFromName() {
        return splitName(from);
    }

    public String getToName() {
        if (isDrop()) {
            return null;
        }
        return splitName(to);
    }

    public boolean isDrop() {
        return to == null || to.isBlank();
    }

    public boolean touchesBody() {
        return getFromLocation() == Location.BODY
                || (!isDrop() && getToLocation() == Location.BODY);
    }

    private static String splitPrefix(String value) {
        int colon = value.indexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException(
                    "Invalid transform reference '" + value + "'; expected 'location:name'");
        }
        return value.substring(0, colon);
    }

    private static String splitName(String value) {
        int colon = value.indexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException(
                    "Invalid transform reference '" + value + "'; expected 'location:name'");
        }
        return value.substring(colon + 1);
    }
}
