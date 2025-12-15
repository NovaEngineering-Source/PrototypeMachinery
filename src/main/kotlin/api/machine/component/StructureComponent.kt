package github.kasuminova.prototypemachinery.api.machine.component

import github.kasuminova.prototypemachinery.api.machine.MachineInstance

/**
 * # StructureComponent
 *
 * A lightweight, structure-derived component.
 *
 * - Not a [MachineComponent]
 * - Has no systems / does not tick
 * - Is rebuilt during structure forming/refresh
 *
 * 结构组件：由结构形成/刷新阶段构建的轻量组件。
 * - 不参与 machine tick
 * - 不包含 systems 概念
 * - 不继承 MachineComponent（与运行时 ECS 组件隔离）
 */
public interface StructureComponent {

    /** Owner machine instance. / 所属机械实例 */
    public val owner: MachineInstance

    /** Optional provider (e.g. TileEntity). / 可选提供者（如 TileEntity） */
    public val provider: Any?
}
