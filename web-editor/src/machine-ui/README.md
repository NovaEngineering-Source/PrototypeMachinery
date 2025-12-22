# Machine UI Editor（Web Editor）

本目录实现的是 **Machine UI Editor**：用于可视化编辑“机器 GUI”（ModularUI 风格），并导出 **Runtime JSON** / **ZenScript**。

它与 JEI Layout Editor 是两条线：

- JEI Layout Editor：编辑配方展示（JEI）布局
- Machine UI Editor：编辑机器 GUI（玩家打开机器看到的界面）

入口页面：`/#/machine-ui`

## 已支持的功能（当前状态）

### 画布与视图

- 画布尺寸可编辑（W/H）
- 网格（gridSize）与吸附（gridSize 最小支持 1px；当 grid 很小时，网格渲染会自动稀疏化以避免卡顿）
- 类 JEI 的缩放/平移：
	- 滚轮缩放（Zoom-to-pointer）
	- `Space + 拖拽` 平移
	- 鼠标中键拖拽平移
	- 双击背景 Fit-to-View
	- 右上角缩放 overlay

### Guides（参考线/参考框）

- Guides 显示开关（editor-only，不参与导出）
- 选择/拖拽/锁定
- 吸附与边界限制
- 修复了 guides hit-test/坐标系问题（不再“挡住交互”）

### Widgets 与 Inspector

当前 widget 类型：

- **Text**：文本、字号、颜色、对齐、阴影
- **SlotGrid**：列/行、slotSize、gap、可选 slot 贴图、slotKey/startIndex、插入/取出开关
- **Progress**：预览进度、方向、纯色 or base/run 贴图、progressKey、tooltipTemplate
- **Image**：贴图路径（texturePath），可按贴图适配大小
- **Button**：文本、actionKey（运行时）
- **Toggle**：stateKey（运行时）、textOn/textOff、可选 textureOn/textureOff
- **Slider**：min/max/step、valueKey（运行时）、horizontal
- **PlayerInventory**：玩家背包区域（仅 x/y/w/h）

通用能力：

- 选中与属性面板编辑
- runtime 条件字段：`visibleIf` / `enabledIf`（Inspector 可编辑，运行时由 mod 侧解释器处理）
- 拖拽移动（带吸附/边界限制）
- Resize（Transformer 角点缩放；SlotGrid 会量化到格子）
- 锁定（locked=true 时禁止编辑/移动）

### Tabs（多 Tab / 分页）

- 自定义 tabs：`options.tabs[]` + `options.activeTabId`
- 兼容旧 A/B 背景字段（旧数据仍可用）
- 每个 tab 可单独配置背景贴图
- widget 具备 `tabId`：
	- 空/未定义：全局 widget（所有 tab 可见）
	- 非空：仅在 activeTabId 时可见

### 多选编辑（Multi-select）

- Ctrl/Shift/Meta 点击切换选中
- 框选（背景拖拽选择矩形，支持 additive）
- 多选拖拽：拖一个带动整体移动（一次拖拽只记一次 undo）
- 多选批量操作（Inspector）：
	- 批量设置 tabId
	- 批量锁定
	- 删除选中 / 清空选择
- 多选对齐/分布（基于“选中整体包围盒”）：
	- 对齐到选择：left/right/top/bottom/hcenter/vcenter/center
	- 均匀分布（按中心点）：水平/垂直
	- locked widget 不移动，但会参与包围盒计算（可当参考锚点）

### Undo/Redo 与快捷键（当前）

- Undo/Redo（拖拽移动、批量操作等会合并成单步）
- 常用快捷键（已实现的一部分）：
	- Delete/Backspace 删除
	- Ctrl+D 复制（当前：复制 primary 单选）
	- 方向键 nudge（会 debounce 合并成一次 undo）

### 导入/导出

- Editor JSON（完整文档，包含 editor-only 字段）
- Runtime JSON（用于运行时的精简结构）
- ZenScript exporter（可用）：
	- 默认导出 runtime-json 注册形式（把 JSON 作为字符串嵌入脚本，并调用 `UIRegistry.registerRuntimeJsonWithPriority`）
	- 也支持 builders 形式，但仍有部分 widget/参数是“近似映射”（以导出器说明为准）
- localStorage 保存/加载

示例：`src/machine-ui/examples/runtime-json.sample.json`（tabs + visibleIf/enabledIf + 额外 widget）。

### 贴图/资产

- 复用 `/pm-textures/*` 与 `pm-asset-index.json`
- `pmTextureUrl(...)` + `useCachedImage(...)` 做贴图预览与尺寸读取
- Inspector 提供“按贴图适配大小”（SlotGrid/Progress）

### 字体预览（editor-only）

右侧「文档」页签支持字体预览（minecraft/system/custom）。

- 字体预览不会进入 runtime 导出（仅用于所见即所得）。
- 如果你希望在本地启用 Mojangles / Unifont 等预览字体：
	- 见 `../../public/fonts/README.md`

## 已知限制（有意为之/暂未做）

- 多选状态下暂不支持 Transformer resize（避免语义不清）；目前仅支持多选移动与对齐/分布
- “复制”目前只复制 primary 单选（多选复制还没做）
- 对齐/分布暂时只在 Inspector 里有按钮（快捷键未加）
- box select 目前是 rect-intersects（相交即算选中），还没做“必须完全包含”的模式切换

## 运行时对接（重要语义差异）

运行时对接的“字段契约 / 兼容策略 / 支持的 widget type / tabs/条件/表达式”的权威来源是：

- `../../../docs/MachineUiEditorRuntime.md`

其中一个容易踩坑的点：

- `visibleIf` 与 `enabledIf` 在当前 Mod 侧实现里最终都落为 enable gate（`isEnabled = visible && enabled`），
	并不保证严格的“不可见且不渲染”。

## 代码导航（从哪里改）

- 画布与交互：`src/machine-ui/components/MachineUiCanvasStage.tsx`
- 右侧面板：`src/machine-ui/components/MachineUiInspector.tsx`
- 工具栏（导入导出/快捷操作/热键）：`src/machine-ui/components/MachineUiToolbar.tsx`
- 状态与 undo/redo：`src/machine-ui/store/machineUiStore.ts`
- IR / Zod schema：`src/machine-ui/model/ir.ts`
- 对齐/分布纯函数：`src/machine-ui/model/selectionOps.ts`
- 导出：
	- Runtime JSON：`src/machine-ui/exporters/jsonExporter.ts`
	- ZenScript：`src/machine-ui/exporters/zsExporter.ts`（runtime-json 注册形式已可用；builders 仍有部分近似映射）

## 下一步（Roadmap / 计划）

### 编辑器侧（短期）

- 多选快捷键：对齐/分布、批量锁定、复制多选
- 框选优化：
	- 触发阈值（防误触）
	- 相交 vs 完全包含
	- 可选忽略 locked
- 更完整的“布局工具”：
	- 统一尺寸（match width/height）
	- 等间距分布（按边缘而非中心）
	- “对齐到 primary”（以 primary 为锚）

### 运行时对接（中期）

- 区分 `visibleIf` 与 `enabledIf` 的真正语义（隐藏 vs 禁用）
- 扩展表达式能力（仍建议保持最小化，复杂逻辑放脚本端）
- 补齐非 LEFT tabPosition 的更完整实现（目前部分为 fallback）

### 资源化管线（长期）

- Runtime JSON loader（从资源或脚本注册），不再依赖复制粘贴 ZenScript
- 文档版本迁移与更友好错误提示

