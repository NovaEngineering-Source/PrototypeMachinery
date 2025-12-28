package github.kasuminova.prototypemachinery.common.buildinstrument

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.PrototypeMachineryAPI
import github.kasuminova.prototypemachinery.api.machine.structure.StructureOrientation
import github.kasuminova.prototypemachinery.api.machine.structure.preview.AnyOfRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.ExactBlockStateRequirement
import github.kasuminova.prototypemachinery.common.block.MachineBlock
import github.kasuminova.prototypemachinery.common.block.entity.MachineBlockEntity
import github.kasuminova.prototypemachinery.impl.machine.structure.preview.StructurePreviewBuilder
import net.minecraft.block.Block
import net.minecraft.block.BlockFalling
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.init.Blocks
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.math.BlockPos
import net.minecraft.util.text.TextComponentString
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fluids.FluidRegistry
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.CapabilityFluidHandler
import net.minecraftforge.fluids.capability.IFluidHandlerItem
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.items.CapabilityItemHandler
import net.minecraftforge.items.IItemHandler
import net.minecraftforge.items.IItemHandlerModifiable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Server-side executor for Build Instrument tasks.
 *
 * Notes:
 * - The UI is synced via ItemStack NBT in the player's inventory slot.
 * - We keep tasks per-player in memory; restarting the server cancels them.
 */
internal object BuildInstrumentTaskManager {

    private const val OPS_PER_TICK: Int = 8

    private data class Task(
        val slotIndex: Int,
        val dim: Int,
        val controllerPos: BlockPos,
        val mode: Mode,
        val targets: List<Target>,
        var index: Int = 0,
        var done: Int = 0,
        val completed: BooleanArray = BooleanArray(targets.size),
        var lastMissingNotifyWorldTime: Long = 0L,
        val missingTally: MutableMap<String, Int> = linkedMapOf()
    ) {
        val total: Int get() = targets.size
        fun finished(): Boolean = index >= targets.size
    }

    private data class Target(
        val pos: BlockPos,
        val desired: net.minecraft.block.state.IBlockState? = null
    )

    private enum class Mode { BUILD, DISASSEMBLE }

    private val tasks: MutableMap<UUID, Task> = ConcurrentHashMap()

    private fun forceSyncSlot(player: EntityPlayerMP, slotIndex: Int) {
        // NBT-only changes on an ItemStack in player inventory are not guaranteed to auto-sync every tick.
        // Force container sync so the client UI (which reads from inventory NBT) updates reliably.
        player.inventory.markDirty()
        player.inventoryContainer.detectAndSendChanges()
    }

    fun registerToEventBus() {
        MinecraftForge.EVENT_BUS.register(this)
    }

    fun handleTaskButtonPressed(player: EntityPlayer, slotIndex: Int) {
        if (player.world.isRemote) return
        val p = player as? EntityPlayerMP ?: return

        val stack = p.inventory.getStackInSlot(slotIndex)
        if (stack.isEmpty) return
        val tag = stack.tagCompound
        if (!BuildInstrumentNbt.isBound(tag)) {
            p.sendStatusMessage(TextComponentString("[BuildInstrument] 未绑定控制器：请右键控制器进行绑定"), true)
            return
        }

        val state = BuildInstrumentNbt.readTaskState(tag)

        // Toggle behavior based on current state.
        when (state) {
            BuildInstrumentNbt.TaskState.IDLE -> {
                startTask(p, slotIndex, stack)
            }

            BuildInstrumentNbt.TaskState.BUILDING -> {
                BuildInstrumentNbt.writeTaskState(stack.tagCompound!!, BuildInstrumentNbt.TaskState.PAUSED_BUILDING)
                p.sendStatusMessage(TextComponentString("[BuildInstrument] 已暂停"), true)
                forceSyncSlot(p, slotIndex)
            }

            BuildInstrumentNbt.TaskState.PAUSED_BUILDING -> {
                BuildInstrumentNbt.writeTaskState(stack.tagCompound!!, BuildInstrumentNbt.TaskState.BUILDING)
                p.sendStatusMessage(TextComponentString("[BuildInstrument] 继续构建"), true)
                forceSyncSlot(p, slotIndex)
            }

            BuildInstrumentNbt.TaskState.DISASSEMBLING -> {
                BuildInstrumentNbt.writeTaskState(stack.tagCompound!!, BuildInstrumentNbt.TaskState.PAUSED_DISASSEMBLING)
                p.sendStatusMessage(TextComponentString("[BuildInstrument] 已暂停"), true)
                forceSyncSlot(p, slotIndex)
            }

            BuildInstrumentNbt.TaskState.PAUSED_DISASSEMBLING -> {
                BuildInstrumentNbt.writeTaskState(stack.tagCompound!!, BuildInstrumentNbt.TaskState.DISASSEMBLING)
                p.sendStatusMessage(TextComponentString("[BuildInstrument] 继续拆卸"), true)
                forceSyncSlot(p, slotIndex)
            }
        }
    }

