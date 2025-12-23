# MachineStructure JSON guide

This guide explains how to write structure JSON files under `config/prototypemachinery/structures/`, which are loaded by `StructureLoader` into `MachineStructure`.

---

Chinese original:

- [`docs/StructureJsonGuide.md`](../StructureJsonGuide.md)

## File location

Structure JSON files live in:

- `config/prototypemachinery/structures/`

The loader scans for `.json` files under this directory (implementation: `common/structure/loader/StructureLoader.kt`).

Notes:

- The loader scans **recursively**, so subdirectories are supported.
- If the directory is empty, on first run the mod copies bundled examples into `config/.../structures/examples/`, then scans again.

## JSON schema (StructureData)

The corresponding data class is:

- `src/main/kotlin/common/structure/serialization/StructureData.kt`

### Top-level fields

- `id` (String, required): structure id (globally unique)
- `type` (String, required): structure type, currently:
  - `"template"`: fixed template structure
  - `"slice"`: repeating slice structure
- `offset` (Object, optional, default 0/0/0): offset relative to the controller
- `pattern` (Array, required): list of block predicates
- `children` (Array<String>, optional): child structure ids

#### Reserved fields (current behavior)

- `validators` (Array<String>): currently the JSON loader does **not** deserialize validators into `StructureValidator` (TODO in `StructureLoader`; currently `emptyList()`).
- `pattern[].nbt` (Object): present in `StructurePatternElementData`, but `StructureLoader.convertPattern(...)` currently **does not use** it; predicates are primarily built from `blockId/meta`.

### Pattern elements

Each element:

- `pos`: `{x,y,z}`
- `blockId`: e.g. `"minecraft:iron_block"`
- `meta`: optional, default 0 (used for `getStateFromMeta(meta)`)
- `nbt`: optional (currently unused; see above)

### Slice-only fields

When `type == "slice"`:

- `minCount` (Int, required)
- `maxCount` (Int, required)
- `sliceOffset` (Object, optional): per-layer offset; if omitted the loader uses `0/1/0` (stack upwards)

## Examples

### 1) Template

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

### 2) Slice

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

### 3) Children references

One JSON file defines exactly one structure object.

`tower_base.json`:

```json
{
  "id": "tower_base",
  "type": "template",
  "pattern": [
    { "pos": { "x": 0, "y": 0, "z": 0 }, "blockId": "minecraft:stone" }
  ]
}
```

`tower_complete.json`:

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

Child resolution happens in PostInit. Load order does not require "child first, parent later". Missing child references are logged as warnings and skipped.

## Loading flow (and logs)

1. PreInit: read and cache JSON as `StructureData`
2. PostInit: resolve `blockId/meta`, convert to `MachineStructure`, and register

## Common errors

- `Duplicate structure ID ...`: duplicate ids (later files are ignored)
- `Unknown structure type ...`: unsupported `type`
- `Slice structure ... must have 'minCount' field`: missing slice fields

## See also

- [Structure system overview](./Structures.md)
- [StructureLoader features](../StructureLoadingFeatures.md)
- Bundled example structures: `src/main/resources/assets/prototypemachinery/structures/examples/`
  - Large stress-test example: `huge_preview_64x16x64.json` (can be regenerated via `scripts/generate_huge_structure.py`)
