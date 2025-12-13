package github.kasuminova.prototypemachinery.common.block.hatch.item

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.common.block.hatch.HatchType
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
 * # ItemHatchBlock - Item Hatch Block
 * # ItemHatchBlock - 物品仓方块
 *
 * A block that provides item storage for multiblock machines.
 *
 * 为多方块机器提供物品存储的方块。
 *
 * @param config The configuration for this hatch
 */
public class ItemHatchBlock(
    public val config: ItemHatchConfig
) : Block(Material.IRON) {

    public companion object {
        /**
         * Creates all item hatch blocks for a specific type.
         * 为特定类型创建所有物品仓方块。
         */
        public fun createAll(type: HatchType): List<ItemHatchBlock> {
            val configs = when (type) {
                HatchType.INPUT -> ItemHatchConfig.INPUT_CONFIGS
                HatchType.OUTPUT -> ItemHatchConfig.OUTPUT_CONFIGS
                HatchType.IO -> ItemHatchConfig.IO_CONFIGS
            }
            return configs.map { ItemHatchBlock(it) }
        }
    }

    init {
        val typeName = when (config.hatchType) {
            HatchType.INPUT -> "input"
            HatchType.OUTPUT -> "output"
            HatchType.IO -> "io"
        }
        val tierLevel = config.tier.tier

        // Keep naming consistent with other hatches and lang keys:
        //   item_input_hatch_1, item_output_hatch_1, ...
        registryName = ResourceLocation(PrototypeMachinery.MOD_ID, "item_${typeName}_hatch_$tierLevel")
        translationKey = "${PrototypeMachinery.MOD_ID}.item_${typeName}_hatch_$tierLevel"

        setHardness(5.0f)
        setResistance(10.0f)
        setHarvestLevel("pickaxe", 1)
        soundType = SoundType.METAL

        // No facing for hatches.
        defaultState = blockState.baseState
    }

    override fun createBlockState(): BlockStateContainer = BlockStateContainer(this)

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getStateFromMeta(meta: Int): IBlockState = defaultState

    override fun getMetaFromState(state: IBlockState): Int = 0

    override fun hasTileEntity(state: IBlockState): Boolean = true

    override fun createTileEntity(world: World, state: IBlockState): TileEntity {
        val currentConfig = HatchConfigRegistry.getItemHatchConfig(config.tier.tier, config.hatchType)
        return ItemHatchBlockEntity(currentConfig)
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

        val tileEntity = worldIn.getTileEntity(pos) as? ItemHatchBlockEntity ?: return false
        PrototypeMachineryGUIs.ITEM_HATCH.open(playerIn, pos)
        return true
    }

}
