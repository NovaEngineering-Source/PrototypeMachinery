# Lifecycle & load order

This page helps answer questions like: "Why does this registration/load have to happen in this phase?"

---

Chinese original:

- [`docs/Lifecycle.md`](../Lifecycle.md)

## Main entry point

- `src/main/kotlin/PrototypeMachinery.kt`

## Structure loading (`StructureLoader`)

- PreInit: `StructureLoader.loadStructureData(event)`
  - reads `config/prototypemachinery/structures/*.json`
  - if empty, copies bundled examples into `config/.../structures/examples/`

- PostInit: `StructureLoader.processStructures(event)`
  - resolves `blockId/meta` into actual `BlockState`
  - converts and registers structures

## Machine type registration (`MachineType`)

- PreInit: `MachineTypeRegisterer.processQueue(event)`
  - flushes the MachineType queue from scripts/code into `MachineTypeRegistryImpl`

Later, during Forge block registry events, the mod creates `MachineBlock`s based on already-registered MachineTypes.

## Server stop

- `TaskSchedulerImpl.shutdown()` stops the scheduler and releases resources.

## See also

- [Structure system overview](../Structures.md)
- [Registration pipeline for blocks/items/block entities](../RegistrationPipeline.md)
