# Machine runtime & recipe execution architecture

This page describes the **runtime skeleton** of a machine and the **recipe execution** architecture in PrototypeMachinery.

Docs follow the current implementation. If you find a mismatch, treat the code as the source of truth.

---

Chinese original:

- [`docs/MachineLogic.md`](../MachineLogic.md)

## Code locations

- Types and runtime instances:
  - `src/main/kotlin/api/machine/*`
  - `src/main/kotlin/impl/machine/*`
  - Block entity: `src/main/kotlin/common/block/entity/MachineBlockEntity.kt`

- Component system (ECS variant):
  - `src/main/kotlin/api/machine/component/*`
  - `src/main/kotlin/impl/machine/component/*`

- Recipes and processes:
  - `src/main/kotlin/api/recipe/*`
  - `src/main/kotlin/impl/recipe/*`

## From `MachineType` to `MachineInstance`

- `MachineType`: the definition of "what this machine is" (structure, component types, metadata, ...)
- `MachineBlockEntity.initialize(machineType)`: creates and binds a `MachineInstanceImpl`
- Formed state: when a multiblock match succeeds, the instance updates its formed status, used by rendering and runtime permission checks.

## Component system (`MachineComponent` / `MachineSystem`)

- Components are declared and constructed via `MachineComponentType`.
- `MachineSystem` provides tick-driving and system ordering (dependency relations -> topological sorting).
- Some components can have `system == null`, meaning they are data-only and not ticked.

## Recipe model (`MachineRecipe` / `RecipeProcess`)

- `MachineRecipe`: the recipe definition, primarily a list of requirements grouped by requirement type.
- `RecipeProcess`: a runtime process instance.
  - Holds a seed to allow reproducible randomness.
  - Has a dedicated attribute overlay (`attributeMap` overlay per process).

## Requirement layer (transactional requirement systems)

`RecipeRequirementType` is bound to a `RecipeRequirementSystem`.

Core API:

- `src/main/kotlin/api/recipe/requirement/component/system/RecipeRequirementSystem.kt`
  - `start(process, component): RequirementTransaction`
  - `acquireTickTransaction(process, component): RequirementTransaction` (optional; for tickable requirements)
  - `onEnd(process, component): RequirementTransaction`

### Transaction semantics (important)

- **Obtaining a transaction (start/tick/end) may produce immediate side effects**, e.g.
  - reserving items
  - draining energy
  - writing temporary state

- The caller must decide based on `RequirementTransaction.result`:
  - `Success`: must call `commit()`
  - `Blocked`: must call `commit()` as well
    - and the system must guarantee "no side effects" or "recoverable side effects" semantics to avoid deadlocks
  - `Failure`: must call `rollback()`

- The executor must treat a set of requirements within the same stage as **one atomic stage**.
  If any requirement returns `Blocked` / `Failure`, it must rollback **all transactions already acquired in this stage**.

Default executor implementation:

- `src/main/kotlin/impl/machine/component/system/FactoryRecipeProcessorSystem.kt`
  - stages: START -> (TICK...)* -> END
  - collects transactions per stage; commits all on success; rolls back (reverse order) on failure/blocked
  - stable ordering (by type id) to keep behavior reproducible and tests deterministic

This layer is the main extension point for complex behaviors (input/output, chance, multipliers, candidates, ...).

## Requirement overlay (process-level overrides)

Each `RecipeProcess` can carry an overlay; requirement execution resolves the "effective component" before running.

- Entry: `src/main/kotlin/impl/recipe/requirement/overlay/RecipeRequirementOverlay.kt`
- Overlay components: `impl/recipe/process/component/RecipeOverlayProcessComponent*`

Typical uses:

- Apply different consumption multipliers / filters per process instance
- Provide independent parameter views for parallel processes

## Process components / systems

`RecipeProcess` also supports a component system.
Each `RecipeProcessComponentType` may bind a process system, executed each machine tick (pre/tick/post).

- `FactoryRecipeProcessorSystem` calls `tickProcessComponents(process, Phase.*)` per tick
- Lifecycle helper component:
  - `impl/recipe/process/component/RecipeLifecycleStateProcessComponent*` (e.g. flags like started)

## Recipe indexing

To avoid scanning all recipes frequently, the project uses indexing:

- `IRecipeIndexRegistry` / `RecipeIndexRegistry`
- `RequirementIndexFactory` builds indexes for each requirement type

## See also

- [Multiblock structure system overview](../Structures.md)
- [Machine Attributes](./Attributes.md)
- [Task scheduler](./TaskScheduler.md)
