package github.kasuminova.prototypemachinery.impl.ui.action

import github.kasuminova.prototypemachinery.api.ui.action.UIActionRegistry
import github.kasuminova.prototypemachinery.common.block.entity.MachineBlockEntity
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.ResourceLocation
import java.util.concurrent.ConcurrentHashMap

public object UIActionRegistryImpl : UIActionRegistry {

    private data class Entry(
        val owner: String,
        val handler: UIActionRegistry.Handler
    )

    private val byMachine: MutableMap<ResourceLocation, MutableMap<String, Entry>> = ConcurrentHashMap()
    private val global: MutableMap<String, Entry> = ConcurrentHashMap()

    override fun register(machineId: ResourceLocation?, actionKey: String, handler: UIActionRegistry.Handler, owner: String) {
        val k = actionKey.trim()
        if (k.isEmpty()) return
        val entry = Entry(owner, handler)
        if (machineId == null) {
            global[k] = entry
        } else {
            val map = byMachine.computeIfAbsent(machineId) { ConcurrentHashMap() }
            map[k] = entry
        }
    }

    override fun clear(machineId: ResourceLocation) {
        byMachine.remove(machineId)
    }

    override fun clearAll() {
        byMachine.clear()
        global.clear()
    }

    override fun invoke(player: EntityPlayerMP, machine: MachineBlockEntity, actionKey: String, payload: NBTTagCompound): Boolean {
        val k = actionKey.trim()
        if (k.isEmpty()) return false
        val machineId = machine.machine.type.id

        val entry = byMachine[machineId]?.get(k) ?: global[k]
        if (entry != null) {
            entry.handler.handle(player, machine, payload)
            return true
        }

        return false
    }
}
