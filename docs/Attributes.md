# 属性系统（Machine Attributes）

English translation: [`docs/en/Attributes.md`](./en/Attributes.md)

本项目的“属性系统”用于把各种倍率/效率/并行度等数值统一抽象出来，并支持在不同层级叠加（机器层基线 + 进程层 overlay）。

## 组成

- `MachineAttributeType`：属性定义（`ResourceLocation id` + 本地化名称）
- `MachineAttributeInstance`：运行时数值（`base` + `modifiers` → `value`）
- `MachineAttributeModifier`：修改器（携带 `operation` 与可选 `adder` 追踪来源）
- `MachineAttributeMap`：属性容器（通常挂在 MachineInstance / RecipeProcess）

## 修改器运算顺序

统一顺序：

1. `ADDITION`
2. `MULTIPLY_BASE`
3. `MULTIPLY_TOTAL`

对应默认实现：

- 加法：`current + amount`
- 基础乘法：`current + base * amount`
- 总乘法：`current * (1 + amount)`

## 两层 Map：machine baseline + process overlay

- 机器层（baseline）：`MachineAttributeMapImpl`
- 进程层（overlay）：`OverlayMachineAttributeMapImpl(parent = owner.attributeMap)`

overlay 允许每个 `RecipeProcess` 在机器基线基础上叠加额外修改器，并且互不影响。

示例：

- machine `PROCESS_SPEED = 0.85`
- processA 叠加 `MULTIPLY_TOTAL amount=0.5`（即 x1.5）

则：

- machine：`0.85`
- processA：`0.85 * 1.5 = 1.275`
- processB：仍为 `0.85`

## NBT 序列化

序列化辅助：`impl/machine/attribute/MachineAttributeNbt.kt`

- `MachineAttributeMapImpl`：全量持久化（base + modifiers）
- `OverlayMachineAttributeMapImpl`：只持久化 *local changes*（local modifiers + base override）

### `adder` 的序列化限制

`MachineAttributeModifier.adder` 目前主要用于调试/追踪：

- 写入 NBT：保存 `adder.toString()`
- 读取 NBT：恢复为 `String`

不保证反序列化后仍是原始对象。

## TODO：属性注册表仍为临时实现

当前反序列化解析 `MachineAttributeType` 主要依赖 `StandardMachineAttributes.getById(...)`。

这是一种临时“注册表”方案：

- 仅覆盖内置属性
- 第三方属性 roundtrip 时会退化为 `UnknownMachineAttributeType(id)`（仍能计算/显示 id，但不保证能与原 type instance 完全一致）

后续应提供一个真正的全局属性注册表，以支持第三方属性注册与稳定反序列化。

另见：[本地化（Localization / i18n）](./Localization.md)
