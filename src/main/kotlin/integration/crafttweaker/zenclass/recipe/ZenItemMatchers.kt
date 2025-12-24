package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.recipe

import crafttweaker.annotations.ZenRegister
import crafttweaker.api.item.IItemStack
import crafttweaker.api.minecraft.CraftTweakerMC
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.ItemRequirementMatcherRegistry
import net.minecraft.item.ItemStack
import net.minecraftforge.common.util.Constants
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod

/**
 * CraftTweaker entry for registering dynamic item matchers.
 *
 * Usage:
 * ```zenscript
 * import mods.prototypemachinery.recipe.ItemMatchers;
 *
 * ItemMatchers.register("my_match", function(candidate, pattern) {
 *     // return true/false
 *     return candidate.hasTag && pattern.hasTag;
 * });
 * ```
 */
@ZenClass("mods.prototypemachinery.recipe.ItemMatchers")
@ZenRegister
public object ZenItemMatchers {

    @ZenMethod
    @JvmStatic
    public fun register(id: String, matcher: ZenItemMatcher) {
        val k = id.trim()
        require(k.isNotEmpty()) { "matcher id is blank" }

        ItemRequirementMatcherRegistry.register(k) { candidate: ItemStack, pattern: ItemStack ->
            val c: IItemStack = CraftTweakerMC.getIItemStack(candidate)
            val p: IItemStack = CraftTweakerMC.getIItemStack(pattern)
            matcher.matches(c, p)
        }
    }

    // NOTE:
    // ZenScript 1 (MC 1.12) 中，function(candidate, pattern) 的参数默认是 any。
    // 如果这里把参数声明成 IItemStack，脚本端经常会出现
    // “a method available but none matches the parameters (null.IItemStack)” 的类型匹配错误。
    // 因此这些辅助方法全部接受 Any?，并在内部安全转换。

    private fun asIItemStackOrNull(value: Any?): IItemStack? {
        return value as? IItemStack
    }

    /**
     * Returns the registry name (e.g. "minecraft:diamond") for the given stack.
     * Non-item inputs / empty stacks return an empty string.
     */
    @ZenMethod
    @JvmStatic
    public fun itemId(stack: Any?): String {
        val zs = asIItemStackOrNull(stack) ?: return ""
        val mc: ItemStack = CraftTweakerMC.getItemStack(zs)
        if (mc.isEmpty) return ""
        return mc.item.registryName?.toString() ?: ""
    }

    /**
     * Returns the item damage / meta of the given stack. Non-item inputs / empty stacks return 0.
     */
    @ZenMethod
    @JvmStatic
    public fun meta(stack: Any?): Int {
        val zs = asIItemStackOrNull(stack) ?: return 0
        val mc: ItemStack = CraftTweakerMC.getItemStack(zs)
        if (mc.isEmpty) return 0
        return mc.itemDamage
    }

    /**
     * Returns true if the stack has an NBT compound.
     */
    @ZenMethod
    @JvmStatic
    public fun hasTag(stack: Any?): Boolean {
        val zs = asIItemStackOrNull(stack) ?: return false
        val mc: ItemStack = CraftTweakerMC.getItemStack(zs)
        return !mc.isEmpty && mc.hasTagCompound()
    }

    /**
     * Returns true if the stack has an NBT int under the given key.
     */
    @ZenMethod
    @JvmStatic
    public fun hasNbtInt(stack: Any?, key: String): Boolean {
        val zs = asIItemStackOrNull(stack) ?: return false
        val mc: ItemStack = CraftTweakerMC.getItemStack(zs)
        if (mc.isEmpty) return false
        val tag = mc.tagCompound ?: return false
        return tag.hasKey(key, Constants.NBT.TAG_INT)
    }

    /**
     * Reads an NBT int under the given key, or returns defaultValue.
     */
    @ZenMethod
    @JvmStatic
    public fun nbtInt(stack: Any?, key: String, defaultValue: Int): Int {
        val zs = asIItemStackOrNull(stack) ?: return defaultValue
        val mc: ItemStack = CraftTweakerMC.getItemStack(zs)
        if (mc.isEmpty) return defaultValue
        val tag = mc.tagCompound ?: return defaultValue
        if (!tag.hasKey(key, Constants.NBT.TAG_INT)) return defaultValue
        return tag.getInteger(key)
    }
}

/**
 * Functional interface for ZenScript lambdas.
 */
public fun interface ZenItemMatcher {
    public fun matches(candidate: IItemStack, pattern: IItemStack): Boolean
}
