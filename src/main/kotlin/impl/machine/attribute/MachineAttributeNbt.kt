package github.kasuminova.prototypemachinery.impl.machine.attribute

import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeInstance
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeModifier
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeRegistry
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeType
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList
import net.minecraft.util.ResourceLocation

/**
 * NBT serialization helpers for machine/process attributes.
 * 机械/进程属性的 NBT 序列化工具。
 *
 * - MachineAttributeMapImpl: serialize all attributes (base + modifiers).
 * - OverlayMachineAttributeMapImpl: serialize only local changes (local modifiers + base override).
 *
 * - MachineAttributeMapImpl：全量序列化（base + modifiers）。
 * - OverlayMachineAttributeMapImpl：只序列化本地变化（本地 modifiers + base override）。
 *
 * Attribute types are resolved through [MachineAttributeRegistry].
 *
 * 不考虑向后兼容：反序列化遇到未知 attribute id 会直接报错。
 */
public object MachineAttributeNbt {

    private const val KEY_ATTR_LIST: String = "Attributes"
    private const val KEY_TYPE: String = "Type"
    private const val KEY_BASE: String = "Base"
    private const val KEY_BASE_OVERRIDE: String = "BaseOverride"
    private const val KEY_MODS: String = "Modifiers"

    private const val KEY_MOD_ID: String = "Id"
    private const val KEY_MOD_AMOUNT: String = "Amount"
    private const val KEY_MOD_OP: String = "Op"
    private const val KEY_MOD_ADDER: String = "Adder"

    public fun writeMachineMap(map: MachineAttributeMapImpl): NBTTagCompound {
        val out = NBTTagCompound()
        val list = NBTTagList()

        for ((type, instance) in map.attributes) {
            list.appendTag(writeInstance(type, instance))
        }

        out.setTag(KEY_ATTR_LIST, list)
        return out
    }

    public fun readMachineMap(tag: NBTTagCompound, map: MachineAttributeMapImpl) {
        map.clear()

        if (!tag.hasKey(KEY_ATTR_LIST)) return
        val list = tag.getTagList(KEY_ATTR_LIST, 10)

        for (i in 0 until list.tagCount()) {
            val entry = list.getCompoundTagAt(i)
            val typeId = ResourceLocation(entry.getString(KEY_TYPE))
            val type = resolveType(typeId)

            val base = entry.getDouble(KEY_BASE)
            val instance = MachineAttributeInstanceImpl(type, base)
            readModifiers(entry, instance)
            map.setAttribute(instance)
        }
    }

    /**
     * Serialize only overlay-local changes. (So parent-derived baseline isn’t duplicated in saves.)
     */
    public fun writeOverlayLocal(map: OverlayMachineAttributeMapImpl): NBTTagCompound {
        val out = NBTTagCompound()
        val list = NBTTagList()

        for (instance in map.localInstances()) {
            if (!instance.hasLocalChanges()) continue

            val entry = NBTTagCompound()
            entry.setString(KEY_TYPE, instance.attribute.id.toString())

            // If parent is missing, persist local base.
            if (!instance.hasParent()) {
                entry.setDouble(KEY_BASE, instance.base)
            }

            instance.getBaseOverrideOrNull()?.let { entry.setDouble(KEY_BASE_OVERRIDE, it) }

            val mods = NBTTagList()
            for ((_, mod) in instance.localModifiers()) {
                mods.appendTag(writeModifier(mod))
            }
            entry.setTag(KEY_MODS, mods)

            list.appendTag(entry)
        }

        out.setTag(KEY_ATTR_LIST, list)
        return out
    }

    public fun readOverlayLocal(tag: NBTTagCompound, map: OverlayMachineAttributeMapImpl) {
        map.clearLocal()

        if (!tag.hasKey(KEY_ATTR_LIST)) return
        val list = tag.getTagList(KEY_ATTR_LIST, 10)

        for (i in 0 until list.tagCount()) {
            val entry = list.getCompoundTagAt(i)
            val typeId = ResourceLocation(entry.getString(KEY_TYPE))
            val type = resolveType(typeId)

            val instance = map.getOrCreateAttribute(type) as OverlayMachineAttributeInstanceImpl

            if (!instance.hasParent() && entry.hasKey(KEY_BASE)) {
                instance.base = entry.getDouble(KEY_BASE)
            }

            if (entry.hasKey(KEY_BASE_OVERRIDE)) {
                instance.setBaseOverrideOrNull(entry.getDouble(KEY_BASE_OVERRIDE))
            }

            instance.clearLocalModifiers()

            if (entry.hasKey(KEY_MODS)) {
                val mods = entry.getTagList(KEY_MODS, 10)
                for (j in 0 until mods.tagCount()) {
                    val m = mods.getCompoundTagAt(j)
                    instance.addLocalModifier(readModifier(m))
                }
            }
        }
    }

    private fun writeInstance(type: MachineAttributeType, instance: MachineAttributeInstance): NBTTagCompound {
        val entry = NBTTagCompound()
        entry.setString(KEY_TYPE, type.id.toString())
        entry.setDouble(KEY_BASE, instance.base)

        val mods = NBTTagList()
        for ((_, mod) in instance.modifiers) {
            mods.appendTag(writeModifier(mod))
        }
        entry.setTag(KEY_MODS, mods)

        return entry
    }

    private fun readModifiers(entry: NBTTagCompound, instance: MachineAttributeInstanceImpl) {
        // Clear existing modifiers just in case.
        val keys = instance.modifiers.keys.toList()
        for (k in keys) {
            instance.removeModifier(k)
        }

        if (!entry.hasKey(KEY_MODS)) return
        val mods = entry.getTagList(KEY_MODS, 10)
        for (i in 0 until mods.tagCount()) {
            val m = mods.getCompoundTagAt(i)
            instance.addModifier(readModifier(m))
        }
    }

    private fun writeModifier(mod: MachineAttributeModifier): NBTTagCompound {
        val out = NBTTagCompound()
        out.setString(KEY_MOD_ID, mod.id)
        out.setDouble(KEY_MOD_AMOUNT, mod.amount)
        out.setString(KEY_MOD_OP, mod.operation.name)

        // adder is only for tracing/debugging; persist as string for roundtrip.
        // adder 仅用于追踪/调试；以字符串形式持久化以支持 roundtrip。
        mod.adder?.let { out.setString(KEY_MOD_ADDER, it.toString()) }
        return out
    }

    private fun readModifier(tag: NBTTagCompound): MachineAttributeModifier {
        val id = tag.getString(KEY_MOD_ID)
        val amount = tag.getDouble(KEY_MOD_AMOUNT)
        val opName = tag.getString(KEY_MOD_OP)
        val op = runCatching { MachineAttributeModifier.Operation.valueOf(opName) }
            .getOrDefault(MachineAttributeModifier.Operation.ADDITION)

        val adder = if (tag.hasKey(KEY_MOD_ADDER)) tag.getString(KEY_MOD_ADDER) else null
        return MachineAttributeModifierImpl(id, amount, op, adder)
    }

    private fun resolveType(id: ResourceLocation): MachineAttributeType {
        return MachineAttributeRegistry.get(id)
            ?: error("Unknown MachineAttributeType id in NBT: $id")
    }
}
