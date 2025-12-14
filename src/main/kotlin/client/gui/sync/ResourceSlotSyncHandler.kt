package github.kasuminova.prototypemachinery.client.gui.sync

import com.cleanroommc.modularui.value.sync.SyncHandler
import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.api.storage.SlottedResourceStorage
import github.kasuminova.prototypemachinery.impl.key.item.PMItemKey
import github.kasuminova.prototypemachinery.impl.storage.ItemResourceStorage
import net.minecraft.entity.player.InventoryPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.network.PacketBuffer

/**
 * # ResourceSlotSyncHandler - Individual Slot Synchronization
 * # ResourceSlotSyncHandler - 单个槽位同步处理器
 *
 * Handles synchronization of individual slots within a [SlottedResourceStorage].
 * Provides interaction handling for GUI widgets.
 *
 * 处理 [SlottedResourceStorage] 中单个槽位的同步。
 * 为 GUI 组件提供交互处理。
 *
 * @param K The specific PMKey type
 */
public class ResourceSlotSyncHandler<K : PMKey<*>>(
    private val storage: SlottedResourceStorage<K>,
    private val keyWriter: (K, NBTTagCompound) -> Unit,
    private val keyReader: (NBTTagCompound) -> K?
) : SyncHandler() {

    private companion object {
        const val SYNC_FULL = 0
        const val SYNC_SLOT_UPDATE = 1
        const val CLICK_INSERT = 10
        const val CLICK_EXTRACT = 11
        const val CLICK_SCROLL = 12
        const val CLICK_QUICK_MOVE_EXTRACT = 13
        const val CLICK_INSERT_ALL_SAME = 14
        const val CLICK_EXTRACT_ALL_SAME = 15
        const val CLICK_QUICK_MOVE_INSERT_FROM_PLAYER = 16
    }

    // Fixed slot list (nullable) for stable indices.
    private var resourceList: MutableList<K?> = mutableListOf()
    private var lastSyncedList: MutableList<Pair<K?, Long>> = mutableListOf()

    override fun detectAndSendChanges(init: Boolean) {
        if (init) {
            updateResourceListFull()
            syncToClient(SYNC_FULL) { buffer ->
                writeFullSync(buffer)
            }
            cacheCurrentState()
            storage.clearPendingChanges()
            return
        }

        if (!storage.hasPendingChanges()) return

        val count = storage.slotCount
        ensureListsSized(count)

        // Incremental: only refresh & diff dirty slots.
        val dirtySlots = storage.drainPendingSlotChanges()
        if (dirtySlots.isEmpty()) {
            // Fallback: if implementation didn't record dirty slots properly, do a safe full scan.
            updateResourceListFull()
            syncToClient(SYNC_FULL) { buffer ->
                writeFullSync(buffer)
            }
            cacheCurrentState()
            storage.clearPendingChanges()
            return
        }

        for (slot in dirtySlots) {
            if (slot !in 0 until count) continue
            val currentKey = storage.getSlot(slot)
            resourceList[slot] = currentKey

            val currentCount = currentKey?.count ?: 0L
            val (lastKey, lastCount) = lastSyncedList[slot]

            val changed = when {
                currentKey == null && lastKey == null -> false
                currentKey == null && lastKey != null -> true
                currentKey != null && lastKey == null -> true
                else -> (currentKey != lastKey) || (currentCount != lastCount)
            }

            if (changed) {
                syncToClient(SYNC_SLOT_UPDATE) { buffer ->
                    buffer.writeVarInt(slot)
                    if (currentKey == null) {
                        buffer.writeBoolean(false)
                    } else {
                        buffer.writeBoolean(true)
                        val nbt = NBTTagCompound()
                        keyWriter(currentKey, nbt)
                        buffer.writeCompoundTag(nbt)
                    }
                }
            }

            lastSyncedList[slot] = currentKey to currentCount
        }

        storage.clearPendingChanges()
    }

    override fun readOnClient(id: Int, buf: PacketBuffer) {
        when (id) {
            SYNC_FULL -> readFullSync(buf)
            SYNC_SLOT_UPDATE -> {
                val index = buf.readVarInt()
                val present = buf.readBoolean()
                val key: K? = if (present) {
                    val nbt = buf.readCompoundTag() ?: return
                    keyReader(nbt)
                } else {
                    null
                }

                if (index !in 0 until resourceList.size) return
                resourceList[index] = key
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

            CLICK_QUICK_MOVE_EXTRACT -> {
                val slotIndex = buf.readVarInt()
                val mouseButton = buf.readVarInt()
                handleQuickMoveExtractToInventory(slotIndex, mouseButton)
            }

            CLICK_INSERT_ALL_SAME -> {
                val slotIndex = buf.readVarInt()
                val mouseButton = buf.readVarInt()
                handleInsertAllSameFromPlayer(slotIndex, mouseButton)
            }

            CLICK_EXTRACT_ALL_SAME -> {
                val slotIndex = buf.readVarInt()
                val mouseButton = buf.readVarInt()
                handleExtractAllSameToPlayer(slotIndex, mouseButton)
            }

            CLICK_QUICK_MOVE_INSERT_FROM_PLAYER -> {
                val playerInvSlot = buf.readVarInt()
                val mouseButton = buf.readVarInt()
                handleQuickMoveInsertFromPlayerInventory(playerInvSlot, mouseButton)
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
     * Maximum number of different resource types this storage can hold.
     * 此存储可容纳的最大不同资源类型数量。
     */
    public fun getMaxTypes(): Int = storage.maxTypes

    /**
     * Maximum count per type.
     * 每种类型的最大数量。
     */
    public fun getMaxCountPerType(): Long = storage.maxCountPerType

    /**
     * Current used type count.
     * 当前已使用的类型数量。
     */
    public fun getUsedTypes(): Int {
        // On client, the underlying storage instance is not authoritative; rely on synced list.
        // 客户端侧底层 storage 不一定权威；以已同步的列表为准。
        return if (getSyncManager().isClient) {
            resourceList.count { it != null }
        } else {
            storage.usedTypes
        }
    }

    /**
     * Handles a click interaction from the widget.
     * 处理来自组件的点击交互。
     */
    public fun handleClick(slotIndex: Int, mouseButton: Int, shift: Boolean, canInsert: Boolean, canExtract: Boolean) {
        // Keep legacy behavior for callers that still use this method:
        // - Right click -> extract
        // - Left click  -> insert
        if (canExtract && mouseButton == 1) {
            requestExtract(slotIndex, mouseButton, shift)
        } else if (canInsert && mouseButton == 0) {
            requestInsert(slotIndex, mouseButton, shift)
        }
    }

    /**
     * Sends an insert request to the server.
     * 向服务端发送插入请求。
     */
    public fun requestInsert(slotIndex: Int, mouseButton: Int, shift: Boolean) {
        syncToServer(CLICK_INSERT) { buffer ->
            buffer.writeVarInt(slotIndex)
            buffer.writeVarInt(mouseButton)
            buffer.writeBoolean(shift)
        }
    }

    /**
     * Sends an extract request to the server.
     * 向服务端发送取出请求。
     */
    public fun requestExtract(slotIndex: Int, mouseButton: Int, shift: Boolean) {
        syncToServer(CLICK_EXTRACT) { buffer ->
            buffer.writeVarInt(slotIndex)
            buffer.writeVarInt(mouseButton)
            buffer.writeBoolean(shift)
        }
    }

    /**
     * Shift-click like vanilla quick move: extract from storage directly into player inventory.
     * 类似原版 Shift-click 快速转移：直接从仓库取出到玩家背包。
     */
    public fun requestQuickMoveExtract(slotIndex: Int, mouseButton: Int) {
        syncToServer(CLICK_QUICK_MOVE_EXTRACT) { buffer ->
            buffer.writeVarInt(slotIndex)
            buffer.writeVarInt(mouseButton)
        }
    }

    /**
     * Double-click helper: insert all stacks of the same item from cursor + inventory into storage.
     * 双击辅助：把“同种物品”从光标 + 背包尽可能全部塞入仓库。
     */
    public fun requestInsertAllSame(slotIndex: Int, mouseButton: Int) {
        syncToServer(CLICK_INSERT_ALL_SAME) { buffer ->
            buffer.writeVarInt(slotIndex)
            buffer.writeVarInt(mouseButton)
        }
    }

    /**
     * Shift-click helper: move a stack from the specified player inventory slot into this storage.
     * Shift-click 辅助：把玩家背包某个槽位里的物品尽可能塞入该仓库。
     */
    public fun requestQuickMoveInsertFromPlayerInventory(playerInvSlot: Int, mouseButton: Int) {
        syncToServer(CLICK_QUICK_MOVE_INSERT_FROM_PLAYER) { buffer ->
            buffer.writeVarInt(playerInvSlot)
            buffer.writeVarInt(mouseButton)
        }
    }

    /**
     * Double-click helper: extract as many as possible of the same item into player inventory.
     * 双击辅助：把“同种物品”尽可能全部取出到玩家背包。
     */
    public fun requestExtractAllSame(slotIndex: Int, mouseButton: Int) {
        syncToServer(CLICK_EXTRACT_ALL_SAME) { buffer ->
            buffer.writeVarInt(slotIndex)
            buffer.writeVarInt(mouseButton)
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

        // Insertion amount policy (vanilla-like):
        // - Left click: insert as much as possible from the cursor stack ("一组")
        // - Right click: insert 1
        // - Shift: insert as much as possible (usually the whole cursor stack)
        val insertAmount = when {
            shift -> cursorStack.count.toLong()
            mouseButton == 1 -> 1L
            else -> cursorStack.count.toLong()
        }
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

        val resource = resourceList[slotIndex] ?: return
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

        // Extraction amount policy (vanilla-like):
        // - Left click: take one stack ("一组")
        // - Right click: take one item
        // - Shift: take one full stack (regardless of mouse button)
        val requested = when {
            shift -> template.maxStackSize
            mouseButton == 1 -> 1
            else -> template.maxStackSize
        }

        val maxFit = if (cursorStack.isEmpty) {
            template.maxStackSize
        } else {
            (cursorStack.maxStackSize - cursorStack.count).coerceAtLeast(0)
        }
        val toExtract = minOf(requested, maxFit)
        if (toExtract <= 0) return

        val extractedAmount = itemStorage.extractFromSlot(slotIndex, toExtract.toLong(), false)
        if (extractedAmount <= 0L) return

        val extracted = template.copy()
        extracted.count = extractedAmount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        if (cursorStack.isEmpty) {
            player.inventory.itemStack = extracted
        } else {
            // Same item guaranteed by checks above.
            cursorStack.grow(extracted.count)
        }

        syncManager.setCursorItem(player.inventory.itemStack)
    }

    private fun handleQuickMoveExtractToInventory(slotIndex: Int, mouseButton: Int) {
        updateResourceList()
        if (slotIndex !in resourceList.indices) return

        val resource = resourceList[slotIndex] ?: return
        if (resource !is PMItemKey) return
        if (storage !is ItemResourceStorage) return

        val player = syncManager.player
        val cursorStack = player.inventory.itemStack
        if (!cursorStack.isEmpty) {
            // Quick-move should not conflict with cursor stacking rules; avoid accidental voiding.
            return
        }

        val template = resource.uniqueKey.createStack(1)
        if (template.isEmpty) return

        val requested = if (mouseButton == 1) 1 else template.maxStackSize
        val fit = computeInventoryFit(player.inventory, template)
        val toExtract = minOf(requested, fit)
        if (toExtract <= 0) return

        val itemStorage = storage as ItemResourceStorage
        val extractedAmount = itemStorage.extractFromSlot(slotIndex, toExtract.toLong(), false)
        if (extractedAmount <= 0L) return

        val extracted = template.copy()
        extracted.count = extractedAmount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        val success = player.inventory.addItemStackToInventory(extracted)
        if (!success) {
            // Should not happen if fit was computed correctly, but keep it safe.
            itemStorage.insertStack(extracted, false)
        }

        player.inventory.markDirty()
        player.openContainer.detectAndSendChanges()
    }

    private fun handleQuickMoveInsertFromPlayerInventory(playerInvSlot: Int, mouseButton: Int) {
        // mouseButton reserved for future UX (e.g. half-stack), currently always insert as much as possible.
        val player = syncManager.player

        // For safety and vanilla-feel, only quick-move when cursor is empty.
        if (!player.inventory.itemStack.isEmpty) return

        if (storage !is ItemResourceStorage) return
        val itemStorage = storage as ItemResourceStorage

        val inv = player.inventory
        if (playerInvSlot !in 0 until inv.mainInventory.size) return

        val stackInSlot = inv.mainInventory[playerInvSlot]
        if (stackInSlot.isEmpty) return

        val inserted = itemStorage.insertStack(stackInSlot.copy(), false)
        if (inserted <= 0L) return

        stackInSlot.shrink(inserted.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        if (!stackInSlot.isEmpty && stackInSlot.count <= 0) {
            inv.mainInventory[playerInvSlot] = ItemStack.EMPTY
        } else if (stackInSlot.isEmpty) {
            inv.mainInventory[playerInvSlot] = ItemStack.EMPTY
        }

        inv.markDirty()
        player.openContainer.detectAndSendChanges()
    }

    private fun handleInsertAllSameFromPlayer(slotIndex: Int, mouseButton: Int) {
        // slotIndex/mouseButton are currently not used, but kept for future UX variations.
        val player = syncManager.player
        if (storage !is ItemResourceStorage) return
        val itemStorage = storage as ItemResourceStorage

        val cursorStack = player.inventory.itemStack
        if (cursorStack.isEmpty) return

        val template = cursorStack.copy().apply { count = 1 }

        // 1) Insert from cursor first.
        run {
            val inserted = itemStorage.insertStack(cursorStack.copy(), false)
            if (inserted > 0) {
                cursorStack.shrink(inserted.toInt())
                if (cursorStack.isEmpty) {
                    player.inventory.itemStack = ItemStack.EMPTY
                }
                syncManager.setCursorItem(player.inventory.itemStack)
            }
        }

        // 2) Insert from player inventory (main + offhand) for the same item.
        val inv = player.inventory
        fun drainStackInPlace(stack: ItemStack) {
            if (stack.isEmpty) return
            if (!isSameItem(stack, template)) return

            val inserted = itemStorage.insertStack(stack.copy(), false)
            if (inserted > 0) {
                stack.shrink(inserted.toInt())
            }
        }

        for (i in 0 until inv.mainInventory.size) {
            val stack = inv.mainInventory[i]
            drainStackInPlace(stack)
            if (!stack.isEmpty && stack.count <= 0) {
                inv.mainInventory[i] = ItemStack.EMPTY
            }
        }
        for (i in 0 until inv.offHandInventory.size) {
            val stack = inv.offHandInventory[i]
            drainStackInPlace(stack)
            if (!stack.isEmpty && stack.count <= 0) {
                inv.offHandInventory[i] = ItemStack.EMPTY
            }
        }

        inv.markDirty()
        player.openContainer.detectAndSendChanges()
    }

    private fun handleExtractAllSameToPlayer(slotIndex: Int, mouseButton: Int) {
        updateResourceList()
        if (slotIndex !in resourceList.indices) return
        val resource = resourceList[slotIndex]
        if (resource !is PMItemKey) return
        if (storage !is ItemResourceStorage) return

        val player = syncManager.player
        // For safety and vanilla-feel, only extract-all when cursor is empty.
        if (!player.inventory.itemStack.isEmpty) return

        val template = resource.uniqueKey.createStack(1)
        if (template.isEmpty) return

        val fit = computeInventoryFit(player.inventory, template)
        if (fit <= 0) return

        // Avoid huge loops; inventory fit is small anyway.
        var remaining = fit
        val itemStorage = storage as ItemResourceStorage

        while (remaining > 0) {
            val batch = minOf(template.maxStackSize, remaining)
            val extracted = itemStorage.extractStackResult(template, batch, false)
            if (extracted.isEmpty) break

            val success = player.inventory.addItemStackToInventory(extracted)
            if (!success) {
                itemStorage.insertStack(extracted, false)
                break
            }
            remaining -= extracted.count
        }

        player.inventory.markDirty()
        player.openContainer.detectAndSendChanges()
    }

    private fun isSameItem(a: ItemStack, b: ItemStack): Boolean {
        if (a.isEmpty || b.isEmpty) return false
        return ItemStack.areItemsEqual(a, b) && ItemStack.areItemStackTagsEqual(a, b)
    }

    private fun computeInventoryFit(inv: InventoryPlayer, template: ItemStack): Int {
        if (template.isEmpty) return 0

        var fit = 0
        // main inventory
        for (stack in inv.mainInventory) {
            if (stack.isEmpty) {
                fit += template.maxStackSize
                continue
            }
            if (isSameItem(stack, template)) {
                fit += (stack.maxStackSize - stack.count).coerceAtLeast(0)
            }
        }
        // offhand inventory
        for (stack in inv.offHandInventory) {
            if (stack.isEmpty) {
                fit += template.maxStackSize
                continue
            }
            if (isSameItem(stack, template)) {
                fit += (stack.maxStackSize - stack.count).coerceAtLeast(0)
            }
        }
        return fit
    }

    private fun handleScrollServer(slotIndex: Int, direction: Int, shift: Boolean) {
        // Scroll can be used for phantom slot quantity adjustment
        // Implementation depends on specific requirements
    }

    private fun writeFullSync(buffer: PacketBuffer) {
        buffer.writeVarInt(resourceList.size)
        for (resource in resourceList) {
            if (resource == null) {
                buffer.writeBoolean(false)
            } else {
                buffer.writeBoolean(true)
                val nbt = NBTTagCompound()
                keyWriter(resource, nbt)
                buffer.writeCompoundTag(nbt)
            }
        }
    }

    private fun readFullSync(buffer: PacketBuffer) {
        resourceList.clear()
        val count = buffer.readVarInt()
        for (i in 0 until count) {
            val present = buffer.readBoolean()
            if (!present) {
                resourceList.add(null)
                continue
            }
            val nbt = buffer.readCompoundTag()
            if (nbt == null) {
                resourceList.add(null)
                continue
            }
            val key = keyReader(nbt)
            resourceList.add(key)
        }
    }

    private fun updateResourceList() {
        // detectAndSendChanges() is server-side only; client must rely on synced resourceList.
        // If we rebuild from client-side storage here, GUI will show empty/stale data.
        if (getSyncManager().isClient) return

        // Server-side: keep existing list; detectAndSendChanges will update dirty slots.
        ensureListsSized(storage.slotCount)
    }

    private fun updateResourceListFull() {
        if (getSyncManager().isClient) return
        val count = storage.slotCount
        ensureListsSized(count)
        for (i in 0 until count) {
            resourceList[i] = storage.getSlot(i)
        }
    }

    private fun ensureListsSized(count: Int) {
        if (resourceList.size != count) {
            resourceList = MutableList(count) { null }
        }
        if (lastSyncedList.size != count) {
            lastSyncedList = MutableList(count) { null to 0L }
        }
    }

    private fun cacheCurrentState() {
        ensureListsSized(resourceList.size)
        for (i in resourceList.indices) {
            val key = resourceList[i]
            lastSyncedList[i] = key to (key?.count ?: 0L)
        }
    }

}
