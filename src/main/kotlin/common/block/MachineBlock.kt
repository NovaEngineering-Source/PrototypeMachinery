package github.kasuminova.prototypemachinery.common.block

import github.kasuminova.prototypemachinery.api.machine.MachineType
import github.kasuminova.prototypemachinery.common.block.entity.BlockEntity
import github.kasuminova.prototypemachinery.common.block.entity.MachineBlockEntity
import github.kasuminova.prototypemachinery.common.gui.PrototypeMachineryGUIs
import github.kasuminova.prototypemachinery.common.registry.PMItems
import net.minecraft.block.BlockContainer
import net.minecraft.block.SoundType
import net.minecraft.block.material.Material
import net.minecraft.block.properties.PropertyBool
import net.minecraft.block.properties.PropertyEnum
import net.minecraft.block.properties.PropertyInteger
import net.minecraft.block.state.BlockStateContainer
import net.minecraft.block.state.IBlockState
import net.minecraft.entity.EntityLivingBase
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
import kotlin.math.abs

/**
 * Machine controller block.
 *
 * ## Blockstate Properties
 * - **FACING**: The direction the controller's front face points (6 values)
 * - **TWIST**: Clockwise rotation steps around FACING (0-3 → 0°/90°/180°/270°)
 * - **FORMED**: Whether the machine structure is formed
 *
 * Together, (FACING, TWIST) uniquely identifies one of 24 cube orientations.
 *
 * ## Meta Persistence
 * Only FACING is persisted in block meta (3 bits). TWIST is stored in TE and
 * provided via [getActualState] for rendering.
 */
@Suppress("OVERRIDE_DEPRECATION")
public open class MachineBlock(
    public val machineType: MachineType,
    material: Material = Material.IRON
) : BlockContainer(material) {

    public companion object {
        /** The direction the controller's front face points. */
        public val FACING: PropertyEnum<EnumFacing> = PropertyEnum.create("facing", EnumFacing::class.java)

        /** Clockwise rotation steps around FACING axis (0-3). */
        public val TWIST: PropertyInteger = PropertyInteger.create("twist", 0, 3)

        /** Whether the machine structure is formed. */
        public val FORMED: PropertyBool = PropertyBool.create("formed")
    }

    init {
        setHardness(10f)
        setResistance(20f)
        setHarvestLevel("pickaxe", 1)

        setSoundType(SoundType.METAL)

        defaultState = this.blockState.baseState
            .withProperty(FACING, EnumFacing.NORTH)
            .withProperty(TWIST, 0)
            .withProperty(FORMED, false)

        registryName = ResourceLocation(
            machineType.id.namespace,
            machineType.id.path + "_controller"
        )

        translationKey = "prototypemachinery.machine.${machineType.id.path}_controller"
    }

    // region BlockStates

    @Nonnull
    override fun withRotation(state: IBlockState, rot: Rotation): IBlockState =
        state.withProperty(FACING, rot.rotate(state.getValue(FACING)))

    override fun createBlockState(): BlockStateContainer =
        BlockStateContainer(this, FACING, TWIST, FORMED)

    override fun getStateFromMeta(meta: Int): IBlockState {
        // Only FACING is stored in meta (bits 0-2)
        return defaultState.withProperty(FACING, EnumFacing.byIndex(meta and 7))
    }

    override fun getMetaFromState(state: IBlockState): Int {
        // Only persist FACING
        return state.getValue(FACING).index
    }

    override fun getStateForPlacement(
        world: World,
        pos: BlockPos,
        facing: EnumFacing,
        hitX: Float,
        hitY: Float,
        hitZ: Float,
        meta: Int,
        placer: EntityLivingBase,
        hand: EnumHand
    ): IBlockState {
        val front = getFacingFromEntity(pos, placer)
        return this.defaultState.withProperty(FACING, front)
    }

    private fun getFacingFromEntity(pos: BlockPos, placer: EntityLivingBase): EnumFacing {
        if (abs(placer.posX - (pos.x + 0.5)) < 2.0 && abs(placer.posZ - (pos.z + 0.5)) < 2.0) {
            val d0 = placer.posY + placer.eyeHeight.toDouble()
            if (d0 - pos.y.toDouble() > 2.0) return EnumFacing.UP
            if (pos.y.toDouble() - d0 > 0.0) return EnumFacing.DOWN
        }
        return placer.horizontalFacing
    }

    override fun onBlockPlacedBy(
        worldIn: World,
        pos: BlockPos,
        state: IBlockState,
        placer: EntityLivingBase,
        stack: net.minecraft.item.ItemStack
    ) {
        super.onBlockPlacedBy(worldIn, pos, state, placer, stack)
        // TE twist defaults to 0, no additional initialization needed.
    }

    override fun getActualState(state: IBlockState, worldIn: IBlockAccess, pos: BlockPos): IBlockState {
        val tileEntity = worldIn.getTileEntity(pos)
        var formed = false
        var twist = 0

        if (tileEntity is MachineBlockEntity) {
            formed = tileEntity.machine.isFormed()
            twist = tileEntity.twist
        }

        return state.withProperty(FORMED, formed).withProperty(TWIST, twist)
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
        // If the player is holding the orientation tool, return false to let
        // Item.onItemUse handle the rotation logic.
        val held = playerIn.getHeldItem(hand)
        if (!held.isEmpty && held.item === PMItems.controllerOrientationTool) {
            return false
        }

        if (worldIn.isRemote) return true
        if (worldIn.getTileEntity(pos) !is MachineBlockEntity) return false
        PrototypeMachineryGUIs.openController(playerIn, worldIn, pos)
        return true
    }

    // endregion

    // region Client

    @SideOnly(Side.CLIENT)
    override fun getRenderLayer(): BlockRenderLayer = BlockRenderLayer.CUTOUT

    override fun getRenderType(@Nonnull state: IBlockState): EnumBlockRenderType = EnumBlockRenderType.MODEL

    // endregion

    // region Vanilla Logics

    override fun isOpaqueCube(@Nonnull state: IBlockState): Boolean = false

    override fun isFullBlock(state: IBlockState): Boolean = false

    override fun isFullCube(state: IBlockState): Boolean = isFullBlock(state)

    @SideOnly(Side.CLIENT)
    override fun isTranslucent(state: IBlockState): Boolean = isFullBlock(state)

    // endregion

    // region BlockEntity

    override fun createTileEntity(world: World, state: IBlockState): BlockEntity = MachineBlockEntity(machineType)

    override fun createNewTileEntity(worldIn: World, meta: Int): BlockEntity = MachineBlockEntity(machineType)

    override fun hasTileEntity(): Boolean = true

    override fun hasTileEntity(state: IBlockState): Boolean = true

    // endregion
}
