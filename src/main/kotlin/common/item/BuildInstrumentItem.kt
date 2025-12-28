package github.kasuminova.prototypemachinery.common.item

import com.cleanroommc.modularui.api.IGuiHolder
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData
import com.cleanroommc.modularui.factory.PlayerInventoryGuiFactory
import com.cleanroommc.modularui.screen.ModularPanel
import com.cleanroommc.modularui.screen.UISettings
import com.cleanroommc.modularui.value.sync.PanelSyncManager
import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.common.block.MachineBlock
import github.kasuminova.prototypemachinery.common.block.entity.MachineBlockEntity
import github.kasuminova.prototypemachinery.common.buildinstrument.BuildInstrumentNbt
import github.kasuminova.prototypemachinery.common.buildinstrument.BuildInstrumentUi
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.Item
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.ActionResult
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * Machine builder (placeholder).
 *
 * 机械构建器（占位实现）：目前仅提供物品注册与模型/贴图。
 */
internal class BuildInstrumentItem : Item(), IGuiHolder<PlayerInventoryGuiData> {

    init {
        registryName = ResourceLocation(PrototypeMachinery.MOD_ID, "build_instrument")
        translationKey = "${PrototypeMachinery.MOD_ID}.build_instrument"
        maxStackSize = 1

        setCreativeTab(github.kasuminova.prototypemachinery.common.registry.PMCreativeTabs.MAIN)
    }

    override fun buildUI(data: PlayerInventoryGuiData, syncManager: PanelSyncManager, settings: UISettings): ModularPanel {
        // Keep this server-safe: Panel is constructed on BOTH sides (server collects sync values).
        return BuildInstrumentUi.build(data, syncManager, settings)
    }

    override fun onItemRightClick(worldIn: World, playerIn: EntityPlayer, handIn: EnumHand): ActionResult<net.minecraft.item.ItemStack> {
        val stack = playerIn.getHeldItem(handIn)
        if (!worldIn.isRemote) {
            PlayerInventoryGuiFactory.INSTANCE.openFromHand(playerIn, handIn)
        }
        return ActionResult(EnumActionResult.SUCCESS, stack)
    }

    override fun onItemUse(
        player: EntityPlayer,
        worldIn: World,
        pos: BlockPos,
        hand: EnumHand,
        facing: EnumFacing,
        hitX: Float,
        hitY: Float,
        hitZ: Float
    ): EnumActionResult {
        val stack = player.getHeldItem(hand)
        if (stack.isEmpty) return EnumActionResult.PASS

        val state = worldIn.getBlockState(pos)
        val block = state.block
        if (block !is MachineBlock) {
            return EnumActionResult.PASS
        }

        val te = worldIn.getTileEntity(pos)
        if (te !is MachineBlockEntity) {
            return EnumActionResult.PASS
        }

        if (!worldIn.isRemote) {
            val tag = stack.tagCompound ?: NBTTagCompound().also { stack.tagCompound = it }
            BuildInstrumentNbt.writeBoundController(tag, worldIn.provider.dimension, pos, te.machine.type.id.toString(), te.machine.type.structure.id)
            player.sendStatusMessage(net.minecraft.util.text.TextComponentString("[BuildInstrument] 已绑定控制器: ${te.machine.type.id} @ $pos"), true)
        }

        return EnumActionResult.SUCCESS
    }
}