    @SubscribeEvent
    fun onPlayerTick(event: TickEvent.PlayerTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        val player = event.player
        if (player.world.isRemote) return

        val task = tasks[player.uniqueID] ?: return

        // Validate the slot still contains the same item.
        val stack = player.inventory.getStackInSlot(task.slotIndex)
        if (stack.isEmpty) {
            tasks.remove(player.uniqueID)
            return
        }

        val root = stack.tagCompound ?: return
        val state = BuildInstrumentNbt.readTaskState(root)
        val paused = state == BuildInstrumentNbt.TaskState.PAUSED_BUILDING || state == BuildInstrumentNbt.TaskState.PAUSED_DISASSEMBLING
        if (paused) {
            // Still update progress numbers.
            BuildInstrumentNbt.writeTaskProgress(root, task.done, task.total)
            (player as? EntityPlayerMP)?.let { forceSyncSlot(it, task.slotIndex) }
            return
        }

        val world = player.world
        if (world.provider.dimension != task.dim) {
            cancelTask(player, root, task.slotIndex, "维度变化")
            return
        }

        var ops = 0
        while (ops < OPS_PER_TICK && !task.finished()) {
            val idx = task.index
            if (idx < 0 || idx >= task.targets.size) break
            if (task.completed[idx]) {
                task.index++
                continue
            }

            val target = task.targets[idx]
            val targetPos = target.pos
            if (!world.isBlockLoaded(targetPos)) {
                cancelTask(player, root, task.slotIndex, "区块未加载")
                return
            }

            when (task.mode) {
                Mode.BUILD -> {
                    val desired = target.desired
                    if (desired == null || desired.block === Blocks.AIR) {
                        task.completed[idx] = true
                        task.index++
                        ops++
                        continue
                    }

                    // If player already placed it manually (or it was placed by earlier pass), count it as done.
                    val cur0 = world.getBlockState(targetPos)
                    val curMeta0 = try {
                        @Suppress("DEPRECATION")
                        cur0.block.getMetaFromState(cur0)
                    } catch (_: Throwable) {
                        0
                    }
                    val desiredMeta0 = try {
                        @Suppress("DEPRECATION")
                        desired.block.getMetaFromState(desired)
                    } catch (_: Throwable) {
                        0
                    }
                    if (cur0.block === desired.block && curMeta0 == desiredMeta0) {
                        task.completed[idx] = true
                        task.done++
                        task.index++
                        ops++
                        continue
                    }

                    // If there is an obstacle, try to remove it first (diamond-tool baseline).
                    if (targetPos != task.controllerPos) {
                        val cur = world.getBlockState(targetPos)
                        if (cur.block !== Blocks.AIR && cur != desired) {
                            if (canBreakWithDiamond(world, targetPos, cur)) {
                                world.destroyBlock(targetPos, true)
                            } else {
                                // Can't break obstacle -> treat as blocked, keep pending.
                                val k = "<无法破坏:${cur.block.registryName}>"
                                task.missingTally[k] = (task.missingTally[k] ?: 0) + 1
                                task.index++
                                ops++
                                continue
                            }
                        }
                    }

                    // Consume required materials unless creative.
                    if (!player.capabilities.isCreativeMode) {
                        val okConsume = tryConsumeForState(player as EntityPlayerMP, desired)
                        if (!okConsume) {
                            val missKey = missingKeyForState(desired)
                            task.missingTally[missKey] = (task.missingTally[missKey] ?: 0) + 1
                            task.index++
                            ops++
                            continue
                        }
                    }

                    val ok = world.setBlockState(targetPos, desired, 3)
                    if (ok) {
                        task.completed[idx] = true
                        task.done++
                        ops++
                    } else {
                        // Placement failed; do not mark done. (We do not attempt to refund here.)
                    }
                    task.index++
                }

                Mode.DISASSEMBLE -> {
                    // Avoid destroying the controller itself.
                    if (targetPos == task.controllerPos) {
                        task.index++
                        continue
                    }
                    val st = world.getBlockState(targetPos)
                    if (st.block !== Blocks.AIR) {
                        world.destroyBlock(targetPos, true)
                        task.done++
                        ops++
                    }
                    task.index++
                }
            }
        }

        // Sync progress to client via ItemStack NBT.
        BuildInstrumentNbt.writeTaskProgress(root, task.done, task.total)
        (player as? EntityPlayerMP)?.let { forceSyncSlot(it, task.slotIndex) }

        if (task.finished()) {
            if (task.done >= task.total) {
                BuildInstrumentNbt.writeTaskState(root, BuildInstrumentNbt.TaskState.IDLE)
                (player as? EntityPlayerMP)?.let { forceSyncSlot(it, task.slotIndex) }
                tasks.remove(player.uniqueID)
                (player as? EntityPlayerMP)?.sendStatusMessage(TextComponentString("[BuildInstrument] 任务完成"), true)
            } else {
                // Not complete yet (missing/blocked). Loop again from start.
                task.index = 0

                // Throttled missing-material notice.
                val now = world.totalWorldTime
                if (task.missingTally.isNotEmpty() && now - task.lastMissingNotifyWorldTime >= 40L) {
                    task.lastMissingNotifyWorldTime = now
                    val top = task.missingTally.entries
                        .sortedByDescending { it.value }
                        .take(5)
                        .joinToString(", ") { (k, v) -> "$k×$v" }
                    (player as? EntityPlayerMP)?.sendStatusMessage(TextComponentString("[BuildInstrument] 缺少材料：$top"), true)
                    task.missingTally.clear()
                }
            }
        }
    }

