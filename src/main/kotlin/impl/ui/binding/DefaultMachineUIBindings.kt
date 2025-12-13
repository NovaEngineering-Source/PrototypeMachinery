package github.kasuminova.prototypemachinery.impl.ui.binding

import github.kasuminova.prototypemachinery.api.machine.attribute.StandardMachineAttributes
import github.kasuminova.prototypemachinery.api.ui.binding.MachineUIBindings
import github.kasuminova.prototypemachinery.common.block.entity.MachineBlockEntity
import github.kasuminova.prototypemachinery.impl.MachineInstanceImpl
import github.kasuminova.prototypemachinery.impl.machine.attribute.MachineAttributeMapImpl
import net.minecraft.util.ResourceLocation

/**
 * 默认（内置）UI 绑定：用于在没有自定义 provider 的情况下，保证 ZenScript 的 bindKey 能跑通。
 *
 * 约定的内置 key（可按需扩展）：
 * - bool:  "formed", "active"
 * - double:"formed", "uptime", "attr:<namespace>:<path>"（仅支持内置 StandardMachineAttributes）
 * - string:"machine_name", "machine_id"
 */
public class DefaultMachineUIBindings(
    private val machine: MachineInstanceImpl
) : MachineUIBindings {

    override fun booleanGetter(key: String): (() -> Boolean)? {
        return when (key.trim()) {
            "formed" -> ({ machine.isFormed() })
            "active" -> ({ machine.isActive() })
            else -> null
        }
    }

    override fun doubleGetter(key: String): (() -> Double)? {
        val k = key.trim()
        if (k == "formed") {
            return { if (machine.isFormed()) 1.0 else 0.0 }
        }
        if (k == "uptime") {
            return {
                val be = machine.blockEntity
                when (be) {
                    is MachineBlockEntity -> be.tickElapsed.toDouble()
                    else -> be.world?.totalWorldTime?.toDouble() ?: 0.0
                }
            }
        }
        if (k.startsWith("attr:")) {
            val idStr = k.removePrefix("attr:")
            val rl = runCatching { ResourceLocation(idStr) }.getOrNull() ?: return null
            val attrType = StandardMachineAttributes.getById(rl) ?: return null
            val map = machine.attributeMap
            return {
                (map as? MachineAttributeMapImpl)?.getAttribute(attrType)?.value ?: 0.0
            }
        }
        return null
    }

    override fun stringGetter(key: String): (() -> String)? {
        return when (key.trim()) {
            "machine_name" -> ({ machine.type.name })
            "machine_id" -> ({ machine.type.id.toString() })
            "formed" -> ({ machine.isFormed().toString() })
            else -> null
        }
    }
}
