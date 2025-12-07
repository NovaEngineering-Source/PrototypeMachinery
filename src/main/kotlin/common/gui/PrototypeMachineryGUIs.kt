package github.kasuminova.prototypemachinery.common.gui

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.common.block.entity.BlockEntity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.Container
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

public enum class PrototypeMachineryGUIs(
    public val serverContainer: (BlockEntity?) -> Container?,
    public val clientGui: (Container?) -> Any?,
) {

    CONTROLLER(TODO(), TODO());

    public fun open(player: EntityPlayer, world: World, pos: BlockPos) {
        player.openGui(PrototypeMachinery, this.ordinal, world, pos.x, pos.y, pos.z)
    }

}