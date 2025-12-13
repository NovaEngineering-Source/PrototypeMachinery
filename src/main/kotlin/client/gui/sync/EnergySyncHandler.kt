package github.kasuminova.prototypemachinery.client.gui.sync

import com.cleanroommc.modularui.value.sync.SyncHandler
import github.kasuminova.prototypemachinery.impl.storage.EnergyStorageImpl
import net.minecraft.network.PacketBuffer

/**
 * # EnergySyncHandler - Energy Storage Synchronization Handler
 * # EnergySyncHandler - 能量存储同步处理器
 *
 * Handles synchronization of [EnergyStorageImpl] between server and client.
 * Supports Long values and incremental updates.
 *
 * 处理 [EnergyStorageImpl] 在服务器和客户端之间的同步。
 * 支持 Long 值和增量更新。
 *
 * @param storage The energy storage to synchronize
 */
public class EnergySyncHandler(
    private val storage: EnergyStorageImpl
) : SyncHandler() {

    private companion object {
        const val SYNC_FULL = 0
        const val SYNC_ENERGY = 1
        const val SYNC_CAPACITY = 2
        const val SYNC_RATES = 3
    }

    private var lastEnergy: Long = -1
    private var lastCapacity: Long = -1
    private var lastMaxReceive: Long = -1
    private var lastMaxExtract: Long = -1

    override fun detectAndSendChanges(init: Boolean) {
        if (init) {
            // Send full sync on init
            syncToClient(SYNC_FULL) { buffer ->
                buffer.writeLong(storage.energy)
                buffer.writeLong(storage.capacity)
                buffer.writeLong(storage.maxReceive)
                buffer.writeLong(storage.maxExtract)
            }
            cacheCurrentState()
            storage.clearPendingChanges()
            return
        }

        var synced = false

        // Check for energy change
        if (storage.energy != lastEnergy) {
            syncToClient(SYNC_ENERGY) { buffer ->
                buffer.writeLong(storage.energy)
            }
            lastEnergy = storage.energy
            synced = true
        }

        // Check for capacity change
        if (storage.capacity != lastCapacity) {
            syncToClient(SYNC_CAPACITY) { buffer ->
                buffer.writeLong(storage.capacity)
            }
            lastCapacity = storage.capacity
            synced = true
        }

        // Check for rate changes
        if (storage.maxReceive != lastMaxReceive || storage.maxExtract != lastMaxExtract) {
            syncToClient(SYNC_RATES) { buffer ->
                buffer.writeLong(storage.maxReceive)
                buffer.writeLong(storage.maxExtract)
            }
            lastMaxReceive = storage.maxReceive
            lastMaxExtract = storage.maxExtract
            synced = true
        }

        if (synced) {
            storage.clearPendingChanges()
        }
    }

    override fun readOnClient(id: Int, buf: PacketBuffer) {
        when (id) {
            SYNC_FULL -> {
                val energy = buf.readLong()
                storage.capacity = buf.readLong()
                storage.maxReceive = buf.readLong()
                storage.maxExtract = buf.readLong()
                // Apply energy after capacity so it clamps against the correct limit.
                storage.setEnergy(energy)
            }

            SYNC_ENERGY -> {
                storage.setEnergy(buf.readLong())
            }

            SYNC_CAPACITY -> {
                storage.capacity = buf.readLong()
                // Clamp current energy if capacity shrinks.
                storage.setEnergy(storage.energy)
            }

            SYNC_RATES -> {
                storage.maxReceive = buf.readLong()
                storage.maxExtract = buf.readLong()
            }
        }
    }

    override fun readOnServer(id: Int, buf: PacketBuffer) {
        // Handle client-to-server updates if needed (e.g., rate adjustments)
        when (id) {
            SYNC_RATES -> {
                storage.maxReceive = buf.readLong()
                storage.maxExtract = buf.readLong()
            }
        }
    }

    /**
     * Sends rate changes to the server.
     * 向服务器发送速率变更。
     */
    public fun syncRatesToServer(maxReceive: Long, maxExtract: Long) {
        syncToServer(SYNC_RATES) { buffer ->
            buffer.writeLong(maxReceive)
            buffer.writeLong(maxExtract)
        }
    }

    private fun cacheCurrentState() {
        lastEnergy = storage.energy
        lastCapacity = storage.capacity
        lastMaxReceive = storage.maxReceive
        lastMaxExtract = storage.maxExtract
    }

}
