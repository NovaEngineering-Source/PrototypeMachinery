# 结构加载器特性说明

> English translation: [docs/en/StructureLoadingFeatures.md](./en/StructureLoadingFeatures.md)

> 本文以当前实现为准：`src/main/kotlin/common/structure/loader/StructureLoader.kt`。

## 已实现的高级特性

### 1. 循环嵌套检测

加载器会自动检测结构定义中的循环依赖，并抛出详细的错误信息。

#### 示例：循环依赖检测

**错误的配置（会被检测到）：**

`structure_a.json`:
```json
{
  "id": "structure_a",
  "type": "template",
  "children": ["structure_b"],
  "pattern": [...]
}
```

`structure_b.json`:
```json
{
  "id": "structure_b",
  "type": "template",
  "children": ["structure_c"],
  "pattern": [...]
}
```

`structure_c.json`:
```json
{
  "id": "structure_c",
  "type": "template",
  "children": ["structure_a"],  // 循环引用回 structure_a
  "pattern": [...]
}
```

**错误信息：**
```
Circular dependency detected in structure hierarchy: structure_a -> structure_b -> structure_c -> structure_a
```

### 2. 引用复用保证

加载器确保相同 ID 的结构始终使用同一个对象引用，满足引用相等性 (`===`)。

#### 工作原理

当多个结构引用同一个子结构时，它们会共享同一个实例：

```
structure1.children[0] === structure2  // true
structure3.children[0] === structure2  // true
structure1.children[0] === structure3.children[0]  // true
```

#### 示例：引用复用

`base_component.json`:
```json
{
  "id": "base_component",
  "type": "slice",
  "minCount": 1,
  "maxCount": 5,
  "pattern": [
    {"pos": {"x": 0, "y": 0, "z": 0}, "blockId": "minecraft:iron_block", "meta": 0}
  ]
}
```

`machine_variant_a.json`:
```json
{
  "id": "machine_variant_a",
  "type": "template",
  "children": ["base_component"],
  "pattern": [...]
}
```

`machine_variant_b.json`:
```json
{
  "id": "machine_variant_b",
  "type": "template",
  "children": ["base_component"],
  "pattern": [...]
}
```

在这个例子中：
- `machine_variant_a` 和 `machine_variant_b` 都引用 `base_component`
- 两者的 `children[0]` 指向**完全相同的对象实例**
- 修改 `base_component` 的行为会同时影响两个变体

### 3. 两阶段加载机制

加载过程分为两个独立阶段，确保无论 JSON 文件的加载顺序如何，都能正确解析所有引用。

#### 阶段 1：数据缓存
- 扫描并读取所有 JSON 文件
- 反序列化为 `StructureData` 对象
- 存入 `structureDataCache`
- 检测重复 ID

#### 阶段 2：引用解析
- 遍历缓存中的所有结构数据
- 递归解析子结构引用
- 应用循环检测和引用复用
- 创建最终的 `MachineStructure` 实例
- 加入注册队列

### 4. 智能引用解析

解析子结构时按以下优先级查找：

1. **注册表** - 已经完成注册的结构（最高优先级）
2. **实例缓存** - 正在转换过程中的结构（引用复用）
3. **数据缓存** - 尚未转换的结构数据（递归转换）

这确保了：
- ✅ 避免重复创建相同结构
- ✅ 支持任意嵌套深度
- ✅ 检测并拒绝循环依赖
- ✅ 保证引用相等性

### 5. 完整的错误处理

加载器提供详细的错误信息：

| 错误类型 | 错误信息示例 |
|---------|------------|
| 循环依赖 | `Circular dependency detected in structure hierarchy: A -> B -> C -> A` |
| 重复 ID | `Duplicate structure ID 'machine_core' in file: machine_core_v2.json` |
| 缺失引用 | `Child structure 'unknown_component' not found for structure 'main_machine'` |
| 类型错误 | `Unknown structure type: custom_type` |
| 必填字段 | `Slice structure 'component_slice' must have 'minCount' field` |

> 备注：缺失引用与重复 ID 当前不会直接中断启动；会记录日志并尽可能继续加载。

## 最佳实践

### ✅ 推荐做法

