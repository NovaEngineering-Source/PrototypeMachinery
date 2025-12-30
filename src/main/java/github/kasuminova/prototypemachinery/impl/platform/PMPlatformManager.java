package github.kasuminova.prototypemachinery.impl.platform;

import github.kasuminova.prototypemachinery.api.platform.PMPlatform;
import github.kasuminova.prototypemachinery.api.platform.PMPlatformProvider;
import org.apache.logging.log4j.Logger;

import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Resolves the active {@link PMPlatform} implementation.
 */
public final class PMPlatformManager {

    private static volatile PMPlatform platform;

    private PMPlatformManager() {
    }

    /**
     * Bootstrap platform resolution.
     *
     * <p>Safe to call multiple times.
     */
    public static void bootstrap(Logger logger) {
        if (platform != null) {
            return;
        }

        PMPlatform resolved = null;
        PMPlatformProvider winner = null;

        try {
            for (PMPlatformProvider provider : ServiceLoader.load(PMPlatformProvider.class, PMPlatformProvider.class.getClassLoader())) {
                if (provider == null) {
                    continue;
                }
                if (winner == null || provider.priority() > winner.priority()) {
                    winner = provider;
                }
            }
        } catch (ServiceConfigurationError err) {
            if (logger != null) {
                logger.warn("[PMPlatform] Failed to enumerate platform providers via ServiceLoader; falling back to legacy.", err);
            }
        } catch (Throwable t) {
            if (logger != null) {
                logger.warn("[PMPlatform] Unexpected error while loading platform providers; falling back to legacy.", t);
            }
        }

        if (winner != null) {
            try {
                resolved = winner.create();
            } catch (Throwable t) {
                if (logger != null) {
                    logger.warn("[PMPlatform] Provider '{}' failed to create platform; falling back to legacy.", winner.id(), t);
                }
                resolved = null;
            }
        }

        if (resolved == null) {
            resolved = LegacyPlatform.INSTANCE;
        }

        platform = resolved;

        if (logger != null) {
            logger.info("[PMPlatform] Using platform: {} ({}) | java={} | modernTarget={}",
                    resolved.id(),
                    resolved.displayName(),
                    detectRuntimeJavaMajor(),
                    resolved.isModernJvmTarget());
        }
    }

    public static PMPlatform get() {
        PMPlatform p = platform;
        if (p == null) {
            bootstrap(null);
            p = platform;
        }
        return p;
    }

    /**
     * Best-effort runtime Java major version detection compatible with Java 8.
     */
    public static int detectRuntimeJavaMajor() {
        // Java 8: "1.8"; Java 9+: "9", "21", ...
        String spec = System.getProperty("java.specification.version");
        if (spec == null || spec.trim().isEmpty()) {
            return -1;
        }
        spec = spec.trim();
        try {
            if (spec.startsWith("1.")) {
                return Integer.parseInt(spec.substring(2));
            }
            return Integer.parseInt(spec);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static final class LegacyPlatform implements PMPlatform {
        private static final LegacyPlatform INSTANCE = new LegacyPlatform();

        @Override
        public String id() {
            return "legacy";
        }

        @Override
        public String displayName() {
            return "Legacy (Java 8/RFG)";
        }

        @Override
        public boolean isModernJvmTarget() {
            return false;
        }
    }
}
