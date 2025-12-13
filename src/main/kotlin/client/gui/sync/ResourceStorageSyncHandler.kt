package github.kasuminova.prototypemachinery.client.gui.sync

import com.cleanroommc.modularui.value.sync.SyncHandler
import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.impl.storage.ResourceStorageImpl
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.network.PacketBuffer

/**
 * # ResourceStorageSyncHandler - Storage Synchronization Handler
 * # ResourceStorageSyncHandler - 存储同步处理器
 *
 * Handles synchronization of [ResourceStorageImpl] between server and client.
 * Supports both full sync and incremental updates.
 *
 * 处理 [ResourceStorageImpl] 在服务器和客户端之间的同步。
 * 支持全量同步和增量更新。
 *
 * @param K The specific PMKey type
 * @param storage The storage to synchronize
 * @param keyWriter Function to write a key to NBT
 * @param keyReader Function to read a key from NBT
 */
public class ResourceStorageSyncHandler<K : PMKey<*>>(
    private val storage: ResourceStorageImpl<K>,
    private val keyWriter: (K, NBTTagCompound) -> Unit,
    private val keyReader: (NBTTagCompound) -> K?
) : SyncHandler() {

    private companion object {
        const val SYNC_FULL = 0
        const val SYNC_INCREMENTAL = 1
        const val SYNC_SLOT_UPDATE = 2
        const val SYNC_CLEAR = 3
    }

    // Client-side mirror of the storage for change detection
    private var lastSyncedResources: MutableMap<Any, Long> = mutableMapOf()

    override fun detectAndSendChanges(init: Boolean) {
        if (init) {
            // Send full sync on init
            syncToClient(SYNC_FULL) { buffer ->
                writeFullSync(buffer)
            }
            cacheCurrentState()
            storage.clearPendingChanges()
            return
        }

        if (!storage.hasPendingChanges()) return

        // Check for changes and send incremental updates
        val currentResources = storage.getAllResources()
        val changes = mutableListOf<ResourceSlotChange<K>>()

        // Find added/modified resources
        for (resource in currentResources) {
            val uniqueKey = getUniqueKey(resource)
            val lastAmount = lastSyncedResources[uniqueKey]
            if (lastAmount == null || lastAmount != resource.count) {
                changes.add(ResourceSlotChange(resource, resource.count))
            }
        }

        // Find removed resources
        val currentKeys = currentResources.map { getUniqueKey(it) }.toSet()
        for ((key, _) in lastSyncedResources) {
            if (key !in currentKeys) {
                // Resource was removed - we need to find it to send the removal
                // For now, we'll do a full sync if resources are removed
                syncToClient(SYNC_FULL) { buffer ->
                    writeFullSync(buffer)
                }
                cacheCurrentState()
                storage.clearPendingChanges()
                return
            }
        }

        if (changes.isNotEmpty()) {
            syncToClient(SYNC_INCREMENTAL) { buffer ->
                writeIncrementalSync(buffer, changes)
            }
            cacheCurrentState()
        }

        storage.clearPendingChanges()
    }

    override fun readOnClient(id: Int, buf: PacketBuffer) {
        when (id) {
            SYNC_FULL -> readFullSync(buf)
            SYNC_INCREMENTAL -> readIncrementalSync(buf)
            SYNC_CLEAR -> storage.clear()
        }
    }

    override fun readOnServer(id: Int, buf: PacketBuffer) {
        // Client-to-server sync is typically not needed for storage
        // but can be implemented for specific use cases
    }

    private fun writeFullSync(buffer: PacketBuffer) {
        val resources = storage.getAllResources()
        buffer.writeVarInt(resources.size)
        for (resource in resources) {
            val nbt = NBTTagCompound()
            keyWriter(resource, nbt)
            buffer.writeCompoundTag(nbt)
        }
    }

    private fun readFullSync(buffer: PacketBuffer) {
        storage.clear()
        val count = buffer.readVarInt()
        for (i in 0 until count) {
            val nbt = buffer.readCompoundTag() ?: continue
            val key = keyReader(nbt) ?: continue
            storage.insert(key, key.count, false)
        }
    }

    private fun writeIncrementalSync(buffer: PacketBuffer, changes: List<ResourceSlotChange<K>>) {
        buffer.writeVarInt(changes.size)
        for (change in changes) {
            val nbt = NBTTagCompound()
            keyWriter(change.key, nbt)
            buffer.writeCompoundTag(nbt)
        }
    }

    private fun readIncrementalSync(buffer: PacketBuffer) {
        val count = buffer.readVarInt()
        for (i in 0 until count) {
            val nbt = buffer.readCompoundTag() ?: continue
            val key = keyReader(nbt) ?: continue

            // Update or insert the resource
            val existing = storage.getAmount(key)
            if (existing > 0) {
                // Extract old amount and insert new
                storage.extract(key, existing, false)
            }
            storage.insert(key, key.count, false)
        }
    }

    private fun cacheCurrentState() {
        lastSyncedResources.clear()
        for (resource in storage.getAllResources()) {
            lastSyncedResources[getUniqueKey(resource)] = resource.count
        }
    }

    private fun getUniqueKey(key: K): Any {
        // Use the key itself as the unique identifier since PMKey's equals/hashCode
        // is based on the unique prototype
        return key
    }

    private data class ResourceSlotChange<K : PMKey<*>>(
        val key: K,
        val newAmount: Long
    )

}
