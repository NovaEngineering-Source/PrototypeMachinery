@file:Suppress("unused")

package github.kasuminova.prototypemachinery.integration.crafttweaker.definition.ui

/**
 * @deprecated 该包下的 definitions 已迁移到 core API：`github.kasuminova.prototypemachinery.api.ui.definition.*`
 *             这里仅保留 typealias 以避免旧代码/脚本侧的二进制引用出问题。
 */

@Deprecated(
    message = "Use github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition",
    replaceWith = ReplaceWith("github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition"),
    level = DeprecationLevel.WARNING
)
public typealias WidgetDefinition = github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition

@Deprecated(
    message = "Use github.kasuminova.prototypemachinery.api.ui.definition.PanelDefinition",
    replaceWith = ReplaceWith("github.kasuminova.prototypemachinery.api.ui.definition.PanelDefinition"),
    level = DeprecationLevel.WARNING
)
public typealias PanelDefinition = github.kasuminova.prototypemachinery.api.ui.definition.PanelDefinition

@Deprecated(
    message = "Use github.kasuminova.prototypemachinery.api.ui.definition.ButtonDefinition",
    replaceWith = ReplaceWith("github.kasuminova.prototypemachinery.api.ui.definition.ButtonDefinition"),
    level = DeprecationLevel.WARNING
)
public typealias ButtonDefinition = github.kasuminova.prototypemachinery.api.ui.definition.ButtonDefinition

@Deprecated(
    message = "Use github.kasuminova.prototypemachinery.api.ui.definition.ProgressBarDefinition",
    replaceWith = ReplaceWith("github.kasuminova.prototypemachinery.api.ui.definition.ProgressBarDefinition"),
    level = DeprecationLevel.WARNING
)
public typealias ProgressBarDefinition = github.kasuminova.prototypemachinery.api.ui.definition.ProgressBarDefinition

@Deprecated(
    message = "Use github.kasuminova.prototypemachinery.api.ui.definition.TextDefinition",
    replaceWith = ReplaceWith("github.kasuminova.prototypemachinery.api.ui.definition.TextDefinition"),
    level = DeprecationLevel.WARNING
)
public typealias TextDefinition = github.kasuminova.prototypemachinery.api.ui.definition.TextDefinition
