package id.adiputera.proxy.model;

/**
 * How OAuth2 client credentials are presented when fetching a token.
 *
 * <p>Different authorization servers expect the {@code client_id}/{@code client_secret}
 * in different places on the token request:</p>
 * <ul>
 *   <li>{@link #HEADER} — as an HTTP Basic {@code Authorization} header
 *       ({@code Basic base64(clientId:clientSecret)}); the form body carries only
 *       {@code grant_type}/{@code scope}.</li>
 *   <li>{@link #BODY} — as {@code client_id}/{@code client_secret} form parameters
 *       in the request body.</li>
 * </ul>
 *
 * @author Yusuf F. Adiputera
 */
public enum Oauth2Type {
    HEADER,
    BODY
}
