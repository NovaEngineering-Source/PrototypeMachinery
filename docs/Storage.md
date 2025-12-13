# 资源存储（ResourceStorage / EnergyStorage）

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
