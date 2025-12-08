package github.kasuminova.prototypemachinery.api.machine.event

import github.kasuminova.prototypemachinery.api.machine.MachineInstance

/**
 * # MachineEvent - Base Machine Event
 * # MachineEvent - 机械事件基类
 *
 * Marker interface for all machine-scoped events. Events are dispatched to [github.kasuminova.prototypemachinery.api.machine.component.system.MachineSystem]
 * instances for processing.
 *
 * 所有机械范围事件的标记接口。事件会被分发到 [github.kasuminova.prototypemachinery.api.machine.component.system.MachineSystem]
 * 实例进行处理。
 */
public interface MachineEvent {

    /** The machine where the event occurs / 事件发生的机械实例 */
    public val machine: MachineInstance

}