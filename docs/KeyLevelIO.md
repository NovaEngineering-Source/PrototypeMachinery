# Key-level IO（基于 PMKey 的 IO）

本项目的结构 IO（Structure IO）在内部已统一迁移为 **key-level** 交互：以 `PMKey<T>` + `Long` 数量作为核心数据模型，而不是频繁构造/比较 `ItemStack` / `FluidStack`。

This document describes the **key-level** IO model used internally by PrototypeMachinery: `PMKey<T>` + `Long` counts, instead of stack-level `ItemStack` / `FluidStack` churn.

## 目标 / Goals

- **性能**：减少 `ItemStack`/`FluidStack` 的构造、复制与 NBT 比较。
- **一致性**：扫描（parallelism 计算）与执行期使用同一套 key 语义，避免“扫描忽略 tag / 执行考虑 tag”这类偏差。
- **可扩展**：Addon 可以面向稳定的 key-level API 实现自定义容器/存储。

## API 位置 / Where is the API

相关接口位于：

- `src/main/kotlin/api/machine/component/container/StructureKeyContainers.kt`

主要接口：

- `StructureItemKeyContainer`
- `StructureFluidKeyContainer`

它们是 **结构组件（StructureComponent）** 层级的容器视图：在结构成型/刷新时由结构解析生成，并存放在 `StructureComponentMap` 中。

## 基本语义 / Core semantics

### 以 Key + Long 为准

- Item：`PMKey<ItemStack>` + `Long`（数量）
- Fluid：`PMKey<FluidStack>` + `Long`（mB 等计量）

这些接口提供形如：

- `insert(key, amount, mode): Long`
- `extract(key, amount, mode): Long`

返回值为**实际插入/提取的数量**（`Long`）。

### TransactionMode（EXECUTE / SIMULATE）

- `TransactionMode.SIMULATE`：只测算能插入/提取多少，不产生实际副作用。
- `TransactionMode.EXECUTE`：真实执行。

注意：扫描并行约束一般使用 `SIMULATE`；配方执行使用 `EXECUTE`。

## unchecked 方法（rollback 专用）

接口同时提供 unchecked 版本（命名可能为 `insertUnchecked` / `extractUnchecked`）：

- 设计目的：**事务 rollback** 时恢复容器状态。
- 语义：忽略 PortMode（输入/输出）限制等“外部规则”，只做“把状态恢复回去”这件事。

重要：unchecked 不是给普通逻辑“绕过规则”用的。

## capability（Forge IItemHandler/IFluidHandler）适配策略

内部以 `Long` 计数运算，但 Forge capability 通常是 `Int` 数量（例如 `ItemStack.count`、`IFluidHandler.fill/drain`）。因此 cap-backed 容器需要在边界做分块/限幅：

- 统一采用 `Int.MAX_VALUE / 2` 作为安全上限（避免溢出、也避免某些实现对极端值的未定义行为）。
- 大于该上限的插入/提取会被拆分为多个 chunk 循环完成。

这意味着：

- **对外 cap 只是兼容层**；如果你需要真正的大数与零分配语义，请使用 storage-backed（key-level storage）实现。

## 推荐用法 / Recommended usage

- 扫描/并行约束：只使用 `insert/extract(..., SIMULATE)`，不要 materialize stack。
- 执行/事务：
  - commit 阶段用 `insert/extract(..., EXECUTE)`
  - rollback 阶段用 `insertUnchecked/extractUnchecked`（同样建议 EXECUTE）
