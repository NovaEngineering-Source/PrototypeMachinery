# Unified API entry (PrototypeMachineryAPI)

The unified public API entry point is:

- `src/main/kotlin/api/PrototypeMachineryAPI.kt`

It aggregates runtime registries/managers, for example:

- `machineTypeRegistry` -> `MachineTypeRegistryImpl`
- `structureRegistry` -> `StructureRegistryImpl`
- `recipeManager` -> `RecipeManagerImpl`
- `machineUIRegistry` -> `MachineUIRegistryImpl`

## Client integrations (JEI / HEI)

JEI/HEI integration is not part of `PrototypeMachineryAPI` (it is an optional, client-side integration), but addon authors still need extensible registration points.

This project provides registries that map the **Requirement system** into JEI:

- slot kind -> JEI ingredient group init/set: `JeiIngredientKindRegistry`
- requirement node -> indexable values: `JeiNodeIngredientProviderRegistry`
- requirement type -> renderer (slots/widgets/variants): `JeiRequirementRendererRegistry`
- machine type -> layout: `JeiMachineLayoutRegistry`

Important boundary:

- PrototypeMachinery registries **do not register custom ingredient types into JEI itself**. Addons still need to register ingredient types/helpers/renderers/comparators via the JEI API.

See:

- [JEI / HEI integration (recipe indexing + default UI + addon extension)](../JEI.md)

## Thread safety

Most registries use concurrent containers (e.g. `ConcurrentHashMap`) for safe access.
However, the **lifecycle constraints** (when you are allowed to register vs read) still apply:

- MachineType should be registered during PreInit
- Structure block reference resolving requires PostInit

See:

- [Lifecycle & load order](../Lifecycle.md)

## Key-level IO (PMKey-based Structure IO)

Internally, Structure IO is unified as **key-level**:

- `PMKey<T>` + `Long` counts are the core semantics
- scanning (parallelism calculation) and runtime execution use the same key matching rules
- Forge capabilities (`IItemHandler` / `IFluidHandler`) are only boundary adapters (their `Int` limits require chunking/clamping)

API location:

- `src/main/kotlin/api/machine/component/container/StructureKeyContainers.kt`

Docs:

- [Key-level IO (PMKey-based IO)](./KeyLevelIO.md)

---

Chinese original:

- [`docs/API.md`](../API.md)
