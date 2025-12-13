package github.kasuminova.prototypemachinery.common.block.hatch.item

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.common.gui.PrototypeMachineryGUIs
import github.kasuminova.prototypemachinery.common.registry.HatchConfigRegistry
import net.minecraft.block.Block
import net.minecraft.block.SoundType
import net.minecraft.block.material.Material
import net.minecraft.block.state.BlockStateContainer
import net.minecraft.block.state.IBlockState
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * # ItemIOHatchBlock - Item IO Hatch Block
 * # ItemIOHatchBlock - 物品交互仓方块
 *
 * A block that provides separate input and output item storage for multiblock machines.
 *
 * 为多方块机器提供独立输入和输出物品存储的方块。
 *
 * @param config The configuration for this IO hatch
 */
public class ItemIOHatchBlock(
    public val config: ItemIOHatchConfig
) : Block(Material.IRON) {

    public companion object {
        /**
         * Creates all item IO hatch blocks.
         * 创建所有物品交互仓方块。
         */
        public fun createAll(): List<ItemIOHatchBlock> {
            return ItemIOHatchConfig.CONFIGS.map { ItemIOHatchBlock(it) }
        }
    }

    init {
        val tierLevel = config.tier.tier

        registryName = ResourceLocation(PrototypeMachinery.MOD_ID, "item_io_hatch_$tierLevel")
        translationKey = "${PrototypeMachinery.MOD_ID}.item_io_hatch_$tierLevel"

        setHardness(5.0f)
        setResistance(10.0f)
        setHarvestLevel("pickaxe", 1)
        soundType = SoundType.METAL

        defaultState = blockState.baseState
    }

    override fun createBlockState(): BlockStateContainer = BlockStateContainer(this)

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getStateFromMeta(meta: Int): IBlockState = defaultState

    override fun getMetaFromState(state: IBlockState): Int = 0

    override fun hasTileEntity(state: IBlockState): Boolean = true

    override fun createTileEntity(world: World, state: IBlockState): TileEntity {
        val currentConfig = HatchConfigRegistry.getItemIOHatchConfig(config.tier.tier)
        return ItemIOHatchBlockEntity(currentConfig)
    }

    @Suppress("OVERRIDE_DEPRECATION")
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
        if (worldIn.isRemote) return true

        val tileEntity = worldIn.getTileEntity(pos) as? ItemIOHatchBlockEntity ?: return false
        PrototypeMachineryGUIs.ITEM_HATCH.open(playerIn, pos)
        return true
    }

}
