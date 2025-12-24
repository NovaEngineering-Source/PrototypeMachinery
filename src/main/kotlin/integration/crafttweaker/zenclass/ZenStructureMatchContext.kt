package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass

import crafttweaker.annotations.ZenRegister
import crafttweaker.api.minecraft.CraftTweakerMC
import crafttweaker.api.world.IBiome
import crafttweaker.api.world.IBlockPos
import crafttweaker.api.world.IWorld
import github.kasuminova.prototypemachinery.api.machine.structure.match.StructureMatchContext
import net.minecraft.util.math.BlockPos
import org.jetbrains.annotations.ApiStatus
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenGetter

/**
 * ZenScript-friendly wrapper for structure match context.
 * ZenScript 友好的结构匹配上下文包装类。
 */
@ZenClass("mods.prototypemachinery.StructureMatchContext")
@ZenRegister
@ApiStatus.Experimental
public class ZenStructureMatchContext internal constructor(
    private val ctx: StructureMatchContext,
    private val offset: BlockPos
) {

    private val absolutePos: BlockPos
        get() = ctx.machine.blockEntity.pos.add(offset)

    /**
     * Get the world this structure is being validated in.
     * 获取此结构正在被验证的世界。
     */
    @ZenGetter("world")
    public fun getWorld(): IWorld {
        val mcWorld = ctx.machine.blockEntity.world
        return CraftTweakerMC.getIWorld(mcWorld)
    }

    /**
     * Get the biome at the absolute position (machine + offset).
     * 获取绝对位置（机器 + 偏移）的生物群系。
     */
    @ZenGetter("biome")
    public fun getBiome(): IBiome {
        val zenWorld = getWorld()
        val zenPos = getBlockPos()
        return zenWorld.getBiome(zenPos)
    }

    /**
     * Get the absolute block position (machine origin + offset).
     * 获取绝对方块位置（机器原点 + 偏移）。
     */
    @ZenGetter("blockPos")
    public fun getBlockPos(): IBlockPos = CraftTweakerMC.getIBlockPos(absolutePos)

    /**
     * Get the X coordinate at the absolute position.
     * 获取绝对位置的 X 坐标。
     */
    @ZenGetter("x")
    public fun getX(): Int = absolutePos.x

    /**
     * Get the Y coordinate at the absolute position.
     * 获取绝对位置的 Y 坐标。
     */
    @ZenGetter("y")
    public fun getY(): Int = absolutePos.y

    /**
     * Get the Z coordinate at the absolute position.
     * 获取绝对位置的 Z 坐标。
     */
    @ZenGetter("z")
    public fun getZ(): Int = absolutePos.z

    /**
     * Get the relative block position (offset only).
     * 获取相对方块位置（仅偏移）。
     */
    @ZenGetter("relativePos")
    public fun getRelativePos(): IBlockPos = CraftTweakerMC.getIBlockPos(offset)

    /**
     * Get the X offset from structure origin.
     * 获取相对于结构原点的 X 偏移。
     */
    @ZenGetter("offsetX")
    public fun getOffsetX(): Int = offset.x

    /**
     * Get the Y offset from structure origin.
     * 获取相对于结构原点的 Y 偏移。
     */
    @ZenGetter("offsetY")
    public fun getOffsetY(): Int = offset.y

    /**
     * Get the Z offset from structure origin.
     * 获取相对于结构原点的 Z 偏移。
     */
    @ZenGetter("offsetZ")
    public fun getOffsetZ(): Int = offset.z

}
