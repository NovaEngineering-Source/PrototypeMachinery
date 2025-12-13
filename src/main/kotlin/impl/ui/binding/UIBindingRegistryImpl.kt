package github.kasuminova.prototypemachinery.impl.ui.binding

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.attribute.StandardMachineAttributes
import github.kasuminova.prototypemachinery.api.machine.component.type.ZSDataComponent
import github.kasuminova.prototypemachinery.api.machine.component.type.ZSDataComponentType
import github.kasuminova.prototypemachinery.api.ui.binding.UIBindingRegistry
import github.kasuminova.prototypemachinery.common.block.entity.MachineBlockEntity
import github.kasuminova.prototypemachinery.impl.machine.attribute.MachineAttributeMapImpl
import net.minecraft.util.ResourceLocation
import java.util.concurrent.ConcurrentHashMap

public object UIBindingRegistryImpl : UIBindingRegistry {

    private const val MAX_RESOLVE_DEPTH: Int = 8

    private data class Entry<T>(
        val owner: String,
        val getter: (MachineInstance) -> T,
        val setter: Any?
    )

    private val boolByMachine: MutableMap<ResourceLocation, MutableMap<String, Entry<Boolean>>> = ConcurrentHashMap()
    private val doubleByMachine: MutableMap<ResourceLocation, MutableMap<String, Entry<Double>>> = ConcurrentHashMap()
    private val stringByMachine: MutableMap<ResourceLocation, MutableMap<String, Entry<String>>> = ConcurrentHashMap()

    private val boolGlobal: MutableMap<String, Entry<Boolean>> = ConcurrentHashMap()
    private val doubleGlobal: MutableMap<String, Entry<Double>> = ConcurrentHashMap()
    private val stringGlobal: MutableMap<String, Entry<String>> = ConcurrentHashMap()

    override fun registerBool(
        machineId: ResourceLocation?,
        key: String,
        getter: (MachineInstance) -> Boolean,
        setter: ((MachineInstance, Boolean) -> Unit)?,
        owner: String
    ) {
        val k = key.trim()
        if (k.isEmpty()) return
        val entry = Entry(owner, getter, setter)
        if (machineId == null) {
            boolGlobal[k] = entry
        } else {
            val map = boolByMachine.computeIfAbsent(machineId) { ConcurrentHashMap() }
            map[k] = entry
        }
    }

    override fun registerDouble(
        machineId: ResourceLocation?,
        key: String,
        getter: (MachineInstance) -> Double,
        setter: ((MachineInstance, Double) -> Unit)?,
        owner: String
    ) {
        val k = key.trim()
        if (k.isEmpty()) return
        val entry = Entry(owner, getter, setter)
        if (machineId == null) {
            doubleGlobal[k] = entry
        } else {
            val map = doubleByMachine.computeIfAbsent(machineId) { ConcurrentHashMap() }
            map[k] = entry
        }
    }

    override fun registerString(
        machineId: ResourceLocation?,
        key: String,
        getter: (MachineInstance) -> String,
        setter: ((MachineInstance, String) -> Unit)?,
        owner: String
    ) {
        val k = key.trim()
        if (k.isEmpty()) return
        val entry = Entry(owner, getter, setter)
        if (machineId == null) {
            stringGlobal[k] = entry
        } else {
            val map = stringByMachine.computeIfAbsent(machineId) { ConcurrentHashMap() }
            map[k] = entry
        }
    }

    override fun clear(machineId: ResourceLocation) {
        boolByMachine.remove(machineId)
        doubleByMachine.remove(machineId)
        stringByMachine.remove(machineId)
    }

    override fun clearAll() {
        boolByMachine.clear()
        doubleByMachine.clear()
        stringByMachine.clear()
        boolGlobal.clear()
        doubleGlobal.clear()
        stringGlobal.clear()
        registerBuiltins()
    }

    // ---------------- builtins ----------------

