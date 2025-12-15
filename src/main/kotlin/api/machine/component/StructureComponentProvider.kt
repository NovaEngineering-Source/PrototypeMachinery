package github.kasuminova.prototypemachinery.api.machine.component

import github.kasuminova.prototypemachinery.api.machine.MachineInstance

/**
 * # StructureComponentProvider
 *
 * Implement this on a BlockEntity (TileEntity) to provide one or more [StructureComponent]s
 * during machine structure forming/refresh.
 *
 * 结构组件提供者：
 * 让某个方块实体在结构形成/刷新时提供一个或多个 [StructureComponent]。
 */
public fun interface StructureComponentProvider {

    /**
     * Create structure-derived components for the given machine.
     *
     * 为给定机械创建结构派生组件；允许返回多个。
     */
    public fun createStructureComponents(machine: MachineInstance): Collection<StructureComponent>
}
