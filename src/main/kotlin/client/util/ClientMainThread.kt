package github.kasuminova.prototypemachinery.client.util

import net.minecraft.client.Minecraft
import java.util.concurrent.Callable

/**
 * Small helper to execute code on the Minecraft client main thread.
 *
 * NOTE: In 1.12.2 the main thread typically owns the GL context, so this is also where
 * GL/VBO operations must happen.
 */
internal object ClientMainThread {

    internal fun <T> call(task: () -> T): T {
        val mc = Minecraft.getMinecraft()
        if (mc.isCallingFromMinecraftThread) {
            return task()
        }
        return mc.addScheduledTask(Callable { task() }).get()
    }

    internal fun run(task: () -> Unit) {
        val mc = Minecraft.getMinecraft()
        if (mc.isCallingFromMinecraftThread) {
            task()
        } else {
            mc.addScheduledTask { task() }
        }
    }
}
