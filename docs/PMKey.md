# PMKey 系统（资源键）

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

- API：`src/main/kotlin/api/pmkey/*`
- 实现：`src/main/kotlin/impl/pmkey/*`

（具体类型与构造入口以代码为准；本页更偏“用途与约定”。）

## 与存储的关系

在 `ResourceStorage` 中，物品/流体条目通常以“资源键 + 数量（Long）”表达：

- key：表示“是哪一种物品/流体”（包含必要的元数据/NBT 标识）
- amount：表示“有多少”（使用 `Long`，便于超大容量）

更多细节见：

- [资源存储（ResourceStorage / EnergyStorage）](./Storage.md)
