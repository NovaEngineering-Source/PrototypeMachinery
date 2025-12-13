# 10 阶 Hatch 系统（Item / Fluid / Energy）

本项目实现了一个“10 阶梯度”的 Hatch 系统，用于提供机器外设级别的：

- 物品输入/输出/输入输出（INPUT / OUTPUT / IO）
- 流体输入/输出/输入输出（INPUT / OUTPUT / IO）
- 能量输入/输出/输入输出（INPUT / OUTPUT / IO）

并具备：

- 统一的配置注册表（每阶容量/速率等）
- CraftTweaker 侧可热更新配置，并应用到**已加载**的 TileEntity
- 默认 ModularUI GUI（带增量同步）
- Forge Capability 对接

## 代码位置

- 注册：`src/main/kotlin/common/registry/HatchRegisterer.kt`
- 配置：
  - `src/main/kotlin/common/registry/HatchConfigRegistry.kt`
  - `src/main/kotlin/common/registry/HatchConfigUpdateBridge.kt`
- 方块/方块实体：
  - `src/main/kotlin/common/block/hatch/item/*`
  - `src/main/kotlin/common/block/hatch/fluid/*`
  - `src/main/kotlin/common/block/hatch/energy/*`
- GUI：`src/main/kotlin/common/block/hatch/*/*GUI.kt`

## 模式：INPUT / OUTPUT / IO

- INPUT：外界只能向 Hatch 输入
- OUTPUT：外界只能从 Hatch 输出
- IO：同时允许输入与输出

对物品/流体通常通过包装 `IItemHandler` / `IFluidHandler` 限制操作方向。

## Tier（1..10）

每一阶对应一套配置（容量/速率/类型上限等），由 `HatchConfigRegistry` 维护。

### 配置热更新

脚本侧更新配置后：

1. 更新 `HatchConfigRegistry` 中该 tier 的配置
2. 通过 `HatchConfigUpdateBridge` 遍历已加载世界里的 TileEntity
3. 对匹配的 Hatch TE 调用 `applyConfig(...)` 以刷新存储参数

## 无朝向（No Facing）

Hatch 方块不再携带 facing / orientation 的 blockstate，避免出现“输入输出朝向误解”的体验问题。

渲染层面使用统一 blockstate `variants.normal`。

## See also

- [资源存储（ResourceStorage / EnergyStorage）](./Storage.md)
- [CraftTweaker（ZenScript）集成](./CraftTweaker.md)
