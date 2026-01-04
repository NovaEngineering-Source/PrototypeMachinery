package github.kasuminova.prototypemachinery.api.machine.structure.pattern.predicate

import github.kasuminova.prototypemachinery.api.machine.structure.preview.BlockRequirement

/**
 * Optional capability for predicates that want to provide a dedicated preview requirement descriptor.
 *
 * This is intended to decouple “matching logic” from “how it should be displayed” in structure preview UI.
 *
 * 可选能力：predicate 可以提供用于结构预览/BOM 的 requirement 描述，
 * 从而把“匹配逻辑”与“显示逻辑”解耦。
 */
public interface PreviewRequirementProvider : BlockPredicate {

    /** Returns a stable, serializable requirement descriptor for preview/BOM generation. */
    public fun previewRequirement(): BlockRequirement
}
