package github.kasuminova.prototypemachinery.common.block

import github.kasuminova.prototypemachinery.api.machine.MachineType
import github.kasuminova.prototypemachinery.common.block.entity.BlockEntity
import github.kasuminova.prototypemachinery.common.block.entity.MachineBlockEntity
import github.kasuminova.prototypemachinery.common.gui.PrototypeMachineryGUIs
import net.minecraft.block.BlockContainer
import net.minecraft.block.SoundType
import net.minecraft.block.material.Material
import net.minecraft.block.properties.PropertyBool
import net.minecraft.block.properties.PropertyEnum
import net.minecraft.block.state.BlockStateContainer
import net.minecraft.block.state.IBlockState
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.BlockRenderLayer
import net.minecraft.util.EnumBlockRenderType
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.ResourceLocation
import net.minecraft.util.Rotation
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import javax.annotation.Nonnull

@Suppress("OVERRIDE_DEPRECATION")
public open class MachineBlock(
    public val machineType: MachineType,
    material: Material = Material.IRON
) : BlockContainer(material) {

    public companion object {
        public val FACING: PropertyEnum<EnumFacing> = PropertyEnum.create("facing", EnumFacing::class.java, *EnumFacing.HORIZONTALS)
        public val FORMED: PropertyBool = PropertyBool.create("formed")
    }

    init {
        setHardness(10f)
        setResistance(20f)
        setHarvestLevel("pickaxe", 1)

        setSoundType(SoundType.METAL)
//        setCreativeTab()

        defaultState = this.blockState.baseState
            .withProperty(FACING, EnumFacing.NORTH)
            .withProperty(FORMED, false)

        registryName = ResourceLocation(
            machineType.id.namespace,
            machineType.id.path + "_controller"
        )

        translationKey = "prototypemachinery.machine.${machineType.id.path}_controller"
    }

    // region BlockStates

    @Nonnull
    override fun withRotation(state: IBlockState, rot: Rotation): IBlockState = state.withProperty(FACING, rot.rotate(state.getValue(FACING)))

    override fun createBlockState(): BlockStateContainer = BlockStateContainer(this, FACING, FORMED)

    override fun getStateFromMeta(meta: Int): IBlockState {
        val facing = EnumFacing.HORIZONTALS[meta and 3]
        return defaultState.withProperty(FACING, facing)
    }

    override fun getMetaFromState(state: IBlockState): Int {
        val facing = state.getValue(FACING)
        return facing.horizontalIndex
    }

    override fun getActualState(state: IBlockState, worldIn: IBlockAccess, pos: BlockPos): IBlockState {
        val tileEntity = worldIn.getTileEntity(pos)
        val formed = if (tileEntity is MachineBlockEntity) {
            tileEntity.machine.isFormed()
        } else {
            false
        }
        return state.withProperty(FORMED, formed)
    }

    // endregion

    // region GUI

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
            return false
        }
        if (worldIn.getTileEntity(pos) !is MachineBlockEntity) {
            return true
        }
        PrototypeMachineryGUIs.CONTROLLER.open(playerIn, worldIn, pos)
        return false
    }

    // endregion

    // region Client

    @SideOnly(Side.CLIENT)
    override fun getRenderLayer(): BlockRenderLayer = BlockRenderLayer.CUTOUT

    override fun getRenderType(@Nonnull state: IBlockState): EnumBlockRenderType = EnumBlockRenderType.MODEL

    // endregion

    // region Vanilla Logics

    override fun isOpaqueCube(@Nonnull state: IBlockState): Boolean = false

    // endregion

    // region BlockEntity

    override fun createTileEntity(world: World, state: IBlockState): BlockEntity = MachineBlockEntity(machineType)

    override fun createNewTileEntity(worldIn: World, meta: Int): BlockEntity = MachineBlockEntity(machineType)

    override fun hasTileEntity(): Boolean = true

    override fun hasTileEntity(state: IBlockState): Boolean = true

    // endregion

}