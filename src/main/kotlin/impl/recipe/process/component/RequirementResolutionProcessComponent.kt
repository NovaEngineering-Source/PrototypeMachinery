package github.kasuminova.prototypemachinery.impl.recipe.process.component

import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.process.component.RecipeProcessComponent
import github.kasuminova.prototypemachinery.api.recipe.process.component.RecipeProcessComponentType
import github.kasuminova.prototypemachinery.impl.key.fluid.PMFluidKeyType
import github.kasuminova.prototypemachinery.impl.key.item.PMItemKeyType
import net.minecraft.nbt.NBTTagCompound

/**
 * Stores per-process "resolution" state for dynamic requirements.
 *
 * Currently used for:
 * - fuzzy input locking: (requirementId, groupIndex, phase) -> chosen PMKey prototype
 */
public class RequirementResolutionProcessComponent(
    override val owner: RecipeProcess,
) : RecipeProcessComponent {

    override val type: RecipeProcessComponentType<*> = RequirementResolutionProcessComponentType

    private val locks: MutableMap<String, PMKey<*>> = linkedMapOf()

    public fun getLock(id: String): PMKey<*>? = locks[id]

    /**
     * Put a lock, returning the previous value (if any).
     * Stored value is copied to avoid accidental external mutation.
     */
    public fun putLock(id: String, key: PMKey<*>): PMKey<*>? {
        return locks.put(id, key.copy())
    }

    public fun removeLock(id: String): PMKey<*>? = locks.remove(id)

    override fun serializeNBT(): NBTTagCompound {
        val tag = NBTTagCompound()

        val locksTag = NBTTagCompound()
        for ((id, key) in locks) {
            val keyTag = NBTTagCompound()
            keyTag.setString("PMKeyType", key.type.name)
            key.writeNBT(keyTag)
            locksTag.setTag(id, keyTag)
        }
        tag.setTag("Locks", locksTag)

        return tag
    }

    override fun deserializeNBT(nbt: NBTTagCompound) {
        locks.clear()

        if (!nbt.hasKey("Locks")) return
        val locksTag = nbt.getCompoundTag("Locks")

        val ids = runCatching { locksTag.keySet.toList().sorted() }.getOrDefault(emptyList())
        for (id in ids) {
            val keyTag = locksTag.getCompoundTag(id)
            val type = keyTag.getString("PMKeyType")

            val key = when (type) {
                PMItemKeyType.name -> PMItemKeyType.readNBT(keyTag)
                PMFluidKeyType.name -> PMFluidKeyType.readNBT(keyTag)
                else -> null
            }

            if (key != null) {
                locks[id] = key
            }
        }
    }
}
