package github.kasuminova.prototypemachinery.common.gui

import com.cleanroommc.modularui.factory.GuiFactories
import github.kasuminova.prototypemachinery.common.block.entity.MachineBlockEntity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * Utility object for opening machine GUIs using ModularUI's factory system.
 * 使用 ModularUI 工厂系统打开机器 GUI 的工具对象。
 */
public object PrototypeMachineryGUIs {

    /**
     * Open the machine controller GUI for a player.
     * 为玩家打开机器控制器 GUI。
     *
     * This uses ModularUI's TileEntityGuiFactory which handles all syncing automatically.
     * 这使用 ModularUI 的 TileEntityGuiFactory，它会自动处理所有同步。
     *
     * @param player The player opening the GUI
     * @param world The world containing the machine
     * @param pos The position of the machine block
     */
    public fun openController(player: EntityPlayer, world: World, pos: BlockPos) {
        val tile = world.getTileEntity(pos)
        if (tile is MachineBlockEntity && !world.isRemote) {
            GuiFactories.tileEntity().open(player, pos)
        }
    }

}

