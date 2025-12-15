package github.kasuminova.prototypemachinery.api.machine.structure.pattern.predicate

import github.kasuminova.prototypemachinery.api.machine.structure.preview.BlockRequirement

/**
 * Optional capability for [BlockPredicate]s that can be represented in structure preview UI.
 *
 * 可选能力：当一个 [BlockPredicate] 能被结构预览 UI 表达时，实现此接口。
 */
public interface PreviewableBlockPredicate : BlockPredicate {

    /**
     * Returns a stable, serializable requirement descriptor for preview/BOM generation.
     *
     * 返回一个稳定、可序列化的需求描述，用于预览/BOM 生成。
     */
    public fun toRequirement(): BlockRequirement

}
