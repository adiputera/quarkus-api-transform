package id.adiputera.proxy.transform.body;

import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * Registry discovering and providing suitable {@link BodyCodec} implementations based on media types.
 *
 * @author Yusuf F. Adiputera
 */
@ApplicationScoped
public class BodyCodecRegistry {

    private final List<BodyCodec> codecs;

    /**
     * Constructs a new BodyCodecRegistry injecting all discovered body codecs.
     *
     * @param codecs The discovered body codecs.
     */
    public BodyCodecRegistry(@All List<BodyCodec> codecs) {
        this.codecs = codecs;
    }

    /**
     * Selects a body codec supporting the given inbound media type.
     *
     * @param contentType The request content type.
     * @return The supporting body codec, or null if unsupported or null media type.
     */
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

    /**
     * Selects a body codec supporting the target outbound media type string.
     *
     * @param contentType The target content type string.
     * @return The supporting body codec.
     * @throws IllegalStateException if the content type string is malformed or no codec is available.
     */
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
