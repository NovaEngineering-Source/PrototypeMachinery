package github.kasuminova.prototypemachinery.common.util

import github.kasuminova.prototypemachinery.Tags
import github.kasuminova.prototypemachinery.common.block.entity.BlockEntity
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.common.registry.GameRegistry
import kotlin.reflect.KClass

public fun <T : BlockEntity> KClass<T>.register(name: String): Unit =
    GameRegistry.registerTileEntity(this.java, ResourceLocation(Tags.MOD_ID, name))
