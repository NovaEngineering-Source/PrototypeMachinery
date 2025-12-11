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

@Suppress("OVERRIDE_DEPRECATION")
public open class MachineBlock(
    public val machineType: MachineType,
    material: Material = Material.IRON
) : BlockContainer(material) {

    public companion object {
        public val FACING: PropertyEnum<EnumFacing> = PropertyEnum.create("facing", EnumFacing::class.java)
        public val TWIST: PropertyEnum<EnumFacing> = PropertyEnum.create("twist", EnumFacing::class.java) { it!!.axis != EnumFacing.Axis.Y }
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
            .withProperty(TWIST, EnumFacing.NORTH)
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

    override fun createBlockState(): BlockStateContainer = BlockStateContainer(this, FACING, TWIST, FORMED)

    override fun getStateFromMeta(meta: Int): IBlockState {
        return defaultState.withProperty(FACING, EnumFacing.byIndex(meta and 7))
    }

    override fun getMetaFromState(state: IBlockState): Int {
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
        // Default twist logic: if front is horizontal, twist is usually not used (default NORTH).
        // If front is UP/DOWN, twist depends on player's horizontal facing.
        // For now, we just set default state, actual twist setting can be done via wrench or advanced placement logic.
        return this.defaultState.withProperty(FACING, front)
    }

    private fun getFacingFromEntity(pos: BlockPos, placer: EntityLivingBase): EnumFacing {
        if (abs(placer.posX - (pos.x + 0.5)) < 2.0 && abs(placer.posZ - (pos.z + 0.5)) < 2.0) {
            val d0 = placer.posY + placer.eyeHeight.toDouble()
            if (d0 - pos.y.toDouble() > 2.0) return EnumFacing.DOWN
            if (pos.y.toDouble() - d0 > 0.0) return EnumFacing.UP
        }
        return placer.horizontalFacing.opposite
    }

    override fun onBlockPlacedBy(worldIn: World, pos: BlockPos, state: IBlockState, placer: EntityLivingBase, stack: net.minecraft.item.ItemStack) {
        super.onBlockPlacedBy(worldIn, pos, state, placer, stack)
        val tile = worldIn.getTileEntity(pos) as? MachineBlockEntity ?: return

        // Calculate twist for UP/DOWN facing
        val facing = state.getValue(FACING)
        if (facing == EnumFacing.UP || facing == EnumFacing.DOWN) {
            // When facing UP/DOWN, the "top" of the machine (twist) aligns with player's facing
            tile.setTwist(placer.horizontalFacing.opposite)
        } else {
            // For horizontal facing, twist is usually UP (which we map to NORTH in our property logic for simplicity, or handle explicitly)
            // Since our TWIST property only accepts horizontal values (axis != Y), we use NORTH as default "Up" equivalent or just store player facing.
            // Let's store the player's horizontal facing as twist, which can be used to rotate the texture if needed.
            tile.setTwist(EnumFacing.NORTH)
        }
    }

    override fun getActualState(state: IBlockState, worldIn: IBlockAccess, pos: BlockPos): IBlockState {
        val tileEntity = worldIn.getTileEntity(pos)
        var formed = false
        var twist = EnumFacing.NORTH

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