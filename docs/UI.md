# UI：默认 ModularUI + 脚本 UIRegistry

本项目的 UI 分两条线：

1. **默认原生 UI**：基于 ModularUI，为机器/外设提供默认界面。
2. **脚本 UI**：CraftTweaker 侧通过 `UIRegistry` 注册 UI 定义，并可通过 `UIBindings` 做数据绑定。

## 代码位置

- 默认 UI（示例：Hatch）：
  - `src/main/kotlin/common/block/hatch/*/*GUI.kt`
  - `src/main/kotlin/client/gui/sync/*`
  - `src/main/kotlin/client/gui/widget/*`

- 脚本 UI：
  - `src/main/kotlin/integration/crafttweaker/zenclass/ui/PMUI.kt`
  - `src/main/kotlin/integration/crafttweaker/zenclass/ui/UIRegistry.kt`
  - `src/main/kotlin/integration/crafttweaker/zenclass/ui/UIBindings.kt`

- UI 注册表实现：`src/main/kotlin/impl/ui/registry/MachineUIRegistryImpl.kt`

## 同步原则（很重要）

- **权威数据在服务端**：ModularUI 的同步应以服务端为准。
- 客户端 UI 不应“自行重建”资源列表来覆盖服务端同步结果。

（这也是 ResourceStorage 增量同步能稳定工作的前提。）

## 翻译（i18n）与注释

默认 GUI 的文本主要来自两类来源：

1. **lang 文件（需要翻译）**：按钮 tooltip、固定文案等。
2. **运行时动态文本（不走本 mod 的 lang）**：例如流体名称（来自 `FluidStack.localizedName`）和存储数值（格式化后直接拼接）。

lang 文件位置：

- 英文：`src/main/resources/assets/prototypemachinery/lang/en_us.lang`
- 中文：`src/main/resources/assets/prototypemachinery/lang/zh_cn.lang`

### 结构投影预览（客户端）相关 i18n

结构投影预览功能（HUD/聊天提示/调试命令）使用以下命名空间：

- 调试命令 `/pm_preview`：`pm.preview.*`
  - 例如：`pm.preview.started` / `pm.preview.stopped` / `pm.preview.unknown_structure`
- 投影 HUD / 提示：`pm.projection.*`
  - 例如：`pm.projection.hud.orientation_status`、`pm.projection.chat.locked`
- 按键绑定名称：`key.pm.preview.*`
  - `key.pm.preview.lock_orientation`
  - `key.pm.preview.rotate_positive`
  - `key.pm.preview.rotate_negative`

> 备注：按键绑定的 key 若缺少翻译，会在“控制设置”里显示原始 key 字符串。为了可用性，建议中英文都补齐。

### Hatch 控件通用文案（新界面也会用到）

以下 key 被多个 Hatch GUI 共用（包括流体仓 `FluidHatchGUI`、流体 IO 仓 `FluidIOHatchGUI`）：

- `prototypemachinery.gui.hatch.auto_input`
  - 用途：自动输入按钮的 tooltip
  - 代码：`*HatchGUI.kt` 的控制按钮构建（`ToggleButton().tooltip { ... }`）

- `prototypemachinery.gui.hatch.auto_output`
  - 用途：自动输出按钮的 tooltip

- `prototypemachinery.gui.hatch.clear`
  - 用途：清空按钮的 tooltip（清空内部存储）

（可选/预留）

- `prototypemachinery.gui.hatch.input` / `prototypemachinery.gui.hatch.output`
  - 用途：如果某些 GUI 需要展示“输入/输出”固定标签，可复用这两个 key。

### 流体仓界面的动态文本（无需翻译）

流体仓/流体 IO 仓的“流体名称”和“数量（mB）”显示为动态文本：

- 流体名称：`FluidStack.localizedName`（来自流体本身或其来源 mod 的语言文件）
- 数量显示：由 `NumberFormatUtil` 格式化（label 为紧凑格式，tooltip 为带千分位的完整格式）

因此这里**不需要**为每种流体额外添加翻译 key，本 mod 也不会覆盖外部流体的名称翻译。

## See also

- [资源存储（ResourceStorage / EnergyStorage）](./Storage.md)
- [CraftTweaker（ZenScript）集成](./CraftTweaker.md)
