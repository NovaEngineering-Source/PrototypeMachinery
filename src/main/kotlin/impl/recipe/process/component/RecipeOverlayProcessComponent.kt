package github.kasuminova.prototypemachinery.impl.recipe.process.component

import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.process.component.RecipeProcessComponent
import github.kasuminova.prototypemachinery.api.recipe.process.component.RecipeProcessComponentType
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementTypes
import github.kasuminova.prototypemachinery.impl.key.fluid.PMFluidKeyType
import github.kasuminova.prototypemachinery.impl.key.item.PMItemKeyType
import github.kasuminova.prototypemachinery.impl.recipe.requirement.EnergyRequirementComponent
import github.kasuminova.prototypemachinery.impl.recipe.requirement.FluidRequirementComponent
import github.kasuminova.prototypemachinery.impl.recipe.requirement.ItemRequirementComponent
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList
import net.minecraftforge.common.util.Constants
import net.minecraftforge.fluids.FluidStack
import org.jetbrains.annotations.ApiStatus

/**
 * Per-process recipe requirement overlay.
 * 每个进程的需求参数覆写（overlay）。
 *
 * 目前只覆盖“参数类”变更（例如 per-tick 消耗/产出、ignore_output_full、输入/输出列表等），
 * 不直接修改 MachineRecipe 本体。
 * TODO 提供注册表形式功能以对接其他类型。
 */
