# PMKey 系统（资源键）

English translation: [`docs/en/PMKey.md`](./en/PMKey.md)

PMKey（PrototypeMachinery Key）用于将“可堆叠资源”统一抽象为可比较、可序列化、可长期稳定的键。

它主要服务于：

- `ResourceStorage`（物品/流体的“按类型计数”存储）
- GUI 列表展示与增量同步
- NBT 序列化（避免把对象引用或复杂结构直接塞进 NBT）

## 设计目标

- **稳定**：同一种资源在不同上下文中能够得到相同 key。
- **可哈希/可排序**：便于用 Map 存储与快速查找。
- **尽量小**：用于同步与 NBT 时减少体积。

## 代码位置

- API：`src/main/kotlin/api/key/*`
- 实现：`src/main/kotlin/impl/key/*`

（具体类型与构造入口以代码为准；本页更偏“用途与约定”。）

## 与存储的关系

在 `ResourceStorage` 中，物品/流体条目通常以“资源键 + 数量（Long）”表达：

- key：表示“是哪一种物品/流体”（包含必要的元数据/NBT 标识）
- amount：表示“有多少”（使用 `Long`，便于超大容量）

更多细节见：

- [资源存储（ResourceStorage / EnergyStorage）](./Storage.md)

## 语义与实现约定（以物品为例）

PMKey 的核心定位是“**类型键 + 数量**”。其中：

- key（类型）必须稳定、可哈希、可序列化
- count（数量）用于表达库存/存储中的实际数量，通常为 `Long`

以 `ItemStack` 的 PMKey 实现 `PMItemKeyImpl`（`src/main/kotlin/impl/key/item/PMItemKeyImpl.kt`）为例：

- `equals/hashCode` **只基于 unique key（原型）**，刻意忽略 `count`
	- 因此把 `PMKey` 作为 Map 的 key 时，其语义是“这是什么资源类型”，而不是“类型+数量”的组合键
- 当需要回落为原版 `ItemStack`（例如用于 GUI 渲染或交互桥接）时，`PMItemKeyImpl.get()` 会对 `ItemStack.count` 做安全钳制（上限约为 `Int.MAX_VALUE / 2`）
	- 目的：降低 `Int` 溢出与第三方兼容风险

如果你要基于 PMKey 做“按类型聚合计数 + 槽位视图”的库存（例如 Hatch 仓），建议同时阅读：

- `docs/Storage.md`（SlottedResourceStorage / ItemResourceStorage / dirty-slot 增量同步）
