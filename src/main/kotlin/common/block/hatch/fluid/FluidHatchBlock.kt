package github.kasuminova.prototypemachinery.common.block.hatch.fluid

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.common.registry.HatchConfigRegistry
import net.minecraft.block.Block
import net.minecraft.block.material.Material
import net.minecraft.block.state.BlockStateContainer
import net.minecraft.block.state.IBlockState
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * # FluidHatchBlock - Fluid Hatch Block
 * # FluidHatchBlock - 流体仓方块
 *
 * Block implementation for fluid input/output hatches.
 *
 * 流体输入/输出仓的方块实现。
 *
 * @param config The configuration for this fluid hatch
 */
public class FluidHatchBlock(
    public val config: FluidHatchConfig
) : Block(Material.IRON) {

    init {
        val typeName = config.hatchType.name.lowercase()
        val tierLevel = config.tier.tier
        translationKey = "${PrototypeMachinery.MOD_ID}.fluid_${typeName}_hatch_$tierLevel"
        setRegistryName(PrototypeMachinery.MOD_ID, "fluid_${typeName}_hatch_$tierLevel")
        setHardness(3.5f)
        setResistance(10.0f)
        defaultState = blockState.baseState
    }

    override fun createBlockState(): BlockStateContainer {
        return BlockStateContainer(this)
    }

    @Deprecated("Deprecated in Java")
    override fun getStateFromMeta(meta: Int): IBlockState = defaultState

    override fun getMetaFromState(state: IBlockState): Int = 0

    override fun hasTileEntity(state: IBlockState): Boolean = true

    override fun createTileEntity(world: World, state: IBlockState): TileEntity {
        val currentConfig = HatchConfigRegistry.getFluidHatchConfig(config.tier.tier, config.hatchType)
        return FluidHatchBlockEntity(currentConfig)
    }

    @Deprecated("Deprecated in Java")
    override fun onBlockActivated(
        worldIn: World,
        pos: BlockPos,
        state: IBlockState,
        playerIn: EntityPlayer,
        hand: EnumHand,
        facing: EnumFacing,
        hitX: Float,
        hitY: Float,
        hitZ: Float
    ): Boolean {
        if (worldIn.isRemote) {
            return true
        }
        val te = worldIn.getTileEntity(pos)
        if (te is FluidHatchBlockEntity) {
            // Open GUI using ModularUI
            com.cleanroommc.modularui.factory.TileEntityGuiFactory.INSTANCE.open(playerIn, pos)
        }
        return true
    }

    override fun breakBlock(worldIn: World, pos: BlockPos, state: IBlockState) {
        // Drop fluids or handle cleanup if needed
        super.breakBlock(worldIn, pos, state)
    }

}
