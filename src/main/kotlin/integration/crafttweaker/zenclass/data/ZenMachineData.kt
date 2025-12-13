package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.data

import crafttweaker.annotations.ZenRegister
import crafttweaker.api.data.IData
import crafttweaker.mc1120.data.NBTConverter
import net.minecraft.nbt.NBTTagCompound
import stanhebben.zenscript.annotations.OperatorType
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenGetter
import stanhebben.zenscript.annotations.ZenMemberGetter
import stanhebben.zenscript.annotations.ZenMemberSetter
import stanhebben.zenscript.annotations.ZenMethod
import stanhebben.zenscript.annotations.ZenOperator

/**
 * ZenScript mutable data container backed by an internal Map.
 *
 * Provides operator-level access:
 *   `data["key"]` => IData (getter)
 *   `data["key"] = value` (setter)
 *   `data.member` => IData (member getter)
 *   `data.member = value` (member setter)
 *   `data.length` => Int (size)
 *
 * ZenScript 可变数据容器，底层使用 Map 存储。
 *
 * 提供操作符级别的访问：
 *   `data["key"]`         => IData (getter)
 *   `data["key"] = value` (setter)
 *   `data.member`         => IData (member getter)
 *   `data.member = value` (member setter)
 *   `data.length`         => Int (size)
 */
@ZenClass("mods.prototypemachinery.data.MachineData")
@ZenRegister
public class ZenMachineData internal constructor(
    private val onChange: (() -> Unit)? = null
) {

    // Internal storage using NBTTagCompound for efficient serialization
    // 内部使用 NBTTagCompound 存储，便于高效序列化
    private val storage: NBTTagCompound = NBTTagCompound()

    // ========== Index operators (ZenScript operator[]) ==========

    @ZenOperator(OperatorType.INDEXGET)
    public fun get(key: String): IData? {
        if (!storage.hasKey(key)) return null
        return NBTConverter.from(storage.getTag(key), false)
    }

    @ZenOperator(OperatorType.INDEXSET)
    public fun set(key: String, value: IData?) {
        if (value == null) {
            if (storage.hasKey(key)) {
                storage.removeTag(key)
                onChange?.invoke()
            }
        } else {
            storage.setTag(key, NBTConverter.from(value))
            onChange?.invoke()
        }
    }

    // ========== Member getter/setter (ZenScript data.xxx) ==========

    @ZenMemberGetter
    public fun getMember(key: String): IData? = get(key)

    @ZenMemberSetter
    public fun setMember(key: String, value: IData?): Unit = set(key, value)

    // ========== length ==========

    @ZenGetter("length")
    public fun size(): Int = storage.keySet.size

    // ========== Convenience methods ==========

    /**
     * Check if a key exists.
     * 检查键是否存在。
     */
    @ZenMethod
    public fun has(key: String): Boolean = storage.hasKey(key)

    /**
     * Remove a key.
     * 移除键。
     */
    @ZenMethod
    public fun remove(key: String) {
        if (storage.hasKey(key)) {
            storage.removeTag(key)
            onChange?.invoke()
        }
    }

    /**
     * Clear all data.
     * 清空所有数据。
     */
    @ZenMethod
    public fun clear() {
        if (storage.keySet.isEmpty()) return
        val keys = storage.keySet.toList()
        for (k in keys) {
            storage.removeTag(k)
        }
        onChange?.invoke()
    }

    /**
     * Convert to IData (immutable snapshot).
     * 转换为 IData（不可变快照）。
     */
    @ZenMethod
    public fun asIData(): IData = NBTConverter.from(storage.copy(), false)

    /**
     * Merge another IData map into this data.
     * 将另一个 IData map 合并到此数据。
     */
    @ZenMethod
    public fun merge(other: IData) {
        val otherNBT = NBTConverter.from(other)
        if (otherNBT is NBTTagCompound) {
            for (key in otherNBT.keySet) {
                storage.setTag(key, otherNBT.getTag(key).copy())
            }
            onChange?.invoke()
        }
    }

    // ========== Typed accessors ==========

    /**
     * Get a double value.
     * 获取 double 值。
     */
    @ZenMethod
    public fun getDouble(key: String, defaultValue: Double = 0.0): Double {
        return if (storage.hasKey(key)) storage.getDouble(key) else defaultValue
    }

    /**
     * Set a double value.
     * 设置 double 值。
     */
    @ZenMethod
    public fun setDouble(key: String, value: Double) {
        storage.setDouble(key, value)
        onChange?.invoke()
    }

    /**
     * Get an int value.
     * 获取 int 值。
     */
    @ZenMethod
    public fun getInt(key: String, defaultValue: Int = 0): Int {
        return if (storage.hasKey(key)) storage.getInteger(key) else defaultValue
    }

    /**
     * Set an int value.
     * 设置 int 值。
     */
    @ZenMethod
    public fun setInt(key: String, value: Int) {
        storage.setInteger(key, value)
        onChange?.invoke()
    }

    /**
     * Get a boolean value.
     * 获取 boolean 值。
     */
    @ZenMethod
    public fun getBool(key: String, defaultValue: Boolean = false): Boolean {
        return if (storage.hasKey(key)) storage.getBoolean(key) else defaultValue
    }

    /**
     * Set a boolean value.
     * 设置 boolean 值。
     */
    @ZenMethod
    public fun setBool(key: String, value: Boolean) {
        storage.setBoolean(key, value)
        onChange?.invoke()
    }

    /**
     * Get a string value.
     * 获取 string 值。
     */
    @ZenMethod
    public fun getString(key: String, defaultValue: String = ""): String {
        return if (storage.hasKey(key)) storage.getString(key) else defaultValue
    }

    /**
     * Set a string value.
     * 设置 string 值。
     */
    @ZenMethod
    public fun setString(key: String, value: String) {
        storage.setString(key, value)
        onChange?.invoke()
    }

    // ========== Serialization (internal) ==========

    /**
     * Write all data to NBT.
     * 将所有数据写入 NBT。
     */
    internal fun writeNBT(): NBTTagCompound = storage.copy() as NBTTagCompound

    /**
     * Read all data from NBT (replaces current contents).
     * 从 NBT 读取所有数据（替换当前内容）。
     */
    internal fun readNBT(nbt: NBTTagCompound) {
        val keys = storage.keySet.toList()
        for (k in keys) {
            storage.removeTag(k)
        }
        for (key in nbt.keySet) {
            storage.setTag(key, nbt.getTag(key).copy())
        }
    }

    public companion object {
        /**
         * Create a new empty MachineData.
         * 创建一个新的空 MachineData。
         */
        @JvmStatic
        @ZenMethod
        public fun create(): ZenMachineData = ZenMachineData()

        /**
         * Create MachineData from existing IData.
         * 从现有 IData 创建 MachineData。
         */
        @JvmStatic
        @ZenMethod
        public fun from(data: IData): ZenMachineData {
            val result = ZenMachineData()
            val nbt = NBTConverter.from(data)
            if (nbt is NBTTagCompound) {
                result.readNBT(nbt)
            }
            return result
        }
    }
}
