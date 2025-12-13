package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.ui

import crafttweaker.annotations.ZenRegister
import github.kasuminova.prototypemachinery.api.PrototypeMachineryAPI
import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.component.type.ZSDataComponent
import github.kasuminova.prototypemachinery.api.machine.component.type.ZSDataComponentType
import net.minecraft.util.ResourceLocation
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod

/**
 * ZenScript entry for registering UI data bindings.
 *
 * This is primarily designed for scripts to expose dynamic machine values to UI widgets
 * without writing Java/Kotlin code.
 *
 * Data is stored in the machine's ZSDataComponent.
 * 数据存储在机器的 ZSDataComponent 中。
 */
@ZenClass("mods.prototypemachinery.ui.UIBindings")
@ZenRegister
public object UIBindings {

    private const val OWNER: String = "crafttweaker"

    private fun getDataComponent(m: MachineInstance): ZSDataComponent? {
        return m.componentMap.get(ZSDataComponentType) as? ZSDataComponent
    }

    @ZenMethod
    @JvmStatic
    public fun registerDoubleData(machineId: String, key: String, dataKey: String, defaultValue: Double) {
        registerDoubleDataWritable(machineId, key, dataKey, defaultValue, true)
    }

    @ZenMethod
    @JvmStatic
    public fun registerDoubleDataWritable(machineId: String, key: String, dataKey: String, defaultValue: Double, writable: Boolean) {
        val id = ResourceLocation(machineId)
        val k = key.trim()
        val dk = dataKey.trim()
        if (k.isEmpty() || dk.isEmpty()) return

        val setter: ((MachineInstance, Double) -> Unit)? = if (writable) {
            { m, v -> getDataComponent(m)?.data?.setDouble(dk, v) }
        } else null

        PrototypeMachineryAPI.uiBindingRegistry.registerDouble(
            id,
            k,
            getter = { m -> getDataComponent(m)?.data?.getDouble(dk, defaultValue) ?: defaultValue },
            setter = setter,
            owner = OWNER
        )
    }

    @ZenMethod
    @JvmStatic
    public fun registerDoubleDataClamped(machineId: String, key: String, dataKey: String, defaultValue: Double, min: Double, max: Double) {
        val id = ResourceLocation(machineId)
        val k = key.trim()
        val dk = dataKey.trim()
        if (k.isEmpty() || dk.isEmpty()) return

        PrototypeMachineryAPI.uiBindingRegistry.registerDouble(
            id,
            k,
            getter = { m -> getDataComponent(m)?.data?.getDouble(dk, defaultValue) ?: defaultValue },
            setter = { m, v -> getDataComponent(m)?.data?.setDouble(dk, v.coerceIn(min, max)) },
            owner = OWNER
        )
    }

    @ZenMethod
    @JvmStatic
    public fun registerBoolData(machineId: String, key: String, dataKey: String, defaultValue: Boolean) {
        registerBoolDataWritable(machineId, key, dataKey, defaultValue, true)
    }

    @ZenMethod
    @JvmStatic
    public fun registerBoolDataWritable(machineId: String, key: String, dataKey: String, defaultValue: Boolean, writable: Boolean) {
        val id = ResourceLocation(machineId)
        val k = key.trim()
        val dk = dataKey.trim()
        if (k.isEmpty() || dk.isEmpty()) return

        val setter: ((MachineInstance, Boolean) -> Unit)? = if (writable) {
            { m, v -> getDataComponent(m)?.data?.setBool(dk, v) }
        } else null

        PrototypeMachineryAPI.uiBindingRegistry.registerBool(
            id,
            k,
            getter = { m -> getDataComponent(m)?.data?.getBool(dk, defaultValue) ?: defaultValue },
            setter = setter,
            owner = OWNER
        )
    }

    @ZenMethod
    @JvmStatic
    public fun registerStringData(machineId: String, key: String, dataKey: String, defaultValue: String) {
        registerStringDataWritable(machineId, key, dataKey, defaultValue, true)
    }

    @ZenMethod
    @JvmStatic
    public fun registerStringDataWritable(machineId: String, key: String, dataKey: String, defaultValue: String, writable: Boolean) {
        val id = ResourceLocation(machineId)
        val k = key.trim()
        val dk = dataKey.trim()
        if (k.isEmpty() || dk.isEmpty()) return

        val setter: ((MachineInstance, String) -> Unit)? = if (writable) {
            { m, v -> getDataComponent(m)?.data?.setString(dk, v) }
        } else null

        PrototypeMachineryAPI.uiBindingRegistry.registerString(
            id,
            k,
            getter = { m -> getDataComponent(m)?.data?.getString(dk, defaultValue) ?: defaultValue },
            setter = setter,
            owner = OWNER
        )
    }

    @ZenMethod
    @JvmStatic
    public fun clear(machineId: String) {
        PrototypeMachineryAPI.uiBindingRegistry.clear(ResourceLocation(machineId))
    }

    @ZenMethod
    @JvmStatic
    public fun clearAll() {
        PrototypeMachineryAPI.uiBindingRegistry.clearAll()
    }
}
