package github.kasuminova.prototypemachinery.client.gui.sync

import com.cleanroommc.modularui.value.sync.SyncHandler
import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.impl.key.item.PMItemKey
import github.kasuminova.prototypemachinery.impl.storage.ItemResourceStorage
import github.kasuminova.prototypemachinery.impl.storage.ResourceStorageImpl
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.network.PacketBuffer

/**
 * # ResourceSlotSyncHandler - Individual Slot Synchronization
 * # ResourceSlotSyncHandler - 单个槽位同步处理器
 *
 * Handles synchronization of individual slots within a [ResourceStorageImpl].
 * Provides interaction handling for GUI widgets.
 *
 * 处理 [ResourceStorageImpl] 中单个槽位的同步。
 * 为 GUI 组件提供交互处理。
 *
 * @param K The specific PMKey type
 */
public class ResourceSlotSyncHandler<K : PMKey<*>>(
    private val storage: ResourceStorageImpl<K>,
    private val keyWriter: (K, NBTTagCompound) -> Unit,
    private val keyReader: (NBTTagCompound) -> K?
) : SyncHandler() {

    private companion object {
        const val SYNC_FULL = 0
        const val SYNC_SLOT_UPDATE = 1
        const val SYNC_SLOT_REMOVE = 2
        const val CLICK_INSERT = 10
        const val CLICK_EXTRACT = 11
        const val CLICK_SCROLL = 12
    }

    // Ordered list of resources for slot indexing
    private var resourceList: MutableList<K> = mutableListOf()
    private var lastSyncedList: List<Pair<Any, Long>> = emptyList()

    override fun detectAndSendChanges(init: Boolean) {
        updateResourceList()

        if (init) {
            syncToClient(SYNC_FULL) { buffer ->
                writeFullSync(buffer)
            }
            cacheCurrentState()
            storage.clearPendingChanges()
            return
        }

        if (!storage.hasPendingChanges()) return

        // Check for changes
        val currentList = resourceList.map { it to it.count }

        if (currentList.size != lastSyncedList.size) {
            // Size changed, do full sync
            syncToClient(SYNC_FULL) { buffer ->
                writeFullSync(buffer)
            }
        } else {
            // Check for individual slot changes
            for (i in currentList.indices) {
                val (currentKey, currentCount) = currentList[i]
                if (i < lastSyncedList.size) {
                    val (lastKey, lastCount) = lastSyncedList[i]
                    if (currentKey != lastKey || currentCount != lastCount) {
                        // Slot changed
                        syncToClient(SYNC_SLOT_UPDATE) { buffer ->
                            buffer.writeVarInt(i)
                            val nbt = NBTTagCompound()
                            keyWriter(currentKey, nbt)
                            buffer.writeCompoundTag(nbt)
                        }
                    }
                }
            }
        }

        cacheCurrentState()
        storage.clearPendingChanges()
    }

    override fun readOnClient(id: Int, buf: PacketBuffer) {
        when (id) {
            SYNC_FULL -> readFullSync(buf)
            SYNC_SLOT_UPDATE -> {
                val index = buf.readVarInt()
                val nbt = buf.readCompoundTag() ?: return
                val key = keyReader(nbt) ?: return
                if (index < resourceList.size) {
                    resourceList[index] = key
                } else {
                    resourceList.add(key)
                }
            }

            SYNC_SLOT_REMOVE -> {
                val index = buf.readVarInt()
                if (index < resourceList.size) {
                    resourceList.removeAt(index)
                }
            }
        }
    }

    override fun readOnServer(id: Int, buf: PacketBuffer) {
        when (id) {
            CLICK_INSERT -> {
                val slotIndex = buf.readVarInt()
                val mouseButton = buf.readVarInt()
                val shift = buf.readBoolean()
                handleInsertClick(slotIndex, mouseButton, shift)
            }

            CLICK_EXTRACT -> {
                val slotIndex = buf.readVarInt()
                val mouseButton = buf.readVarInt()
                val shift = buf.readBoolean()
                handleExtractClick(slotIndex, mouseButton, shift)
            }

            CLICK_SCROLL -> {
                val slotIndex = buf.readVarInt()
                val direction = buf.readVarInt()
                val shift = buf.readBoolean()
                handleScrollServer(slotIndex, direction, shift)
            }
        }
    }

    /**
     * Gets the resource at a specific slot index.
     * 获取特定槽位索引处的资源。
     */
    public fun getResourceAt(index: Int): K? {
        updateResourceList()
        return if (index in resourceList.indices) resourceList[index] else null
    }

    /**
     * Gets the total number of slots.
     * 获取槽位总数。
     */
    public fun getSlotCount(): Int {
        updateResourceList()
        return resourceList.size
    }

    /**
     * Handles a click interaction from the widget.
     * 处理来自组件的点击交互。
     */
    public fun handleClick(slotIndex: Int, mouseButton: Int, shift: Boolean, canInsert: Boolean, canExtract: Boolean) {
        if (canExtract && mouseButton == 1) {
            // Right click - extract
            syncToServer(CLICK_EXTRACT) { buffer ->
                buffer.writeVarInt(slotIndex)
                buffer.writeVarInt(mouseButton)
                buffer.writeBoolean(shift)
            }
        } else if (canInsert && mouseButton == 0) {
            // Left click - insert
            syncToServer(CLICK_INSERT) { buffer ->
                buffer.writeVarInt(slotIndex)
                buffer.writeVarInt(mouseButton)
                buffer.writeBoolean(shift)
            }
        }
    }

    /**
     * Handles a scroll interaction from the widget.
     * 处理来自组件的滚轮交互。
     */
    public fun handleScroll(slotIndex: Int, direction: Int, shift: Boolean) {
        syncToServer(CLICK_SCROLL) { buffer ->
            buffer.writeVarInt(slotIndex)
            buffer.writeVarInt(direction)
            buffer.writeBoolean(shift)
        }
    }

    private fun handleInsertClick(slotIndex: Int, mouseButton: Int, shift: Boolean) {
        val player = syncManager.player
        val cursorStack = player.inventory.itemStack
        if (cursorStack.isEmpty) return

        // Only handle item storage for now
        if (storage !is ItemResourceStorage) return
        val itemStorage = storage as ItemResourceStorage

        val insertAmount = if (shift) cursorStack.count.toLong() else 1L
        val inserted = itemStorage.insertStack(cursorStack.copy().apply { count = insertAmount.toInt() }, false)

        if (inserted > 0) {
            cursorStack.shrink(inserted.toInt())
            if (cursorStack.isEmpty) {
                player.inventory.itemStack = ItemStack.EMPTY
            }
            syncManager.setCursorItem(player.inventory.itemStack)
        }
    }

    private fun handleExtractClick(slotIndex: Int, mouseButton: Int, shift: Boolean) {
        updateResourceList()
        if (slotIndex !in resourceList.indices) return

        val resource = resourceList[slotIndex]
        if (resource !is PMItemKey) return

        // Only handle item storage for now
        if (storage !is ItemResourceStorage) return
        val itemStorage = storage as ItemResourceStorage

        val player = syncManager.player
        val cursorStack = player.inventory.itemStack

        // If player already holds something different, do nothing (avoid voiding items).
        val template = resource.uniqueKey.createStack(1)
        if (template.isEmpty) return
        if (!cursorStack.isEmpty && !ItemStack.areItemStacksEqual(cursorStack, template)) {
            return
        }

        val requested = if (shift) template.maxStackSize else 1

        val maxFit = if (cursorStack.isEmpty) {
            template.maxStackSize
        } else {
            (cursorStack.maxStackSize - cursorStack.count).coerceAtLeast(0)
        }
        val toExtract = minOf(requested, maxFit)
        if (toExtract <= 0) return

        val extracted = itemStorage.extractStackResult(template, toExtract, false)
        if (extracted.isEmpty) return

        if (cursorStack.isEmpty) {
            player.inventory.itemStack = extracted
        } else {
            // Same item guaranteed by checks above.
            cursorStack.grow(extracted.count)
        }

        syncManager.setCursorItem(player.inventory.itemStack)
    }

    private fun handleScrollServer(slotIndex: Int, direction: Int, shift: Boolean) {
        // Scroll can be used for phantom slot quantity adjustment
        // Implementation depends on specific requirements
    }

    private fun writeFullSync(buffer: PacketBuffer) {
        buffer.writeVarInt(resourceList.size)
        for (resource in resourceList) {
            val nbt = NBTTagCompound()
            keyWriter(resource, nbt)
            buffer.writeCompoundTag(nbt)
        }
    }

    private fun readFullSync(buffer: PacketBuffer) {
        resourceList.clear()
        val count = buffer.readVarInt()
        for (i in 0 until count) {
            val nbt = buffer.readCompoundTag() ?: continue
            val key = keyReader(nbt) ?: continue
            resourceList.add(key)
        }
    }

    private fun updateResourceList() {
        // detectAndSendChanges() is server-side only; client must rely on synced resourceList.
        // If we rebuild from client-side storage here, GUI will show empty/stale data.
        if (getSyncManager().isClient) return
        resourceList = storage.getAllResources().toMutableList()
    }

    private fun cacheCurrentState() {
        lastSyncedList = resourceList.map { it to it.count }
    }

}
