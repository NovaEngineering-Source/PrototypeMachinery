package github.kasuminova.prototypemachinery.api.machine.event

import github.kasuminova.prototypemachinery.api.machine.MachineInstance

/**
 * # MachineTickEvent - Tick phase event
 * # MachineTickEvent - Tick 阶段事件
 *
 * Event hierarchy representing the three tick phases dispatched to [github.kasuminova.prototypemachinery.api.machine.component.system.MachineSystem].
 *
 * 表示三段 tick（Pre/Normal/Post）的事件层级，用于分发给各类 MachineSystem。
 */
public sealed interface MachineTickEvent : MachineEvent {

    /** Pre phase. / 前置阶段。 */
    public class Pre(override val machine: MachineInstance) : MachineTickEvent

    /** Normal phase. / 正常阶段。 */
    public class Normal(override val machine: MachineInstance) : MachineTickEvent

    /** Post phase. / 后置阶段。 */
    public class Post(override val machine: MachineInstance) : MachineTickEvent

}
