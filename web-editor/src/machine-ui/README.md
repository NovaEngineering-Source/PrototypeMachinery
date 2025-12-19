# Machine UI Editor (WIP)

本目录用于后续“机械 UI 自定义”编辑器（非 JEI Layout）。

## 目标
- 可视化编辑 ModularUI 风格的界面布局
- 组件/Widget 选择、拖拽布局、属性面板
- 导入/导出（JSON / ZenScript / 与模组端脚本 API 对接）

## 资源
- 复用现有 `/pm-textures/*` 与 `pm-asset-index.json`
- 后续可能需要额外提供 ModularUI 的贴图索引（例如 progress/slot/背景等）。

## 里程碑（草案）
1. 文档模型（schemaVersion + widgets + theme）
2. 基础画布（grid/对齐/缩放/拖拽）
3. Widget palette + Inspector
4. 导出器对接模组端
