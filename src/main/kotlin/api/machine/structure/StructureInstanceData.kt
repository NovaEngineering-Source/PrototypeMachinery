package github.kasuminova.prototypemachinery.api.machine.structure

/**
 * # StructureInstanceData - Matched structure instance data
 * # StructureInstanceData - 结构实例数据（匹配产物）
 *
 * Data produced during structure matching. Typically includes orientation and other per-instance metadata.
 *
 * 结构匹配过程中生成的数据，通常包含朝向等每实例元数据。
 */
public interface StructureInstanceData {

    public val orientation: StructureOrientation

}