1. **模块化设计** - 将常用组件定义为独立的结构文件
2. **清晰命名** - 使用描述性的 ID（如 `energy_input_hatch` 而非 `comp1`）
3. **合理嵌套** - 避免过深的嵌套层次（建议 ≤ 3 层）
4. **复用组件** - 多个结构共享相同的子结构定义

### ❌ 避免做法

1. **循环引用** - 结构 A 引用 B，B 又引用 A
2. **重复 ID** - 多个文件使用相同的结构 ID
3. **无效引用** - 引用不存在的子结构 ID
4. **过深嵌套** - 超过 5 层的嵌套可能影响性能

## 示例：完整的模块化设计

### 基础组件

`components/energy_hatch.json`:
```json
{
  "id": "energy_hatch",
  "type": "slice",
  "minCount": 1,
  "maxCount": 4,
  "pattern": [
    {"pos": {"x": 0, "y": 0, "z": 0}, "blockId": "minecraft:gold_block", "meta": 0}
  ]
}
```

`components/fluid_hatch.json`:
```json
{
  "id": "fluid_hatch",
  "type": "slice",
  "minCount": 0,
  "maxCount": 2,
  "pattern": [
    {"pos": {"x": 0, "y": 0, "z": 0}, "blockId": "minecraft:lapis_block", "meta": 0}
  ]
}
```

### 复合结构

`machines/basic_machine.json`:
```json
{
  "id": "basic_machine",
  "type": "template",
  "children": ["energy_hatch", "fluid_hatch"],
  "pattern": [
    {"pos": {"x": 0, "y": 0, "z": 0}, "blockId": "minecraft:iron_block", "meta": 0},
    {"pos": {"x": 1, "y": 0, "z": 0}, "blockId": "minecraft:iron_block", "meta": 0}
  ]
}
```

在这个设计中：
- `basic_machine` 可以独立使用
- `energy_hatch` 可以独立使用
- `fluid_hatch` 可以独立使用
- **且** `basic_machine.children[0]` 与独立注册的 `energy_hatch` 是**同一个对象**

## 技术细节

### 引用相等性证明

```kotlin
val basicMachine = StructureRegistry.get("basic_machine")
val energyHatch = StructureRegistry.get("energy_hatch")

// 引用相等性成立
assert(basicMachine.children[0] === energyHatch)
```

### 循环检测算法

使用深度优先搜索（DFS）追踪转换路径：
- 每次进入结构转换时，将 ID 加入路径集合
- 检测到路径中已存在的 ID 时，报告循环
- 转换完成后从路径移除，允许同一结构被多次引用

### 性能优化

- **实例缓存** - 避免重复转换相同结构
- **早期返回** - 已缓存的实例直接返回
- **延迟清理** - 所有结构处理完成后统一清理缓存

## 现状与限制

### 1) validators / nbt 字段

- `StructureData.validators` 已支持：loader 会把字符串按 `ResourceLocation` 解析，并通过 `StructureValidatorRegistry` 创建 `StructureValidator`。
  - 无效 / 未注册的 validator 会被跳过，并输出 warn。

内置 validators（无参数）：

- `prototypemachinery:overworld_only`：仅允许主世界（dimension == 0）
- `prototypemachinery:nether_only`：仅允许下界（dimension == -1）
- `prototypemachinery:end_only`：仅允许末地（dimension == 1）
- `prototypemachinery:day_only`：仅允许白天
- `prototypemachinery:night_only`：仅允许夜晚
- `prototypemachinery:clear_weather_only`：仅允许晴天（不下雨/不打雷）

示例：

```json
{
  "validators": [
    "prototypemachinery:overworld_only",
    "prototypemachinery:clear_weather_only"
  ]
}
```

- `StructurePatternElementData.nbt` 已支持：当元素带 `nbt` 时，会使用 `StatedBlockNbtPredicate`。
  - 限制：当 `alternatives` 中存在 NBT 约束时，目前不会对“多个候选 + NBT”做完整匹配；loader 会 warn 并回退为仅使用 base option。

### 2) 缺失引用的处理策略

当 `children` 中出现不存在的结构 ID：

- loader 会记录 warn：`Child structure '...' not found for structure '...'`
- 并将该 child 从 children 列表中移除（继续加载父结构）

### 3) 重复 ID 的处理策略

当出现重复结构 ID：

- loader 会记录 warn：`Duplicate structure ID '...' in file: ...`
- 后续重复定义会被忽略