    private fun missingKeyForState(desired: net.minecraft.block.state.IBlockState): String {
        val block = desired.block
        val id = block.registryName?.toString() ?: block.javaClass.name
        val meta = try {
            @Suppress("DEPRECATION")
            block.getMetaFromState(desired)
        } catch (_: Throwable) {
            0
        }
        val fluid = FluidRegistry.lookupFluidForBlock(block)
        return if (fluid != null) {
            "${fluid.name}(1000mB)"
        } else {
            "$id@$meta"
        }
    }

    /**
     * Try to consume the resources needed to place one block of [desired].
     * - Non-fluid blocks: consume 1 matching ItemStack.
     * - Fluid blocks: consume 1000mB of the matching fluid from any container in the player's inventory (including nested item handlers).
     */
    private fun tryConsumeForState(player: EntityPlayerMP, desired: net.minecraft.block.state.IBlockState): Boolean {
        val block = desired.block

        // Fluid blocks: try to drain from any fluid container.
        val fluid = FluidRegistry.lookupFluidForBlock(block)
            ?: when (desired.material) {
                net.minecraft.block.material.Material.WATER -> FluidRegistry.WATER
                net.minecraft.block.material.Material.LAVA -> FluidRegistry.LAVA
                else -> null
            }
        if (fluid != null) {
            return consumeFluidFromPlayer(player, fluid.name, 1000)
        }

        val meta = try {
            @Suppress("DEPRECATION")
            block.getMetaFromState(desired)
        } catch (_: Throwable) {
            0
        }
        val req = ItemStack(block, 1, meta)
        if (req.isEmpty) {
            // No item form -> cannot consume, treat as missing.
            return false
        }
        return consumeItemFromPlayer(player, req, 1)
    }

    private fun consumeItemFromPlayer(player: EntityPlayerMP, required: ItemStack, count: Int): Boolean {
        var remaining = count

        fun matches(s: ItemStack): Boolean {
            if (s.isEmpty) return false
            if (s.item != required.item) return false
            return s.metadata == required.metadata
        }

        // 1) Direct inventory stacks.
        for (i in 0 until player.inventory.sizeInventory) {
            val s = player.inventory.getStackInSlot(i)
            if (s.isEmpty) continue
            if (matches(s)) {
                val take = kotlin.math.min(remaining, s.count)
                s.shrink(take)
                remaining -= take
                if (remaining <= 0) return true
                continue
            }

            // 2) Nested item handlers (backpacks, boxes, etc.).
            val handler = s.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null) ?: continue
            remaining -= extractMatchingFromHandler(handler, required, remaining)
            if (remaining <= 0) return true
        }

