package github.kasuminova.prototypemachinery.common.util

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.common.block.entity.BlockEntity
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.common.registry.GameRegistry
import kotlin.reflect.KClass

internal fun <T : BlockEntity> KClass<T>.register(name: String): Unit =
    GameRegistry.registerTileEntity(this.java, ResourceLocation(PrototypeMachinery.MOD_ID, name))
