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

#### 旧格式（legacy base block）

以下字段为旧格式（仍支持）：

- `blockId`：例如 `"minecraft:iron_block"`（**现在可选**）
- `meta`：可选，默认 0（用于 `getStateFromMeta(meta)`）
- `alternatives`：可选，额外候选方块列表（任意其一匹配即可）
- `nbt`：可选（支持，见上）

> 注意：当 `alternatives` 中存在 NBT 约束时，目前不会对“多个候选 + NBT”做完整匹配；loader 会 warn 并回退为仅使用 base option。

#### 新格式：predicates（AND 组合）

新增字段：

- `predicates`：`Array`，可选，默认空。表示该坐标上的额外条件，**所有 predicate 会按 AND 组合**。
  - 该字段可以单独使用（此时 `blockId` 可以省略），也可以和 legacy `blockId/meta/nbt` 一起使用（表示“legacy 方块约束 AND predicates 约束”）。

`predicates` 的每一项支持两种写法：

1) 字符串：

```json
"prototypemachinery:not_air"
```

2) 对象：

```json
{ "id": "prototypemachinery:block_id_regex", "pattern": "minecraft:.*_wool" }
```

对象写法里，`id` 也可写为 `type`（别名）；除 `id/type` 外的所有字段都会作为参数传递。

当前内置 predicate（未识别的 id 会被跳过并 warn）：

- `prototypemachinery:any`：总是匹配（通常只用于配合 display）
- `prototypemachinery:not_air`：要求不是空气
- `prototypemachinery:has_tile_entity`：要求方块 state 带 TileEntity
- `prototypemachinery:tile_nbt`：要求 TileEntity NBT 满足约束（浅匹配；需与其他 predicate 组合用于限定方块类型）
  - 参数：`nbt`（Object，键值为字符串，例如 `{ "id": "foo" }`）
- `prototypemachinery:block_id_regex`：要求方块 id 匹配正则（regex 在加载期展开为 Set<Block>，匹配时仅 contains）
  - 参数：`pattern`（String，或别名 `regex`）
  - 注意：使用 `Pattern.matcher(...).matches()`，即 **整串匹配**；想做前缀请写 `minecraft:.*`。
  - loader 会把 `\\:` 规范化为 `:`，因此 `"minecraft\\:.*"` 与 `"minecraft:.*"` 等价。

#### 新格式：display（预览显示独立对象）

新增字段：

- `display`：Object，可选。只影响客户端预览/BOM 分组，不影响结构匹配逻辑。

字段说明：

- `key`：可选，稳定 key；用于预览/BOM 分组（不填时 loader 会用位置派生 key）。
- `blocks`：可选，显示候选列表：`[{"blockId":"minecraft:stone","meta":0}, ...]`
- `blockIdRegex`：可选，显示候选的 regex 列表；会在加载期展开（为了 UI 性能，当前实现最多取前 32 个候选用于显示）。
- `tileNbt`：可选，TileEntity 的 SNBT 字符串（用于预览 TESR best-effort 初始化/渲染）。

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

### 4）predicates（AND 组合）+ 纯 predicates 模式

```json
{
  "id": "example_predicates_only",
  "type": "template",
  "pattern": [
    {
      "pos": { "x": 0, "y": 0, "z": 0 },
      "predicates": [
        "prototypemachinery:not_air",
        { "id": "prototypemachinery:block_id_regex", "pattern": "minecraft:.*_wool" }
      ]
    }
  ]
}
```

### 5）display（预览显示覆盖）+ TileEntity SNBT

```json
{
  "id": "example_display_override",
  "type": "template",
  "pattern": [
    {
      "pos": { "x": 0, "y": 0, "z": 0 },
      "blockId": "minecraft:chest",
      "display": {
        "key": "demo:chest_preview",
        "blocks": [
          { "blockId": "minecraft:chest", "meta": 0 }
        ],
        "tileNbt": "{CustomName:\"Preview Chest\"}"
      }
    }
  ]
}
```

> `tileNbt` 是 best-effort：解析/注入失败会被吞掉以保证 UI 稳定。

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