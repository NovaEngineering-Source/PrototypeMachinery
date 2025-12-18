package github.kasuminova.prototypemachinery.integration.jei.util

import github.kasuminova.prototypemachinery.api.machine.MachineType
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.common.registry.ForgeRegistries

/**
 * Shared helper to resolve a MachineType's controller ItemStack.
 */
public object JeiControllerStack {

    public fun get(machineType: MachineType): ItemStack? {
        val rl = ResourceLocation(machineType.id.namespace, machineType.id.path + "_controller")
        val block = ForgeRegistries.BLOCKS.getValue(rl) ?: return null
        if (block.registryName == null) return null
        return ItemStack(block)
    }
}
