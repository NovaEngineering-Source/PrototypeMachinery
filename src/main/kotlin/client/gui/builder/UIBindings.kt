package github.kasuminova.prototypemachinery.client.gui.builder

import com.cleanroommc.modularui.utils.BooleanConsumer
import com.cleanroommc.modularui.value.sync.BooleanSyncValue
import com.cleanroommc.modularui.value.sync.DoubleSyncValue
import com.cleanroommc.modularui.value.sync.PanelSyncManager
import com.cleanroommc.modularui.value.sync.StringSyncValue
import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.PrototypeMachineryAPI
import github.kasuminova.prototypemachinery.common.block.entity.MachineBlockEntity
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import java.util.function.DoubleConsumer

public class UIBindings {

    public data class BindingResult<T>(
        val syncValue: T,
        val writable: Boolean
    )

    private val warnedMissingBindings: MutableSet<String> = ConcurrentHashMap.newKeySet()

    public fun bindingKey(key: String?): String? = key?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Internal key used for ModularUI sync handlers.
     *
     * ModularUI requires that (key, id) uniquely maps to a single SyncHandler type.
     * A single logical binding like "formed" may be used as bool/double/string in
     * different widgets, so we namespace by value type to avoid collisions.
     */
    private fun syncKey(type: String, rawKey: String): String = "prototypemachinery:ui_binding:$type:$rawKey"

    public fun boolSyncKey(rawKey: String): String = syncKey("bool", rawKey)
    public fun doubleSyncKey(rawKey: String): String = syncKey("double", rawKey)
    public fun stringSyncKey(rawKey: String): String = syncKey("string", rawKey)

    private fun warnMissingBinding(machineTile: MachineBlockEntity, key: String, type: String) {
        // 避免刷屏：按 (machineType + key + type) 去重
        val token = "${machineTile.machine.type.id}|$type|$key"
        if (warnedMissingBindings.add(token)) {
            PrototypeMachinery.logger.warn(
                "UI binding key '{}' (type {}) was requested by UI, but MachineInstance did not provide it (machine={}).",
                key,
                type,
                machineTile.machine.type.id
            )
        }
    }

    public fun ensureBoolBinding(syncManager: PanelSyncManager, machineTile: MachineBlockEntity, rawKey: String): BindingResult<BooleanSyncValue> {
        val key = boolSyncKey(rawKey)
        val sync = syncManager.getOrCreateSyncHandler(key, 0, BooleanSyncValue::class.java) {
            if (syncManager.isClient) {
                BooleanSyncValue({ false })
            } else {
                val resolved = PrototypeMachineryAPI.uiBindingRegistry.resolveBool(machineTile.machine, rawKey)
                if (resolved == null) {
                    warnMissingBinding(machineTile, rawKey, "bool")
                }

                val getter = resolved?.getter ?: ({ false })
                val setterFn = resolved?.setter
                val setter: BooleanConsumer? = setterFn?.let { fn ->
                    BooleanConsumer { v -> fn.invoke(machineTile.machine, v) }
                }
                BooleanSyncValue({ getter.invoke(machineTile.machine) }, setter)
            }
        }
        val writable = if (!syncManager.isClient) {
            PrototypeMachineryAPI.uiBindingRegistry.resolveBool(machineTile.machine, rawKey)?.writable == true
        } else {
            false
        }
        return BindingResult(sync, writable)
    }

    public fun ensureDoubleBinding(syncManager: PanelSyncManager, machineTile: MachineBlockEntity, rawKey: String): BindingResult<DoubleSyncValue> {
        val key = doubleSyncKey(rawKey)
        val sync = syncManager.getOrCreateSyncHandler(key, 0, DoubleSyncValue::class.java) {
            if (syncManager.isClient) {
                DoubleSyncValue({ 0.0 })
            } else {
                val resolved = PrototypeMachineryAPI.uiBindingRegistry.resolveDouble(machineTile.machine, rawKey)
                if (resolved == null) {
                    warnMissingBinding(machineTile, rawKey, "double")
                }

                val getter = resolved?.getter ?: ({ 0.0 })
                val setterFn = resolved?.setter
                val setter: DoubleConsumer? = setterFn?.let { fn ->
                    DoubleConsumer { v -> fn.invoke(machineTile.machine, v) }
                }
                DoubleSyncValue({ getter.invoke(machineTile.machine) }, setter)
            }
        }
        val writable = if (!syncManager.isClient) {
            PrototypeMachineryAPI.uiBindingRegistry.resolveDouble(machineTile.machine, rawKey)?.writable == true
        } else {
            false
        }
        return BindingResult(sync, writable)
    }

    public fun ensureStringBinding(syncManager: PanelSyncManager, machineTile: MachineBlockEntity, rawKey: String): BindingResult<StringSyncValue> {
        val key = stringSyncKey(rawKey)
        val sync = syncManager.getOrCreateSyncHandler(key, 0, StringSyncValue::class.java) {
            if (syncManager.isClient) {
                StringSyncValue({ "" })
            } else {
                val resolved = PrototypeMachineryAPI.uiBindingRegistry.resolveString(machineTile.machine, rawKey)
                if (resolved == null) {
                    warnMissingBinding(machineTile, rawKey, "string")
                }

                val getter = resolved?.getter ?: ({ "" })
                val setterFn = resolved?.setter
                val setter: Consumer<String>? = setterFn?.let { fn ->
                    Consumer { v -> fn.invoke(machineTile.machine, v) }
                }

                StringSyncValue({ getter.invoke(machineTile.machine) }, setter)
            }
        }
        val writable = if (!syncManager.isClient) {
            PrototypeMachineryAPI.uiBindingRegistry.resolveString(machineTile.machine, rawKey)?.writable == true
        } else {
            false
        }
        return BindingResult(sync, writable)
    }
}
