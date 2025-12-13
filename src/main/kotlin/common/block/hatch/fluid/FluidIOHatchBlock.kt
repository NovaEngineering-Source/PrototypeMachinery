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
 * # FluidIOHatchBlock - Fluid IO Hatch Block
 * # FluidIOHatchBlock - 流体交互仓方块
 *
 * Block implementation for fluid IO hatches with separate input/output tanks.
 *
 * 具有独立输入/输出储罐的流体交互仓方块实现。
 *
 * @param config The configuration for this fluid IO hatch
 */
public class FluidIOHatchBlock(
    public val config: FluidIOHatchConfig
) : Block(Material.IRON) {

    init {
        val tierLevel = config.tier.tier
        translationKey = "${PrototypeMachinery.MOD_ID}.fluid_io_hatch_$tierLevel"
        setRegistryName(PrototypeMachinery.MOD_ID, "fluid_io_hatch_$tierLevel")
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
        val currentConfig = HatchConfigRegistry.getFluidIOHatchConfig(config.tier.tier)
        return FluidIOHatchBlockEntity(currentConfig)
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
        if (te is FluidIOHatchBlockEntity) {
            com.cleanroommc.modularui.factory.TileEntityGuiFactory.INSTANCE.open(playerIn, pos)
        }
        return true
    }

    override fun breakBlock(worldIn: World, pos: BlockPos, state: IBlockState) {
        super.breakBlock(worldIn, pos, state)
    }

}