    private fun registerBuiltins() {
        // bool
        boolGlobal["formed"] = Entry("builtin", { it.isFormed() }, null)
        boolGlobal["active"] = Entry("builtin", { !it.blockEntity.isInvalid }, null)

        // double
        doubleGlobal["formed"] = Entry("builtin", { if (it.isFormed()) 1.0 else 0.0 }, null)
        doubleGlobal["uptime"] = Entry("builtin", {
            val be = it.blockEntity
            when (be) {
                is MachineBlockEntity -> be.tickElapsed.toDouble()
                else -> be.world?.totalWorldTime?.toDouble() ?: 0.0
            }
        }, null)

        // string
        stringGlobal["machine_name"] = Entry("builtin", { it.type.name }, null)
        stringGlobal["machine_id"] = Entry("builtin", { it.type.id.toString() }, null)
        stringGlobal["formed"] = Entry("builtin", { it.isFormed().toString() }, null)
    }

    init {
        registerBuiltins()
    }

    // ---------------- resolve ----------------

    override fun resolveBool(machine: MachineInstance, key: String): UIBindingRegistry.ResolvedBool? {
        return resolveBoolInternal(machine, key, depth = 0)
    }

    private fun resolveBoolInternal(machine: MachineInstance, key: String, depth: Int): UIBindingRegistry.ResolvedBool? {
        if (depth > MAX_RESOLVE_DEPTH) return null
        val k = key.trim()
        if (k.isEmpty()) return null

        // dynamic builtin: data:<key> - uses ZSDataComponent
        if (k.startsWith("data:")) {
            val dataKey = k.removePrefix("data:").trim()
            if (dataKey.isEmpty()) return null
            return UIBindingRegistry.ResolvedBool(
                UIBindingRegistry.BindingKey(UIBindingRegistry.ValueType.BOOL, k),
                getter = { m ->
                    val component = m.componentMap.get(ZSDataComponentType) as? ZSDataComponent
                    component?.data?.getBool(dataKey, false) ?: false
                },
                setter = { m, v ->
                    val component = m.componentMap.get(ZSDataComponentType) as? ZSDataComponent
                    component?.data?.setBool(dataKey, v)
                }
            )
        }

        val machineId = machine.type.id
        val entry = boolByMachine[machineId]?.get(k) ?: boolGlobal[k] ?: return null

        @Suppress("UNCHECKED_CAST")
        val setter = entry.setter as? (MachineInstance, Boolean) -> Unit
        return UIBindingRegistry.ResolvedBool(
            UIBindingRegistry.BindingKey(UIBindingRegistry.ValueType.BOOL, k),
            entry.getter,
            setter
        )
    }

    override fun resolveDouble(machine: MachineInstance, key: String): UIBindingRegistry.ResolvedDouble? {
        return resolveDoubleInternal(machine, key, depth = 0)
    }

    private fun resolveDoubleInternal(machine: MachineInstance, key: String, depth: Int): UIBindingRegistry.ResolvedDouble? {
        if (depth > MAX_RESOLVE_DEPTH) return null
        val k = key.trim()
        if (k.isEmpty()) return null

        // dynamic builtin: data:<key> - uses ZSDataComponent
        if (k.startsWith("data:")) {
            val dataKey = k.removePrefix("data:").trim()
            if (dataKey.isEmpty()) return null
            return UIBindingRegistry.ResolvedDouble(
                UIBindingRegistry.BindingKey(UIBindingRegistry.ValueType.DOUBLE, k),
                getter = { m ->
                    val component = m.componentMap.get(ZSDataComponentType) as? ZSDataComponent
                    component?.data?.getDouble(dataKey, 0.0) ?: 0.0
                },
                setter = { m, v ->
                    val component = m.componentMap.get(ZSDataComponentType) as? ZSDataComponent
                    component?.data?.setDouble(dataKey, v)
                }
            )
        }

        // dynamic builtin: norm(<srcKey>;<min>;<max>) => 0..1
        parseFn3(k, "norm")?.let { (srcKey, minStr, maxStr) ->
            val min = minStr.toDoubleOrNull() ?: return null
            val max = maxStr.toDoubleOrNull() ?: return null
            val src = resolveDoubleInternal(machine, srcKey, depth + 1) ?: return null
            val denom = (max - min)
            if (denom == 0.0) return null
            return UIBindingRegistry.ResolvedDouble(
                UIBindingRegistry.BindingKey(UIBindingRegistry.ValueType.DOUBLE, k),
                getter = { m -> ((src.getter(m) - min) / denom).coerceIn(0.0, 1.0) },
                setter = null
            )
        }

        // dynamic builtin: attr:<namespace>:<path>
        if (k.startsWith("attr:")) {
            val idStr = k.removePrefix("attr:")
            val rl = runCatching { ResourceLocation(idStr) }.getOrNull() ?: return null
            val attrType = StandardMachineAttributes.getById(rl) ?: return null
            return UIBindingRegistry.ResolvedDouble(
                UIBindingRegistry.BindingKey(UIBindingRegistry.ValueType.DOUBLE, k),
                getter = { m ->
                    val map = m.attributeMap
                    (map as? MachineAttributeMapImpl)?.getAttribute(attrType)?.value ?: 0.0
                },
                setter = null
            )
        }

        val machineId = machine.type.id
        val entry = doubleByMachine[machineId]?.get(k) ?: doubleGlobal[k] ?: return null

        @Suppress("UNCHECKED_CAST")
        val setter = entry.setter as? (MachineInstance, Double) -> Unit
        return UIBindingRegistry.ResolvedDouble(
            UIBindingRegistry.BindingKey(UIBindingRegistry.ValueType.DOUBLE, k),
            entry.getter,
            setter
        )
    }

