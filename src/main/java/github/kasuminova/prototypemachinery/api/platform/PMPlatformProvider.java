package github.kasuminova.prototypemachinery.api.platform;

/**
 * Service Provider Interface (SPI) for selecting a {@link PMPlatform} implementation.
 *
 * <p>Providers are discovered via {@link java.util.ServiceLoader}. A separate addon mod can ship
 * a provider implementation and register it in:
 * {@code META-INF/services/github.kasuminova.prototypemachinery.api.platform.PMPlatformProvider}
 */
public interface PMPlatformProvider {

    /**
     * Provider id for logging/debug (should be stable).
     */
    String id();

    /**
     * Higher value wins.
     *
     * <p>Suggested convention:
     * <ul>
     *   <li>Legacy built-in provider: 0</li>
     *   <li>Optional modern backend provider: 100+</li>
     * </ul>
     */
    int priority();

    /**
     * Create the platform implementation.
     */
    PMPlatform create();
}
