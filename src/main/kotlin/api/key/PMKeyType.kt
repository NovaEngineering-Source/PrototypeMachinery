package github.kasuminova.prototypemachinery.api.key

import net.minecraft.nbt.NBTTagCompound

/**
 * # PMKeyType
 * # PMKey 类型
 *
 * Defines the type of [PMKey].
 * Used to distinguish between ItemKeys, FluidKeys, etc.
 *
 * 定义 [PMKey] 的类型。
 * 用于区分 ItemKeys, FluidKeys 等。
 */
public interface PMKeyType {
    /**
     * A unique identifier for this key type.
     *
     * 此键类型的唯一标识符。
     */
    public val name: String

    /**
     * Deserializes a key from NBT.
     *
     * 从 NBT 反序列化键。
     *
     * @param nbt The NBT tag to read from.
     * @return The deserialized key, or null if the NBT data is invalid or empty.
     */
    public fun readNBT(nbt: NBTTagCompound): PMKey<*>?
}
