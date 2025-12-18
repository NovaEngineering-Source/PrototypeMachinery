package github.kasuminova.prototypemachinery.integration.jei.api.ui

/**
 * Minimal indirection for collecting UI widgets without hard-binding to ModularUI types.
 *
 * The actual runtime will decide what concrete widget types are accepted.
 * Keeping this as `Any` reduces coupling and makes hot-reload safer.
 */
public interface PMJeiWidgetCollector {
    public fun add(widget: Any)
}
