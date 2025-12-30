package github.kasuminova.prototypemachinery.modernbackend.accel;

/**
 * Backend selector.
 *
 * <p>All reflective probing is done once during class initialization.
 * Hot paths should only call {@link #get()} and use the returned backend.
 */
public final class PositionTransformBackends {

    // IMPORTANT: do not inline the class name constant into other classes that must stay Java8-safe.
    // Keeping it here ensures only this class performs the optional lookup.
    private static final String VECTOR_IMPL_CLASS =
            "github.kasuminova.prototypemachinery.modernbackend.accel.vector.VectorPositionTransformBackend";

    private static final PositionTransformBackend BACKEND = loadBackend();

    private PositionTransformBackends() {
    }

    public static PositionTransformBackend get() {
        return BACKEND;
    }

    private static PositionTransformBackend loadBackend() {
        // Allow forcing scalar for A/B testing.
        if (Boolean.getBoolean("pm.forceScalarTransform")) {
            return ScalarPositionTransformBackend.INSTANCE;
        }

        final boolean debug = Boolean.getBoolean("pm.debugTransformBackend");

        try {
            final Class<?> cls = Class.forName(VECTOR_IMPL_CLASS, true, PositionTransformBackends.class.getClassLoader());
            final Object inst = cls.getDeclaredConstructor().newInstance();
            if (inst instanceof PositionTransformBackend) {
                return (PositionTransformBackend) inst;
            }
            if (debug) {
                System.err.println("[PM] Vector backend class loaded but does not implement PositionTransformBackend: " + inst.getClass());
            }
        } catch (Throwable t) {
            // Expected when:
            // - the optional accel jar is not present
            // - --add-modules=jdk.incubator.vector is missing
            // - the running JDK does not provide the incubator module
            if (debug) {
                System.err.println("[PM] Failed to load VectorPositionTransformBackend: " + t);
                t.printStackTrace(System.err);
            }
        }

        return ScalarPositionTransformBackend.INSTANCE;
    }
}
