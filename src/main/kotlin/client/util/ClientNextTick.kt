package github.kasuminova.prototypemachinery.client.util

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Runs tasks on the next client tick (END phase).
 *
 * Why not use Minecraft#addScheduledTask?
 * - When called from the main client thread (e.g. chat command execution), it may run immediately.
 * - Some vanilla screens (like chat) close at end-of-tick and can override a screen opened immediately.
 */
internal object ClientNextTick {

    private val queue: ConcurrentLinkedQueue<() -> Unit> = ConcurrentLinkedQueue()

    fun enqueue(task: () -> Unit) {
        queue.add(task)
    }

    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.END) return

        // Drain queue.
        while (true) {
            val t = queue.poll() ?: break
            try {
                t.invoke()
            } catch (_: Throwable) {
                // Intentionally swallow: opening UI should never crash the game.
            }
        }
    }
}
