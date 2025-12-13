# 多方块结构系统（MachineStructure）

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
- `StructureRegistryImpl.get(id, orientation, facing)` 会把基础结构变换到目标朝向，并缓存结果。
- `BlockPredicate.transform(...)` 会尝试旋转带方向属性的 `IBlockState`（如 `facing`）。

> JSON 加载器当前总是以 `NORTH/UP` 创建基础结构（见 `StructureLoader.DEFAULT_ORIENTATION`）。

## JSON 两阶段加载

- PreInit：扫描 `config/prototypemachinery/structures/*.json` 并反序列化为 `StructureData` 缓存
- PostInit：把 `StructureData` 转换为 `MachineStructure` 并解析方块引用（此时所有 mod 的方块都已注册）

## See also

- [MachineStructure JSON 配置指南](./StructureJsonGuide.md)
- [StructureLoader 加载特性说明](./StructureLoadingFeatures.md)
