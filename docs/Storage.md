# 资源存储（ResourceStorage / EnergyStorage）

English translation: [`docs/en/Storage.md`](./en/Storage.md)

本项目的存储实现主要面向“工业模组常见的大容量容器”场景：

- **按类型计数**（type-based）而非“槽位 + ItemStack 堆叠”
- 数量使用 `Long`，避免 `Int` 溢出
- 支持增量同步：服务端变更 -> 客户端 GUI 只同步变化部分

## 代码位置

- API：
  - `src/main/kotlin/api/storage/ResourceStorage.kt`
  - `src/main/kotlin/api/storage/ObservableResourceStorage.kt`
  - `src/main/kotlin/api/storage/ResourceStorageListener.kt`

- 实现：
  - `src/main/kotlin/impl/storage/ResourceStorageImpl.kt`
  - `src/main/kotlin/impl/storage/ItemResourceStorage.kt`
  - `src/main/kotlin/impl/storage/FluidResourceStorage.kt`
  - `src/main/kotlin/impl/storage/EnergyStorageImpl.kt`

- GUI 同步（ModularUI）：
  - `src/main/kotlin/client/gui/sync/ResourceStorageSyncHandler.kt`
  - `src/main/kotlin/client/gui/sync/ResourceSlotSyncHandler.kt`
  - `src/main/kotlin/client/gui/sync/EnergySyncHandler.kt`

## ResourceStorage：按类型计数

`ResourceStorage` 的核心约束通常是：

- `maxTypes`：最多能存多少“不同类型”（不同 PMKey）
- `maxCountPerType`：每种类型最多能存多少

因此它更像“字典型容器”，而不是传统背包。

### 关键点

- **以 PMKey 作为索引**：同一种资源会合并计数。
- **支持监听器**：变更时通知 UI 同步层。
- **NBT 持久化**：用列表/键值形式保存每一种资源的 key 与数量。
- **pendingChanges**：用于判断“是否需要同步/是否有增量变化”。

> 注意：客户端不应自行从本地存储推导“权威列表”。资源列表以服务端同步为准。

## SlottedResourceStorage：槽位视图（用于 GUI/交互）

为了兼顾“按类型计数的大容量存储”和“传统槽位交互（点格子插入/取出）”，项目提供了 `SlottedResourceStorage` 这一**固定槽位数的视图模型**：

- API：`src/main/kotlin/api/storage/SlottedResourceStorage.kt`
- 核心能力：
  - `slotCount`：固定槽位数（索引稳定，便于 GUI 网格/列表展示）
  - `getSlot(index)`：读取该槽位当前展示的资源键（可能为空）
  - `extractFromSlot(index, amount, simulate)`：从指定槽位提取
  - `drainPendingSlotChanges()`：返回并清空“自上次清理以来发生变化的槽位索引集合”（dirty slots）

> 关键点：槽位视图不要求“一种类型只能占一个槽位”。同一种资源类型可以出现在多个槽位中（用于分段展示数量或提供并行交互入口）。

## ItemResourceStorage：Map 内核 + 虚拟槽位分段

物品存储的当前实现（`src/main/kotlin/impl/storage/ItemResourceStorage.kt`）采用“聚合计数 + 槽位视图”的组合：

- 真实内核：`totals: Map<UniquePMItemKey, Long>`（按类型聚合计数）
- 槽位视图：为每种 key 分配若干槽位，并按单槽容量 `slotCap` 进行分段
  - 总量为 $N$、单槽容量为 $C$ 时，该类型需要的槽位数约为 $\lceil N / C \rceil$
  - 每个槽位展示 `min(C, remaining)` 的数量

为兼容原版 `ItemStack.count`（`Int`）与部分渲染/交互假设，向外生成 `ItemStack` 时会对数量做安全钳制：

- `PMItemKeyImpl.get()`：将 `count` 限制到 `Int.MAX_VALUE / 2`
- `ItemResourceStorage` 同样用该上限限制每槽 `slotCap`（避免溢出/兼容风险）

## 增量同步：dirty-slot（避免每 tick 全量扫描）

`ResourceSlotSyncHandler`（`src/main/kotlin/client/gui/sync/ResourceSlotSyncHandler.kt`）在同步槽位型存储时，会优先走 dirty-slot 增量路径：

- 服务端：存储实现记录发生变化的槽位索引
- 同步层：调用 `drainPendingSlotChanges()` 只刷新/下发这些槽位
- 兜底：若实现未正确记录 dirty slots，同步层会回退为 full sync（保证正确性）

该设计显著降低了“槽位多、tick 频繁”的场景下 GUI 同步的 CPU 与网络开销。

## EnergyStorageImpl：能量存储

能量存储使用独立实现（与 `IEnergyStorage` 兼容）：

- capacity / maxReceive / maxExtract 可动态更新
- 更新容量时会对现有能量进行 clamp（避免超过新容量）

## 与 Hatch 的关系

Hatch（输入/输出/IO）内部持有对应的存储实例：

- Item Hatch -> `ItemResourceStorage`
- Fluid Hatch -> `FluidResourceStorage`
- Energy Hatch -> `EnergyStorageImpl`

并通过 Capability 向外暴露：

- `IItemHandler` / `IFluidHandler` / `IEnergyStorage`

详见：

- [10 阶 Hatch 系统（Item / Fluid / Energy）](./Hatches.md)
