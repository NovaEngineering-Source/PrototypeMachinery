package org.lwjgl.opengl;

/**
 * Minimal compatibility stub for environments that provide a LWJGL2-like API surface on top of LWJGL3
 * but omit {@code org.lwjgl.opengl.OpenGLException}.
 *
 * <p>Minecraft 1.12.2 references this type during client bootstrap.
 * Cleanroom/LWJGLXX provides most of LWJGL2 compatibility classes, but some builds may miss this one.
 * This stub is intentionally tiny and only exists to satisfy class loading.</p>
 */
public class OpenGLException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public OpenGLException() {
        super();
    }

    public OpenGLException(String message) {
        super(message);
    }

    public OpenGLException(String message, Throwable cause) {
        super(message, cause);
    }

    public OpenGLException(Throwable cause) {
        super(cause);
    }
}
