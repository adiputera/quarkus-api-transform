package id.adiputera.proxy.config;

/**
 * Immutable result summary returned from a configuration reload operation.
 *
 * @author Yusuf F. Adiputera
 */
public record ReloadResult(int routes, int backends) {
}
