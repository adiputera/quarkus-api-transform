package id.adiputera.proxy.transform.body;

import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@ApplicationScoped
public class BodyCodecRegistry {

    private final List<BodyCodec> codecs;

    public BodyCodecRegistry(@All List<BodyCodec> codecs) {
        this.codecs = codecs;
    }

    public BodyCodec pickInput(MediaType contentType) {
        if (contentType == null) {
            return null;
        }
        for (BodyCodec codec : codecs) {
            if (codec.supports(contentType)) {
                return codec;
            }
        }
        return null;
    }

    public BodyCodec pickOutput(String contentType) {
        MediaType parsed;
        try {
            parsed = MediaType.valueOf(contentType);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "Invalid outbound Content-Type '" + contentType + "': " + ex.getMessage(), ex);
        }
        for (BodyCodec codec : codecs) {
            if (codec.supports(parsed)) {
                return codec;
            }
        }
        throw new IllegalStateException(
                "No body codec available for declared Content-Type '" + contentType + "'");
    }
}
