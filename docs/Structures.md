# 多方块结构系统（MachineStructure）

English translation: [`docs/en/Structures.md`](./en/Structures.md)

本项目的结构系统用于描述“多方块机器”的形状、朝向、以及匹配逻辑。

它由三部分组成：

1. **结构定义 API**：`MachineStructure` / `StructurePattern` / `StructureValidator`
2. **结构注册表**：`StructureRegistryImpl`（支持缓存与朝向变换）
3. **JSON 加载器**：`StructureLoader`（从 config 读取结构 JSON）

## 代码位置

- API：`src/main/kotlin/api/machine/structure/*`
- 实现：`src/main/kotlin/impl/machine/structure/*`
- JSON 结构数据：`src/main/kotlin/common/structure/serialization/StructureData.kt`
- 加载器：`src/main/kotlin/common/structure/loader/StructureLoader.kt`

## 朝向与旋转

- 结构本体保存一个“基础朝向”（通常默认 `NORTH/UP`）。
- `StructureOrientation(front, top)` 使用 **front+top** 定义结构朝向，因此理论上支持 **24 种**正交姿态（而不只是 4 向水平旋转）。
- `StructureRegistryImpl.get(id, orientation, horizontalFacing)` 会把基础结构变换到目标朝向，并缓存结果。
	- 备注：当前实现的变换主要基于 `orientation`（`horizontalFacing` 主要用于兼容/预留参数）。
- `BlockPredicate.transform(...)` 会尝试旋转带方向属性的 `IBlockState`（如 `facing`）。

> JSON 加载器当前总是以 `NORTH/UP` 创建基础结构（见 `StructureLoader.DEFAULT_ORIENTATION`）。

## Pattern bounds 与区块加载 fast-fail

`StructurePattern` 现在提供 bounds 与区块加载检查，用于在匹配前快速失败：

- bounds：`minPos` / `maxPos`（由 pattern 覆盖的相对坐标计算得出）
- `isAreaLoaded(world, origin)`：检查 pattern 覆盖的包围盒在世界中是否已加载（不会强制加载区块）

匹配流程中会先调用 `isAreaLoaded(...)`，未加载则直接返回 false，避免：

- 未加载区块导致 predicate 读取到默认状态，从而产生误判
- 在大结构上遍历大量 predicate 引发卡顿（尤其是玩家站在边界、区块频繁加载/卸载时）

相关实现：

- `src/main/kotlin/api/machine/structure/pattern/StructurePattern.kt`
- `src/main/kotlin/impl/machine/structure/TemplateStructure.kt`
- `src/main/kotlin/impl/machine/structure/SliceStructure.kt`

## Template / Slice 的匹配要点

- Template：一次性检查 `pattern.blocks` 中每个 predicate。
- Slice：从起始位置开始最多匹配 `maxCount` 次切片，遇到任一切片不匹配则停止计数，最终要求 `matchCount in [minCount..maxCount]`。
- 两者都会跳过“控制器方块坐标”（controller/origin），该位置由外部逻辑保留与校验，pattern 不应占用控制器格。

## 结构预览（客户端）

结构系统提供了一个“纯数据”的预览模型，用于客户端投影渲染与 BOM 汇总：

- 预览模型 API：`src/main/kotlin/api/machine/structure/preview/StructurePreviewModel.kt`

客户端投影预览的使用方式与按键说明见：

- [`docs/StructurePreview.md`](./StructurePreview.md)

## JSON 两阶段加载

- PreInit：扫描 `config/prototypemachinery/structures/*.json` 并反序列化为 `StructureData` 缓存
- PostInit：把 `StructureData` 转换为 `MachineStructure` 并解析方块引用（此时所有 mod 的方块都已注册）

## See also

- [MachineStructure JSON 配置指南](./StructureJsonGuide.md)
- [StructureLoader 加载特性说明](./StructureLoadingFeatures.md)
