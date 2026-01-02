# MachineType registration

This page documents how **MachineType** registration works in PrototypeMachinery and the relevant load order constraints.

---

Chinese original:

- [`docs/MachineRegistration.md`](../MachineRegistration.md)

## Where can you register?

Two supported paths:

1. **Code (Kotlin/Java)**: build a `MachineType` and enqueue it via `MachineTypeRegisterer.queue(...)`
2. **Scripts (CraftTweaker / ZenScript)**: register through `mods.prototypemachinery.MachineRegistry`

Both paths converge in PreInit and are stored into `MachineTypeRegistryImpl`.

## Key code locations

- Enqueue + processing: `src/main/kotlin/common/registry/MachineTypeRegisterer.kt`
- Registry implementation: `src/main/kotlin/impl/machine/MachineTypeRegistryImpl.kt`
- Script bridge & builders:
  - `src/main/kotlin/integration/crafttweaker/zenclass/ZenMachineRegistry.kt`
  - `src/main/kotlin/integration/crafttweaker/zenclass/ZenMachineTypeBuilder.kt`
  - `src/main/kotlin/integration/crafttweaker/CraftTweakerMachineTypeBuilder.kt`

## Load order (important)

- **PreInit**: `PrototypeMachinery.preInit(...)` calls `MachineTypeRegisterer.processQueue(event)`
  - flushes the queued MachineTypes into `MachineTypeRegistryImpl`

- **Forge Block Registry Event**: `BlockRegisterer` iterates `MachineTypeRegistryImpl.all()`
  - creates and registers one `MachineBlock` per MachineType

Therefore: MachineTypes must be registered/enqueued in PreInit (or earlier), otherwise you will miss the block registration stage.

## Code-side example (Kotlin)

Core usage:

```kotlin
// During mod construction or PreInit
MachineTypeRegisterer.queue(myMachineType)
```

## Script-side example (ZenScript)

Entry: `mods.prototypemachinery.MachineRegistry`

Bundled example script:

- `src/main/resources/assets/prototypemachinery/scripts/examples/machine_registration.zs`

Recommended approach is to reference structures by id and resolve lazily:

```zenscript
#loader preinit

import mods.prototypemachinery.MachineRegistry;

val m = MachineRegistry.create("mymod", "my_machine");
m.name("My Machine");
m.structure("example_complex_machine"); // structure id (lazy resolve)

MachineRegistry.register(m);
```

`CraftTweakerMachineTypeBuilder.structure(String)` resolves from `StructureRegistryImpl` when `MachineType.structure` is first accessed.

## Duplicate IDs and error handling

- `MachineTypeRegistryImpl.register(...)` throws `IllegalArgumentException` on duplicate ids.
- `MachineTypeRegisterer.processQueue(...)` catches the exception and logs it, instead of crashing the game.

## Thread safety

The registry uses concurrent containers (e.g. `ConcurrentHashMap`) for safe access.

However, it is still recommended to respect the lifecycle:

- register in PreInit
- avoid registering new MachineTypes during runtime unless you fully understand the downstream impact (blocks, rendering, UI, ...)

## See also

- [Machine runtime & recipe execution architecture](./MachineLogic.md)
- [Multiblock structure system overview](../Structures.md)
- [CraftTweaker integration](../CraftTweaker.md)