    override fun resolveString(machine: MachineInstance, key: String): UIBindingRegistry.ResolvedString? {
        return resolveStringInternal(machine, key, depth = 0)
    }

    private fun resolveStringInternal(machine: MachineInstance, key: String, depth: Int): UIBindingRegistry.ResolvedString? {
        if (depth > MAX_RESOLVE_DEPTH) return null
        val k = key.trim()
        if (k.isEmpty()) return null

        // dynamic builtin: data:<key> - uses ZSDataComponent
        if (k.startsWith("data:")) {
            val dataKey = k.removePrefix("data:").trim()
            if (dataKey.isEmpty()) return null
            return UIBindingRegistry.ResolvedString(
                UIBindingRegistry.BindingKey(UIBindingRegistry.ValueType.STRING, k),
                getter = { m ->
                    val component = m.componentMap.get(ZSDataComponentType) as? ZSDataComponent
                    component?.data?.getString(dataKey, "") ?: ""
                },
                setter = { m, v ->
                    val component = m.componentMap.get(ZSDataComponentType) as? ZSDataComponent
                    component?.data?.setString(dataKey, v)
                }
            )
        }

        // dynamic builtin: str(<doubleKey>)
        parseFn1(k, "str")?.let { srcKey ->
            val src = resolveDoubleInternal(machine, srcKey, depth + 1) ?: return null
            return UIBindingRegistry.ResolvedString(
                UIBindingRegistry.BindingKey(UIBindingRegistry.ValueType.STRING, k),
                getter = { m ->
                    val v = src.getter(m)
                    val asInt = v.toInt()
                    if (v == asInt.toDouble()) asInt.toString() else v.toString()
                },
                setter = null
            )
        }

        val machineId = machine.type.id
        val entry = stringByMachine[machineId]?.get(k) ?: stringGlobal[k] ?: return null

        @Suppress("UNCHECKED_CAST")
        val setter = entry.setter as? (MachineInstance, String) -> Unit
        return UIBindingRegistry.ResolvedString(
            UIBindingRegistry.BindingKey(UIBindingRegistry.ValueType.STRING, k),
            entry.getter,
            setter
        )
    }

    private fun parseFn1(key: String, name: String): String? {
        val prefix = "$name("
        if (!key.startsWith(prefix) || !key.endsWith(")")) return null
        val inner = key.substring(prefix.length, key.length - 1).trim()
        return inner.ifEmpty { null }
    }

    private fun parseFn3(key: String, name: String): Triple<String, String, String>? {
        val prefix = "$name("
        if (!key.startsWith(prefix) || !key.endsWith(")")) return null
        val inner = key.substring(prefix.length, key.length - 1)
        val parts = inner.split(';')
        if (parts.size != 3) return null
        val a = parts[0].trim()
        val b = parts[1].trim()
        val c = parts[2].trim()
        if (a.isEmpty() || b.isEmpty() || c.isEmpty()) return null
        return Triple(a, b, c)
    }
}
