# PrototypeMachinery Web Editor（WIP）

一个独立的 Web 子项目（`web-editor/`），用于可视化编辑 UI/配方相关数据，并导出 **Runtime JSON** / **ZenScript**。

本项目的优先目标：**可维护、可演进、先把闭环跑通**。我们会刻意避免“为了架构而架构”，用最少的抽象换取长期可控的复杂度。

## 技术栈

- Vite + React + TypeScript
- Mantine（组件库，快速做出“像样”的界面）
- react-konva/Konva（画布拖拽与元素编辑）
- Zustand（状态管理）
- Zod（IR schema 校验）

## 运行

在 `web-editor/` 目录：

- `npm install`
- `npm run dev`

其他常用命令：

- `npm run build`：产出 `dist/`（用于 Pages）
- `npm run typecheck`：严格类型检查
- `npm run lint`：代码规范检查

## 部署

仓库根目录提供 GitHub Pages workflow，会构建 `web-editor/` 并部署到 Pages。

> 注意：我们使用 HashRouter + Vite `base: './'`，适配子路径静态托管。

## 当前状态与 Roadmap

### 基层框架（Foundation）

这些是“以后所有功能都依赖它”的底座能力：

1) **可撤销/重做（Undo/Redo）**（已实现）
	- 拖拽/批量操作会合并为单步历史（避免拖拽过程中产生海量历史）。

2) **基础快捷键**（已实现一批）
	- `Ctrl+Z` / `Ctrl+Shift+Z` / `Ctrl+Y`
	- `Delete/Backspace` 删除
	- 方向键 nudge（debounce 合并成一次 undo）

3) **画布尺寸可编辑**（已实现）

4) **稳定的最小 IR（中间格式）**（已实现）
	- IR 由 Zod 校验；导出时会剥离 editor-only 字段。

### 迭代增强（按需推进，避免过度设计）

1) **元素预设与更友好的添加方式**
	- 预期效果：添加 `slot/tank/energy/text` 有默认尺寸与默认 data。

2) **视图能力：缩放/平移/对齐线**
	- 预期效果：画布更像“编辑器”而不是“静态图片”，定位元素更轻松。
	- TODO：补充快捷键缩放（例如 `Ctrl +` / `Ctrl -` / `Ctrl 0`）。
	- 已实现：滚轮缩放；Space+拖拽平移；Fit-to-View；双击背景 Fit-to-View；定位到选中元素。

3) **资产系统（最简版）**
	- 预期效果：内建素材索引 + 用户导入图片（本地）+ 简单参数校验（如 9-slice）。

4) **导出对接真实脚本语义**
	- 预期效果：ZenScript 导出不再是 stub：
	  - 有稳定 nodeId/role/variant 语义
	  - `data` 能映射到 mod 端 placement data（例如 `energyLedYOffset`）

## 目录结构（保持简单）

- `src/pages/`：页面与布局（UI 壳）
- `src/editor/`：编辑器核心
  - `model/`：IR schema 与类型
  - `store/`：状态与操作（含 undo/redo）
  - `components/`：画布/编辑器组件
  - `io/`：本地存储、示例、下载
  - `exporters/`：导出器（ZenScript/JSON）

## 子编辑器：Machine UI Editor

Web Editor 内目前有一条独立的“机器 GUI 编辑器”链路：

- 页面入口：`/#/machine-ui`
- 开发者文档（功能现状 + 代码导航 + Roadmap）：`src/machine-ui/README.md`

运行时对接/变量绑定/多 Tab 构建对接文档（Mod 侧现状 + 契约）：

- `../docs/MachineUiEditorRuntime.md`
