# Multiblock structure system (MachineStructure)

The structure system describes a multiblock machine's shape, orientation, and matching logic.

It consists of three parts:

1. **Structure definition API**: `MachineStructure` / `StructurePattern` / `StructureValidator`
2. **Structure registry**: `StructureRegistryImpl` (supports caching and orientation transforms)
3. **JSON loader**: `StructureLoader` (loads structure JSON from config)

---

Chinese original:

- [`docs/Structures.md`](../Structures.md)

## Code locations

- API: `src/main/kotlin/api/machine/structure/*`
- Implementation: `src/main/kotlin/impl/machine/structure/*`
- JSON data model: `src/main/kotlin/common/structure/serialization/StructureData.kt`
- Loader: `src/main/kotlin/common/structure/loader/StructureLoader.kt`

## Orientation & rotation

- A structure stores a **base orientation** (commonly `NORTH/UP`).
- `StructureOrientation(front, top)` defines orientation via **front + top**, which supports up to **24** orthogonal poses.
- `StructureRegistryImpl.get(id, orientation, horizontalFacing)` transforms the base structure into the target orientation and caches results.
  - Note: current transforms primarily use `orientation`. `horizontalFacing` is mostly a compatibility/reserved parameter.
- `BlockPredicate.transform(...)` tries to rotate `IBlockState`s with directional properties (e.g. `facing`).

> The JSON loader currently always constructs the base structure with `NORTH/UP` (see `StructureLoader.DEFAULT_ORIENTATION`).

## Pattern bounds & chunk-loaded fast-fail

`StructurePattern` provides bounds and chunk-loaded checks to fail fast before matching:

- bounds: `minPos` / `maxPos` computed from covered relative positions
- `isAreaLoaded(world, origin)`: checks whether the bounding box is loaded **without forcing chunk loads**

The matching flow calls `isAreaLoaded(...)` first. If the area is not loaded, matching returns false immediately. This avoids:

- predicates reading default states from unloaded chunks and causing false matches
- iterating huge predicate sets and causing lag when chunks near the boundary load/unload frequently

Related code:

- `src/main/kotlin/api/machine/structure/pattern/StructurePattern.kt`
- `src/main/kotlin/impl/machine/structure/TemplateStructure.kt`
- `src/main/kotlin/impl/machine/structure/SliceStructure.kt`

## Template / Slice matching notes

- Template: checks each predicate in `pattern.blocks` once.
- Slice: matches up to `maxCount` layers; stops when any slice fails; then requires `matchCount in [minCount..maxCount]`.
- Both skip the controller/origin position. The controller coordinate is reserved and validated by external logic; patterns should not occupy it.

## Client-side structure preview

The structure system provides a data-only preview model for client projection rendering and BOM summary:

- Preview model API: `src/main/kotlin/api/machine/structure/preview/StructurePreviewModel.kt`

See also:

- [Structure preview (client)](./StructurePreview.md)

## Two-phase JSON loading

- PreInit: scan `config/prototypemachinery/structures/*.json` and deserialize into cached `StructureData`
- PostInit: convert `StructureData` into `MachineStructure`, resolving block references (after blocks from all mods are registered)

## See also

- [MachineStructure JSON guide](./StructureJsonGuide.md)
- [StructureLoader features](../StructureLoadingFeatures.md)
