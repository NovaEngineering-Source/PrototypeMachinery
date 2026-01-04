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

- `validators` (Array<String>): supported. The loader parses each entry as a `ResourceLocation` and resolves it via `StructureValidatorRegistry`.
  - Invalid / unknown validator ids are skipped with a warning.
- `pattern[].nbt` (Object): supported via `StatedBlockNbtPredicate`.
  - Limitation: NBT constraints on `alternatives` are not fully supported yet; the loader warns and falls back to the base option only.

### Pattern elements

Each element:

- `pos`: `{x,y,z}`

#### Legacy base block format

These legacy fields are still supported:

- `blockId`: e.g. `"minecraft:iron_block"` (**optional now**)
- `meta`: optional, default 0 (used for `getStateFromMeta(meta)`)
- `alternatives`: optional, extra candidates (any-of)
- `nbt`: optional (supported; see above)

Note: NBT constraints on `alternatives` are not fully supported yet; the loader warns and falls back to the base option only.

#### New: `predicates` (AND-composed)

New field:

- `predicates`: Array, optional (default empty). Extra conditions for this position; **all predicates are AND-ed**.
  - You can omit `blockId` and rely on `predicates` only.
  - Or combine legacy `blockId/...` with `predicates` for “legacy constraints AND predicate constraints”.

Each entry supports two JSON forms:

1) String:

```json
"prototypemachinery:not_air"
```

2) Object:

```json
{ "id": "prototypemachinery:block_id_regex", "pattern": "minecraft:.*_wool" }
```

In object form, `type` is an alias of `id`. All other keys are treated as params.

Built-in predicates (unknown ids are skipped with a warning):

- `prototypemachinery:any`: always matches (useful with `display`)
- `prototypemachinery:not_air`: requires non-air
- `prototypemachinery:has_tile_entity`: requires the blockstate to have a TileEntity
- `prototypemachinery:tile_nbt`: requires TileEntity NBT constraints (shallow matching; combine with other predicates)
  - param: `nbt` (Object, string values)
- `prototypemachinery:block_id_regex`: block id regex
  - param: `pattern` (String, or alias `regex`)
  - uses full-string match (`matches()`); for prefix use `minecraft:.*`
  - the loader normalizes `\\:` into `:` (so `"minecraft\\:.*"` equals `"minecraft:.*"`)

#### New: `display` (preview-only override)

New field:

- `display`: Object, optional. Affects preview/BOM grouping only; does not affect matching.

Fields:

- `key`: optional stable key used for preview/BOM grouping
- `blocks`: optional explicit display list: `[{"blockId":"minecraft:stone","meta":0}, ...]`
- `blockIdRegex`: optional regex list expanded at load time (current implementation takes up to 32 representative options for UI)
- `tileNbt`: optional TileEntity SNBT used for preview TESR init/render (best-effort)

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

## Additional examples

### Predicates-only element

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

### Preview display override + TileEntity SNBT

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