@ApiStatus.Experimental
public class RecipeOverlayProcessComponent(
    override val owner: RecipeProcess,
) : RecipeProcessComponent {

    override val type: RecipeProcessComponentType<*> = RecipeOverlayProcessComponentType

    /** key = "<typeId>|<componentId>"; value = data compound */
    private val overrides: MutableMap<String, NBTTagCompound> = linkedMapOf()

    public fun clear() {
        overrides.clear()
    }

    public fun setIgnoreOutputFull(type: RecipeRequirementType<*>, componentId: String, value: Boolean) {
        val data = getOrCreateData(type, componentId)
        data.setBoolean(KEY_IGNORE_OUTPUT_FULL, value)
    }

    public fun setEnergyPerTick(componentId: String, inputPerTick: Long? = null, outputPerTick: Long? = null) {
        val data = getOrCreateData(RecipeRequirementTypes.ENERGY, componentId)
        if (inputPerTick != null) data.setLong(KEY_ENERGY_INPUT_PER_TICK, inputPerTick)
        if (outputPerTick != null) data.setLong(KEY_ENERGY_OUTPUT_PER_TICK, outputPerTick)
    }

    public fun setItemIO(componentId: String, inputs: List<PMKey<ItemStack>>? = null, outputs: List<PMKey<ItemStack>>? = null) {
        val data = getOrCreateData(RecipeRequirementTypes.ITEM, componentId)
        if (inputs != null) data.setTag(KEY_ITEM_INPUTS, writeItemKeys(inputs))
        if (outputs != null) data.setTag(KEY_ITEM_OUTPUTS, writeItemKeys(outputs))
    }

    public fun setFluidIO(
        componentId: String,
        inputs: List<PMKey<FluidStack>>? = null,
        outputs: List<PMKey<FluidStack>>? = null,
        inputsPerTick: List<PMKey<FluidStack>>? = null,
        outputsPerTick: List<PMKey<FluidStack>>? = null,
    ) {
        val data = getOrCreateData(RecipeRequirementTypes.FLUID, componentId)
        if (inputs != null) data.setTag(KEY_FLUID_INPUTS, writeFluidKeys(inputs))
        if (outputs != null) data.setTag(KEY_FLUID_OUTPUTS, writeFluidKeys(outputs))
        if (inputsPerTick != null) data.setTag(KEY_FLUID_INPUTS_PER_TICK, writeFluidKeys(inputsPerTick))
        if (outputsPerTick != null) data.setTag(KEY_FLUID_OUTPUTS_PER_TICK, writeFluidKeys(outputsPerTick))
    }

    /** Apply overlay to a known component type. Unknown types are returned as-is. */
    public fun applyTo(component: Any): Any {
        return when (component) {
            is EnergyRequirementComponent -> applyToEnergy(component)
            is ItemRequirementComponent -> applyToItem(component)
            is FluidRequirementComponent -> applyToFluid(component)
            else -> component
        }
    }

    private fun applyToEnergy(component: EnergyRequirementComponent): EnergyRequirementComponent {
        val data = overrides[overlayKey(component.type.id.toString(), component.id)] ?: return component
        val props = applyPropertyOverlay(component.properties, data)

        val inputPerTick = if (data.hasKey(KEY_ENERGY_INPUT_PER_TICK, Constants.NBT.TAG_LONG)) data.getLong(KEY_ENERGY_INPUT_PER_TICK) else component.inputPerTick
        val outputPerTick = if (data.hasKey(KEY_ENERGY_OUTPUT_PER_TICK, Constants.NBT.TAG_LONG)) data.getLong(KEY_ENERGY_OUTPUT_PER_TICK) else component.outputPerTick

        return component.copy(
            inputPerTick = inputPerTick,
            outputPerTick = outputPerTick,
            properties = props,
        )
    }

    private fun applyToItem(component: ItemRequirementComponent): ItemRequirementComponent {
        val data = overrides[overlayKey(component.type.id.toString(), component.id)] ?: return component
        val props = applyPropertyOverlay(component.properties, data)

        val inputs = if (data.hasKey(KEY_ITEM_INPUTS, Constants.NBT.TAG_LIST)) readItemKeys(data.getTagList(KEY_ITEM_INPUTS, Constants.NBT.TAG_COMPOUND)) else component.inputs
        val outputs = if (data.hasKey(KEY_ITEM_OUTPUTS, Constants.NBT.TAG_LIST)) readItemKeys(data.getTagList(KEY_ITEM_OUTPUTS, Constants.NBT.TAG_COMPOUND)) else component.outputs

        return component.copy(inputs = inputs, outputs = outputs, properties = props)
    }

    private fun applyToFluid(component: FluidRequirementComponent): FluidRequirementComponent {
        val data = overrides[overlayKey(component.type.id.toString(), component.id)] ?: return component
        val props = applyPropertyOverlay(component.properties, data)

        val inputs = if (data.hasKey(KEY_FLUID_INPUTS, Constants.NBT.TAG_LIST)) readFluidKeys(data.getTagList(KEY_FLUID_INPUTS, Constants.NBT.TAG_COMPOUND)) else component.inputs
        val outputs = if (data.hasKey(KEY_FLUID_OUTPUTS, Constants.NBT.TAG_LIST)) readFluidKeys(data.getTagList(KEY_FLUID_OUTPUTS, Constants.NBT.TAG_COMPOUND)) else component.outputs
        val inputsPerTick = if (data.hasKey(KEY_FLUID_INPUTS_PER_TICK, Constants.NBT.TAG_LIST)) readFluidKeys(data.getTagList(KEY_FLUID_INPUTS_PER_TICK, Constants.NBT.TAG_COMPOUND)) else component.inputsPerTick
        val outputsPerTick = if (data.hasKey(KEY_FLUID_OUTPUTS_PER_TICK, Constants.NBT.TAG_LIST)) readFluidKeys(data.getTagList(KEY_FLUID_OUTPUTS_PER_TICK, Constants.NBT.TAG_COMPOUND)) else component.outputsPerTick

        return component.copy(
            inputs = inputs,
            outputs = outputs,
            inputsPerTick = inputsPerTick,
            outputsPerTick = outputsPerTick,
            properties = props,
        )
    }

    override fun serializeNBT(): NBTTagCompound {
        val nbt = NBTTagCompound()
        val list = NBTTagList()

        overrides.forEach { (k, data) ->
            val idx = k.indexOf('|')
            if (idx <= 0 || idx >= k.length - 1) return@forEach
            val typeId = k.substring(0, idx)
            val componentId = k.substring(idx + 1)

            val entry = NBTTagCompound()
            entry.setString(KEY_ENTRY_TYPE, typeId)
            entry.setString(KEY_ENTRY_ID, componentId)
            entry.setTag(KEY_ENTRY_DATA, data)
            list.appendTag(entry)
        }

        nbt.setTag(KEY_ENTRIES, list)
        return nbt
    }

    override fun deserializeNBT(nbt: NBTTagCompound) {
        overrides.clear()

        if (!nbt.hasKey(KEY_ENTRIES, Constants.NBT.TAG_LIST)) return
        val list = nbt.getTagList(KEY_ENTRIES, Constants.NBT.TAG_COMPOUND)
        for (i in 0 until list.tagCount()) {
            val entry = list.getCompoundTagAt(i)
            if (!entry.hasKey(KEY_ENTRY_TYPE, Constants.NBT.TAG_STRING)) continue
            if (!entry.hasKey(KEY_ENTRY_ID, Constants.NBT.TAG_STRING)) continue
            if (!entry.hasKey(KEY_ENTRY_DATA, Constants.NBT.TAG_COMPOUND)) continue

            val typeId = entry.getString(KEY_ENTRY_TYPE)
            val componentId = entry.getString(KEY_ENTRY_ID)
            val data = entry.getCompoundTag(KEY_ENTRY_DATA)
            overrides[overlayKey(typeId, componentId)] = data
        }
    }

    private fun overlayKey(typeId: String, componentId: String): String = "$typeId|$componentId"

    private fun getOrCreateData(type: RecipeRequirementType<*>, componentId: String): NBTTagCompound {
        val key = overlayKey(type.id.toString(), componentId)
        return overrides.getOrPut(key) { NBTTagCompound() }
    }

    private fun applyPropertyOverlay(original: Map<String, Any>, data: NBTTagCompound): Map<String, Any> {
        if (!data.hasKey(KEY_IGNORE_OUTPUT_FULL, Constants.NBT.TAG_BYTE)) return original
        val out = LinkedHashMap<String, Any>(original)
        out["ignore_output_full"] = data.getBoolean(KEY_IGNORE_OUTPUT_FULL)
        return out
    }

    private fun writeItemKeys(keys: List<PMKey<ItemStack>>): NBTTagList {
        val list = NBTTagList()
        keys.forEach { k ->
            val tag = NBTTagCompound()
            k.writeNBT(tag)
            list.appendTag(tag)
        }
        return list
    }

    private fun readItemKeys(list: NBTTagList): List<PMKey<ItemStack>> {
        val out = ArrayList<PMKey<ItemStack>>(list.tagCount())
        for (i in 0 until list.tagCount()) {
            val tag = list.getCompoundTagAt(i)
            val key = PMItemKeyType.readNBT(tag) as? PMKey<ItemStack> ?: continue
            if (key.count <= 0L) continue
            out.add(key)
        }
        return out
    }

    private fun writeFluidKeys(keys: List<PMKey<FluidStack>>): NBTTagList {
        val list = NBTTagList()
        keys.forEach { k ->
            val tag = NBTTagCompound()
            k.writeNBT(tag)
            list.appendTag(tag)
        }
        return list
    }

    private fun readFluidKeys(list: NBTTagList): List<PMKey<FluidStack>> {
        val out = ArrayList<PMKey<FluidStack>>(list.tagCount())
        for (i in 0 until list.tagCount()) {
            val tag = list.getCompoundTagAt(i)
            val key = PMFluidKeyType.readNBT(tag) as? PMKey<FluidStack> ?: continue
            if (key.count <= 0L) continue
            out.add(key)
        }
        return out
    }

    private companion object {
        private const val KEY_ENTRIES: String = "Entries"
        private const val KEY_ENTRY_TYPE: String = "Type"
        private const val KEY_ENTRY_ID: String = "Id"
        private const val KEY_ENTRY_DATA: String = "Data"

        private const val KEY_IGNORE_OUTPUT_FULL: String = "IgnoreOutputFull"

        private const val KEY_ENERGY_INPUT_PER_TICK: String = "EnergyInputPerTick"
        private const val KEY_ENERGY_OUTPUT_PER_TICK: String = "EnergyOutputPerTick"

        private const val KEY_ITEM_INPUTS: String = "ItemInputs"
        private const val KEY_ITEM_OUTPUTS: String = "ItemOutputs"

        private const val KEY_FLUID_INPUTS: String = "FluidInputs"
        private const val KEY_FLUID_OUTPUTS: String = "FluidOutputs"
        private const val KEY_FLUID_INPUTS_PER_TICK: String = "FluidInputsPerTick"
        private const val KEY_FLUID_OUTPUTS_PER_TICK: String = "FluidOutputsPerTick"
    }
}
