package github.kasuminova.prototypemachinery.impl.recipe.process.component

import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeType
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.process.component.RecipeProcessComponent
import github.kasuminova.prototypemachinery.api.recipe.process.component.RecipeProcessComponentType
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList
import net.minecraft.nbt.NBTTagString
import org.jetbrains.annotations.ApiStatus

/**
 * Per-process selective state.
 * 每个进程的选择性状态（选择结果 + 已应用的属性修改器）。
 */
@ApiStatus.Experimental
public class SelectiveStateProcessComponent(
    override val owner: RecipeProcess,
) : RecipeProcessComponent {

    override val type: RecipeProcessComponentType<*> = SelectiveStateProcessComponentType

    /** selectionId -> selected candidate index; -1 means "disabled". */
    private val selectedIndexById: MutableMap<String, Int> = linkedMapOf()

    /** selectionId -> list of applied attribute modifier refs ("<attrRL>|<modifierId>") */
    private val appliedModifierRefsById: MutableMap<String, MutableList<String>> = linkedMapOf()

    public fun getSelectedIndex(selectionId: String): Int? = selectedIndexById[selectionId]

    public fun setSelectedIndex(selectionId: String, index: Int) {
        selectedIndexById[selectionId] = index
    }

    public fun recordAppliedModifier(selectionId: String, attribute: MachineAttributeType, modifierId: String) {
        val key = attribute.id.toString() + "|" + modifierId
        appliedModifierRefsById.computeIfAbsent(selectionId) { mutableListOf() }.add(key)
    }

    public fun consumeAppliedModifiers(selectionId: String): List<Pair<String, String>> {
        val list = appliedModifierRefsById.remove(selectionId) ?: return emptyList()
        return list.mapNotNull { ref ->
            val idx = ref.indexOf('|')
            if (idx <= 0 || idx >= ref.length - 1) return@mapNotNull null
            ref.substring(0, idx) to ref.substring(idx + 1)
        }
    }

    override fun serializeNBT(): NBTTagCompound {
        val nbt = NBTTagCompound()

        val selected = NBTTagCompound()
        for ((id, idx) in selectedIndexById) {
            selected.setInteger(id, idx)
        }
        nbt.setTag("Selected", selected)

        val applied = NBTTagCompound()
        for ((id, refs) in appliedModifierRefsById) {
            val list = NBTTagList()
            refs.forEach { list.appendTag(NBTTagString(it)) }
            applied.setTag(id, list)
        }
        nbt.setTag("Applied", applied)

        return nbt
    }

    override fun deserializeNBT(nbt: NBTTagCompound) {
        selectedIndexById.clear()
        appliedModifierRefsById.clear()

        if (nbt.hasKey("Selected")) {
            val selected = nbt.getCompoundTag("Selected")
            @Suppress("DEPRECATION")
            for (key in selected.keySet) {
                selectedIndexById[key] = selected.getInteger(key)
            }
        }

        if (nbt.hasKey("Applied")) {
            val applied = nbt.getCompoundTag("Applied")
            @Suppress("DEPRECATION")
            for (key in applied.keySet) {
                val list = applied.getTagList(key, 8) // 8 = String
                val refs = mutableListOf<String>()
                for (i in 0 until list.tagCount()) {
                    refs.add(list.getStringTagAt(i))
                }
                appliedModifierRefsById[key] = refs
            }
        }
    }

}
