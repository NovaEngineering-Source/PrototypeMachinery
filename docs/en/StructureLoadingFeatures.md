# Structure loader features

> Original (Chinese): [`docs/StructureLoadingFeatures.md`](../StructureLoadingFeatures.md)

This page documents the behavior of the current implementation:

- `src/main/kotlin/common/structure/loader/StructureLoader.kt`

## Implemented features

## 1) Circular dependency detection

The loader detects circular references in the `children` graph during conversion and fails fast with a detailed cycle path.

Implementation note (see `convertToMachineStructure(..., conversionPath)`):

- `conversionPath: MutableSet<String>` tracks the current recursion chain.
- if the current id is already in the set, the loader throws `IllegalStateException`:
  - `Circular dependency detected in structure hierarchy: a -> b -> ... -> a`

## 2) Reference reuse (reference equality)

The loader guarantees that a structure id maps to **one canonical `MachineStructure` instance** during conversion.

Implementation notes:

- `structureInstanceCache: MutableMap<String, MachineStructure>` stores converted instances.
- before converting, the loader checks the cache and returns the cached instance immediately.

This makes reference equality checks (`===`) stable across parents that reference the same child id.

## 3) Two-phase loading

Loading is split into two phases to avoid block registry order issues:

### Phase 1 (PreInit): scan + deserialize only

Method: `StructureLoader.loadStructureData(event: FMLPreInitializationEvent)`

- scans `config/prototypemachinery/structures` recursively for `*.json`
- deserializes JSON to `StructureData` (kotlinx.serialization)
- stores it into `structureDataCache`
- warns on duplicate structure ids and ignores the later duplicates
- if no JSON files are found, copies example structures from resources into `structures/examples/` and re-scans

### Phase 2 (PostInit): resolve blocks + build structures

Method: `StructureLoader.processStructures(event: FMLPostInitializationEvent)`

- converts each cached `StructureData` into a `MachineStructure`
- resolves child references by id (registry/cache/data cache)
- resolves block ids to actual block states (all blocks are registered by PostInit)
- queues structures into `StructureRegisterer` and processes the queue
- clears caches afterwards

## 4) Child resolution strategy

When converting one structure, each child id is resolved in this order:

1. from the structure registry (`StructureRegistryImpl.get(childId)`) — already-registered structures
2. from `structureInstanceCache` — already-converted (or being converted) instances
3. from `structureDataCache` — convert from cached data (recursive)
4. otherwise: warn and drop the missing child from the `children` list

## 5) Duplicate id handling

If multiple JSON files define the same `StructureData.id`:

- the loader logs a warning
- the later definition is ignored

This is enforced in `loadStructureDataFile(...)` by checking `structureDataCache.containsKey(id)`.

## Current limitations / caveats

### Validators

Validators are supported. The loader parses validator ids as `ResourceLocation` and resolves them via `StructureValidatorRegistry`.

- invalid ids are skipped with a warning
- unknown (unregistered) validators are skipped with a warning

### NBT matching

`StructurePatternElementData.nbt` is supported via `StatedBlockNbtPredicate`.

Limitation:

- NBT constraints on `alternatives` are not fully supported yet; the loader warns and falls back to the base option only.

### Controller position overlap guard

When `offset == (0,0,0)`, an element at `(0,0,0)` would overlap the controller position. The loader warns and ignores that pattern element.