        return false
    }

    private fun extractMatchingFromHandler(handler: IItemHandler, required: ItemStack, maxAmount: Int): Int {
        if (maxAmount <= 0) return 0

        fun matches(s: ItemStack): Boolean {
            if (s.isEmpty) return false
            if (s.item != required.item) return false
            return s.metadata == required.metadata
        }

        var remaining = maxAmount
        var consumed = 0
        for (slot in 0 until handler.slots) {
            val s = handler.getStackInSlot(slot)
            if (!matches(s)) continue

            // Extract up to remaining.
            val extracted = handler.extractItem(slot, remaining, false)
            if (extracted.isEmpty) continue
            val got = extracted.count
            consumed += got
            remaining -= got
            if (remaining <= 0) break
        }
        return consumed
    }

    private fun consumeFluidFromPlayer(player: EntityPlayerMP, fluidName: String, amountMb: Int): Boolean {
        val fluid = FluidRegistry.getFluid(fluidName) ?: return false
        var remaining = amountMb

        fun drainFromStackInSlot(invSlot: Int, stack: ItemStack): Int {
            if (stack.isEmpty) return 0
            val handler = stack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null) ?: return 0
            val req = FluidStack(fluid, remaining)
            val simulated = handler.drain(req, false) ?: return 0
            if (simulated.amount <= 0) return 0
            if (simulated.fluid != fluid) return 0

            val drained = handler.drain(FluidStack(fluid, kotlin.math.min(remaining, simulated.amount)), true) ?: return 0
            val got = drained.amount
            if (got > 0 && handler is IFluidHandlerItem) {
                player.inventory.setInventorySlotContents(invSlot, handler.container)
            }
            return got
        }

        // 1) Direct inventory.
        for (i in 0 until player.inventory.sizeInventory) {
            val s = player.inventory.getStackInSlot(i)
            if (s.isEmpty) continue

            // Direct drain.
            val got = drainFromStackInSlot(i, s)
            if (got > 0) {
                remaining -= got
                if (remaining <= 0) return true
                continue
            }

            // 2) Nested item handlers: try each contained stack as a fluid container.
            val itemHandler = s.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null) ?: continue
            for (slot in 0 until itemHandler.slots) {
                val inner = itemHandler.getStackInSlot(slot)
                if (inner.isEmpty) continue
                val fh = inner.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null) ?: continue
                val req = FluidStack(fluid, remaining)
                val simulated = fh.drain(req, false) ?: continue
                if (simulated.amount <= 0 || simulated.fluid != fluid) continue

                val drained = fh.drain(FluidStack(fluid, kotlin.math.min(remaining, simulated.amount)), true) ?: continue
                val gotInner = drained.amount
                if (gotInner > 0) {
                    remaining -= gotInner
                    if (fh is IFluidHandlerItem && itemHandler is IItemHandlerModifiable) {
                        // Put the updated container back.
                        itemHandler.setStackInSlot(slot, fh.container)
                    }
                    if (remaining <= 0) return true
                }
            }
        }

        return false
    }

    private fun canBreakWithDiamond(world: net.minecraft.world.World, pos: BlockPos, state: net.minecraft.block.state.IBlockState): Boolean {
        val block = state.block
        if (block === Blocks.BEDROCK) return false
        val hardness = state.getBlockHardness(world, pos)
        if (hardness < 0f) return false

        val tool = try {
            block.getHarvestTool(state)
        } catch (t: Throwable) {
            null
        }

        val level = try {
            block.getHarvestLevel(state)
        } catch (t: Throwable) {
            -1
        }

        // Baseline: diamond tools (harvest level 3). If no tool is required, treat it as breakable.
        return tool == null || level <= 3
    }

    private fun startTask(player: EntityPlayerMP, slotIndex: Int, stack: ItemStack) {
        val root = stack.tagCompound ?: return

        val dim = BuildInstrumentNbt.readBoundDim(root)
        val controllerPos = BuildInstrumentNbt.readBoundPos(root)
        if (player.world.provider.dimension != dim) {
            player.sendStatusMessage(TextComponentString("[BuildInstrument] 绑定的控制器不在当前维度"), true)
            return
        }

        val te = player.world.getTileEntity(controllerPos)
        if (te !is MachineBlockEntity) {
            player.sendStatusMessage(TextComponentString("[BuildInstrument] 绑定的控制器不存在或未加载"), true)
            return
        }

        val state = player.world.getBlockState(controllerPos)
        val facing = try {
            state.getValue(MachineBlock.FACING)
        } catch (t: Throwable) {
            null
        }

        if (facing == null) {
            player.sendStatusMessage(TextComponentString("[BuildInstrument] 无法读取控制器朝向"), true)
            return
        }

        val orientation: StructureOrientation = te.getOrientation(facing)
        val structureId = te.machine.type.structure.id

        val structure = PrototypeMachineryAPI.structureRegistry.get(structureId, orientation, facing)
        if (structure == null) {
            player.sendStatusMessage(TextComponentString("[BuildInstrument] 未找到结构: $structureId"), true)
            return
        }

        val model = StructurePreviewBuilder.build(structure)

        val formed = te.machine.isFormed()
        val mode = if (formed) Mode.DISASSEMBLE else Mode.BUILD

        val targets: List<Target> = when (mode) {
            Mode.BUILD -> buildTargetsForBuild(player.world, controllerPos, model.blocks, root)
            Mode.DISASSEMBLE -> buildTargetsForDisassemble(player, controllerPos, model.blocks.keys)
        }

        val taskState = when (mode) {
            Mode.BUILD -> BuildInstrumentNbt.TaskState.BUILDING
            Mode.DISASSEMBLE -> BuildInstrumentNbt.TaskState.DISASSEMBLING
        }

        BuildInstrumentNbt.writeTaskState(root, taskState)
        BuildInstrumentNbt.writeTaskProgress(root, 0, targets.size)

        forceSyncSlot(player, slotIndex)

        tasks[player.uniqueID] = Task(
            slotIndex = slotIndex,
            dim = dim,
            controllerPos = controllerPos,
            mode = mode,
            targets = targets
        )

        player.sendStatusMessage(
            TextComponentString(
                if (mode == Mode.BUILD) "[BuildInstrument] 开始构建 (共 ${targets.size} 个方块)" else "[BuildInstrument] 开始拆卸 (共 ${targets.size} 个方块)"
            ),
            true
        )
    }

    private fun buildTargetsForBuild(
        world: net.minecraft.world.World,
        origin: BlockPos,
        blocks: Map<BlockPos, github.kasuminova.prototypemachinery.api.machine.structure.preview.BlockRequirement>,
        rootTag: NBTTagCompound
    ): List<Target> {
        val normal = ArrayList<Target>()
        val late = ArrayList<Target>() // fluids + gravity

        for ((rel, req) in blocks) {
            if (rel == BlockPos.ORIGIN) continue

            val ex: ExactBlockStateRequirement = when (req) {
                is ExactBlockStateRequirement -> req
                is AnyOfRequirement -> {
                    val chosenKey = BuildInstrumentNbt.readMaterialSelection(rootTag, req.stableKey())
                    req.options.firstOrNull { it.stableKey() == chosenKey } ?: req.options.first()
                }
                else -> continue
            }

            val block = Block.REGISTRY.getObject(ex.blockId)
            if (block === Blocks.AIR) continue

            @Suppress("DEPRECATION")
            val desired = block.getStateFromMeta(ex.meta)

            val worldPos = origin.add(rel)

            // Skip already-correct blocks.
            val current = world.getBlockState(worldPos)
            val curId = current.block.registryName
            val curMeta = try {
                current.block.getMetaFromState(current)
            } catch (t: Throwable) {
                0
            }
            if (curId == ex.blockId && curMeta == ex.meta) {
                continue
            }

            val isLate = desired.material.isLiquid || block is BlockFalling
            val entry = Target(pos = worldPos, desired = desired)
            if (isLate) late.add(entry) else normal.add(entry)
        }

        normal.sortWith(compareBy<Target> { it.pos.y }.thenBy { it.pos.x }.thenBy { it.pos.z })
        late.sortWith(compareBy<Target> { it.pos.y }.thenBy { it.pos.x }.thenBy { it.pos.z })

        return normal + late
    }

    private fun buildTargetsForDisassemble(
        player: EntityPlayerMP,
        origin: BlockPos,
        relPositions: Collection<BlockPos>
    ): List<Target> {
        val world = player.world
        val targets = ArrayList<Target>(relPositions.size)

        for (rel in relPositions) {
            if (rel == BlockPos.ORIGIN) continue
            val worldPos = origin.add(rel)
            if (!world.isBlockLoaded(worldPos)) continue
            if (worldPos == origin) continue

            val st = world.getBlockState(worldPos)
            if (st.block !== Blocks.AIR) {
                targets.add(Target(pos = worldPos, desired = null))
            }
        }

        // Disassemble from top to bottom.
        targets.sortWith(compareByDescending<Target> { it.pos.y }.thenBy { it.pos.x }.thenBy { it.pos.z })
        return targets
    }

    private fun cancelTask(player: EntityPlayer, root: NBTTagCompound, slotIndex: Int, reason: String) {
        BuildInstrumentNbt.writeTaskState(root, BuildInstrumentNbt.TaskState.IDLE)
        BuildInstrumentNbt.writeTaskProgress(root, 0, 0)
        tasks.remove(player.uniqueID)
        (player as? EntityPlayerMP)?.sendStatusMessage(TextComponentString("[BuildInstrument] 任务已取消：$reason"), true)
        (player as? EntityPlayerMP)?.let { forceSyncSlot(it, slotIndex) }
        PrototypeMachinery.logger.warn("BuildInstrument task cancelled for {}: {}", player.name, reason)
    }
}
