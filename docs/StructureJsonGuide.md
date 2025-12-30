# MachineStructure JSON 配置指南

English translation: [`docs/en/StructureJsonGuide.md`](./en/StructureJsonGuide.md)

本指南说明如何在 `config/prototypemachinery/structures/` 下编写 JSON 结构文件，并由 `StructureLoader` 加载为 `MachineStructure`。

## 文件位置

结构 JSON 放置在：

- `config/prototypemachinery/structures/`

加载器会扫描该目录下的 `.json` 文件（实现细节见 `common/structure/loader/StructureLoader.kt`）。

补充说明：

- 加载器会对 `config/prototypemachinery/structures/` **递归扫描**所有 `.json` 文件（支持子目录分类）。
- 若目录为空，首次运行会从资源内复制示例结构到 `config/.../structures/examples/`，然后再次扫描，确保首跑就有可用示例。

## JSON Schema（对应 StructureData）

对应的数据类定义位于：

- `src/main/kotlin/common/structure/serialization/StructureData.kt`

### 顶层字段

- `id`（String，必填）：结构 ID（全局唯一）。
- `type`（String，必填）：结构类型，目前支持：
  - `"template"`：固定模板结构
  - `"slice"`：可重复切片结构
- `offset`（Object，可选，默认 0/0/0）：相对控制器的偏移。
- `pattern`（Array，必填）：由若干“方块谓词”组成的结构模式。
- `children`（Array<String>，可选）：子结构 ID 引用。

#### 预留字段（现状说明）

- `validators`（Array<String>）：已支持。loader 会把字符串按 `ResourceLocation` 解析，并通过 `StructureValidatorRegistry` 创建对应的 `StructureValidator`。
  - 无效 id / 未注册的 validator 会被跳过，并输出 warn（结构仍会继续加载）。
- `pattern[].nbt`（Object）：已支持（见 `StatedBlockNbtPredicate`）。
  - 限制：当 `alternatives` 中存在 NBT 约束时，目前不会对“多个候选 + NBT”做完整匹配；loader 会 warn 并回退为仅使用 base option。

### pattern 元素

每个元素：

- `pos`：`{x,y,z}`
- `blockId`：例如 `"minecraft:iron_block"`
- `meta`：可选，默认 0（用于 `getStateFromMeta(meta)`）
- `nbt`：可选（支持，见上）

### slice 专用字段

当 `type == "slice"` 时需要：

- `minCount`（Int，必填）
- `maxCount`（Int，必填）
- `sliceOffset`（Object，可选）：每一层的偏移；缺省时 loader 使用默认值 `0/1/0`（向上叠层）

## 示例

### 1）Template（固定模板）

```json
{
  "id": "example_simple",
  "type": "template",
  "offset": { "x": 0, "y": 0, "z": 0 },
  "pattern": [
    {
      "pos": { "x": 0, "y": 0, "z": 0 },
      "blockId": "minecraft:iron_block",
      "meta": 0
    }
  ],
  "children": []
}
```

### 2）Slice（切片结构）

```json
{
  "id": "example_slice",
  "type": "slice",
  "offset": { "x": 0, "y": 0, "z": 0 },
  "pattern": [
    {
      "pos": { "x": 0, "y": 0, "z": 0 },
      "blockId": "minecraft:iron_block",
      "meta": 0
    }
  ],
  "minCount": 2,
  "maxCount": 5,
  "sliceOffset": { "x": 0, "y": 1, "z": 0 }
}
```

### 3）children（分层结构引用）

一个 JSON 文件只能定义一个结构对象。

`tower_base.json`：

```json
{
  "id": "tower_base",
  "type": "template",
  "pattern": [
    { "pos": { "x": 0, "y": 0, "z": 0 }, "blockId": "minecraft:stone" }
  ]
}
```

`tower_complete.json`：

```json
{
  "id": "tower_complete",
  "type": "template",
  "pattern": [
    { "pos": { "x": 0, "y": 0, "z": 0 }, "blockId": "minecraft:iron_block" }
  ],
  "children": ["tower_base"]
}
```

> children 的解析是 PostInit 阶段进行的：加载顺序不要求“先 child 后 parent”。若引用缺失，会记录 warn 并跳过该 child。

## 加载流程（与日志行为）

1. PreInit：读取并缓存 JSON 为 `StructureData`
2. PostInit：解析 `blockId/meta`，转换为 `MachineStructure` 并注册

## 常见错误

- `Duplicate structure ID ...`：重复 ID（后续文件会被忽略）
- `Unknown structure type ...`：type 不支持
- `Slice structure ... must have 'minCount' field`：slice 缺字段

## See also

- [结构系统总览](./Structures.md)
- [StructureLoader 加载特性说明](./StructureLoadingFeatures.md)
- 资源内示例结构：`src/main/resources/assets/prototypemachinery/structures/examples/`
  - 压力测试用超大示例：`huge_preview_64x16x64.json`（可用 `scripts/generate_huge_structure.py` 重新生成）