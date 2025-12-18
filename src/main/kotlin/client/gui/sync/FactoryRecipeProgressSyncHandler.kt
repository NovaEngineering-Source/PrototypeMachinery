package github.kasuminova.prototypemachinery.client.gui.sync

import com.cleanroommc.modularui.value.sync.SyncHandler
import github.kasuminova.prototypemachinery.api.machine.component.getFirstComponentOfType
import github.kasuminova.prototypemachinery.api.machine.component.type.FactoryRecipeProcessorComponent
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.common.block.entity.MachineBlockEntity
import net.minecraft.network.PacketBuffer

/**
 * Sync handler for factory recipe processor progress list (server -> client).
 *
 * This is intentionally GUI-scoped: data only flows while a panel is open.
 */
public class FactoryRecipeProgressSyncHandler(
    private val machine: MachineBlockEntity
) : SyncHandler() {

    public data class Entry(
        /** 0-based slot index (displayed as #1, #2, ...). */
        val slotIndex: Int,
        /** Progress percent in [0, 100]. */
        val percent: Int,
        /** Human-readable status message (may be empty). */
        val message: String,
        /** Whether current status is an error. */
        val isError: Boolean
    )

    private companion object {
        const val SYNC_FULL: Int = 0
    }

    @Volatile
    private var lastSentMaxSlots: Int = -1

    @Volatile
    private var lastSentEntries: List<Entry> = emptyList()

    @Volatile
    private var clientMaxSlots: Int = 0

    @Volatile
    private var clientEntries: List<Entry> = emptyList()

    /** Client-side snapshot of max concurrent slots. */
    public fun getMaxSlots(): Int = clientMaxSlots

    /** Client-side snapshot of entries (ordered by slotIndex ascending). */
    public fun getEntries(): List<Entry> = clientEntries

    override fun detectAndSendChanges(init: Boolean) {
        // Only meaningful on server; on client this will be a no-op.
        if (getSyncManager().isClient) return

        val processor = machine.machine.componentMap.getFirstComponentOfType<FactoryRecipeProcessorComponent>()
        val maxSlots = processor?.maxConcurrentProcesses ?: 0
        val processes = processor?.activeProcesses?.toList().orEmpty()

        val entries = buildEntries(processes)

        // For progress UI we accept frequent updates; still avoid spamming if nothing changed.
        if (!init && maxSlots == lastSentMaxSlots && entries == lastSentEntries) return

        lastSentMaxSlots = maxSlots
        lastSentEntries = entries

        syncToClient(SYNC_FULL) { buf ->
            buf.writeVarInt(maxSlots)
            buf.writeVarInt(entries.size)
            for (e in entries) {
                buf.writeVarInt(e.slotIndex)
                buf.writeVarInt(e.percent)
                buf.writeBoolean(e.isError)
                buf.writeString(e.message)
            }
        }
    }

    override fun readOnClient(id: Int, buf: PacketBuffer) {
        when (id) {
            SYNC_FULL -> {
                val maxSlots = buf.readVarInt()
                val count = buf.readVarInt()
                val list = ArrayList<Entry>(count)
                for (i in 0 until count) {
                    val slotIndex = buf.readVarInt()
                    val percent = buf.readVarInt().coerceIn(0, 100)
                    val isError = buf.readBoolean()
                    val message = buf.readString(Short.MAX_VALUE.toInt())
                    list.add(Entry(slotIndex = slotIndex, percent = percent, message = message, isError = isError))
                }

                clientMaxSlots = maxSlots.coerceAtLeast(0)
                clientEntries = list.sortedBy { it.slotIndex }
            }
        }
    }

    override fun readOnServer(id: Int, buf: PacketBuffer) {
        // No client -> server traffic.
    }

    private fun buildEntries(processes: List<RecipeProcess>): List<Entry> {
        if (processes.isEmpty()) return emptyList()

        return processes.mapIndexed { idx, process ->
            val percent = computePercent(process)
            Entry(
                slotIndex = idx,
                percent = percent,
                message = process.status.message,
                isError = process.status.isError
            )
        }
    }

    private fun computePercent(process: RecipeProcess): Int {
        val duration = process.recipe.durationTicks.coerceAtLeast(0).toFloat()
        if (duration <= 0.0f) return 100

        val p = process.status.progress
        val raw = ((p / duration) * 100.0f)
        val clamped = raw.coerceIn(0.0f, 100.0f)

        // Spec wants 1%..100% for running processes; avoid showing 0% when a process exists.
        val asInt = clamped.toInt()
        return if (asInt <= 0) 1 else asInt
    }
}
