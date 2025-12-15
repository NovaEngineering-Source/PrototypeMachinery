package github.kasuminova.prototypemachinery.client.preview

import net.minecraft.client.settings.KeyBinding
import net.minecraftforge.fml.client.registry.ClientRegistry
import org.lwjgl.input.Keyboard

/**
 * Key bindings for structure projection preview.
 */
internal object ProjectionKeyBindings {

    private const val CATEGORY = "PrototypeMachinery"

    // Toggle lock/unlock projection orientation.
    val toggleOrientationLock: KeyBinding = KeyBinding(
        "key.pm.preview.lock_orientation",
        Keyboard.KEY_R,
        CATEGORY
    )

    // Rotate locked orientation (use modifiers to choose axis).
    // - no modifier: Yaw (around UP)
    // - Shift: Pitch (around EAST)
    // - Ctrl: Roll (around SOUTH)
    val rotatePositive: KeyBinding = KeyBinding(
        "key.pm.preview.rotate_positive",
        Keyboard.KEY_RBRACKET,
        CATEGORY
    )

    val rotateNegative: KeyBinding = KeyBinding(
        "key.pm.preview.rotate_negative",
        Keyboard.KEY_LBRACKET,
        CATEGORY
    )

    private var registered: Boolean = false

    fun register() {
        if (registered) return
        registered = true

        ClientRegistry.registerKeyBinding(toggleOrientationLock)
        ClientRegistry.registerKeyBinding(rotatePositive)
        ClientRegistry.registerKeyBinding(rotateNegative)
    }
}
