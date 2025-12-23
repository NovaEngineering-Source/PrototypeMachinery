# PrototypeMachinery Project Overview (English)

> Original (Chinese): [`PROJECT_OVERVIEW.md`](./PROJECT_OVERVIEW.md)

This document provides a high-level overview for maintainers and contributors. It intentionally stays “map-like”; detailed design notes live in `docs/`.

## 1. Recent notable changes (aligned with current code)

Highlights from the latest large refactors / additions:

- **Structure Projection Preview (client)**: supports `/pm_preview`, HUD hints, localization, and **24 orientations** (front + top) with lock/rotate.
  - See: [`docs/StructurePreview.md`](./docs/StructurePreview.md)

- **Structure Preview UI (ModularUI)**: a read-only GUI preview (`/pm_preview_ui`) that can show materials/BOM, includes a **3D view** (block model rendering) and a **layer/slice** mode.
  - Includes smooth dt-based folding animations, wireframe overlay toggle, and an optional client-side world-scan compare (host-gated).
  - See: [`docs/StructurePreview.md`](./docs/StructurePreview.md)
  - UI texture spec (layout/interaction/naming):
    - `src/main/resources/assets/prototypemachinery/textures/gui/gui_structure_preview/gui_structure_preview.md`

- **GUI textures (sliced assets + optional runtime atlas)**: migrated preview GUI textures from a monolithic spritesheet to sliced per-component textures with stable paths. Also provides an optional runtime GUI atlas (TextureMap + Stitcher) to reduce bind overhead.
  - Runtime atlas: `src/main/kotlin/client/atlas/PmGuiAtlas.kt`
  - Build-time slicer: `src/main/kotlin/devtools/atlas/GuiSliceGenerator.kt` + `src/main/resources/assets/prototypemachinery/pm_gui_slices/*.json`

- **Structure match fast-fail**: `StructurePattern` exposes bounds (`minPos`/`maxPos`) and `isAreaLoaded(...)` to avoid matching against unloaded chunks.

- **Transactional requirements**: recipe requirements are executed via a `RequirementTransaction` model (`start / tick / end`), enabling atomic rollback on failure/blocking.

- **Requirement Overlay (per-process override)**: supports attaching an overlay to a `RecipeProcess`, resolving “effective requirement components” before execution.

- **Stress-test structure sample**: a huge structure JSON example + generator script for performance testing.

## 📚 Docs are split by topic (start here)

Main docs live in `docs/`.

- Docs index: [`docs/README.md`](./docs/README.md)
- English docs index: [`docs/en/README.md`](./docs/en/README.md)

## 2. Topic index (details live in `docs/`)

To avoid duplicating the topic docs, this section is a navigation map:

- Attributes: [`docs/Attributes.md`](./docs/Attributes.md)
- Machine runtime & recipes: [`docs/MachineLogic.md`](./docs/MachineLogic.md)
- Multiblock structures: [`docs/Structures.md`](./docs/Structures.md)
  - JSON guide: [`docs/StructureJsonGuide.md`](./docs/StructureJsonGuide.md)
  - Loader features: [`docs/StructureLoadingFeatures.md`](./docs/StructureLoadingFeatures.md)
- Structure preview (world projection / GUI): [`docs/StructurePreview.md`](./docs/StructurePreview.md)
- MachineType registration: [`docs/MachineRegistration.md`](./docs/MachineRegistration.md)
- CraftTweaker integration: [`docs/CraftTweaker.md`](./docs/CraftTweaker.md)
- UI (default + script UIRegistry): [`docs/UI.md`](./docs/UI.md)
- JEI / HEI integration: [`docs/JEI.md`](./docs/JEI.md)
- Task scheduler: [`docs/TaskScheduler.md`](./docs/TaskScheduler.md)
- Public API entry: [`docs/API.md`](./docs/API.md)
- PMKey (resource keys): [`docs/PMKey.md`](./docs/PMKey.md)
- Resource storage: [`docs/Storage.md`](./docs/Storage.md)
- Tiered hatches: [`docs/Hatches.md`](./docs/Hatches.md)
- Registration pipeline: [`docs/RegistrationPipeline.md`](./docs/RegistrationPipeline.md)
- Lifecycle & load order: [`docs/Lifecycle.md`](./docs/Lifecycle.md)

## 3. “30 seconds” code entry points

1. Mod lifecycle entry: `src/main/kotlin/PrototypeMachinery.kt`
2. Structure loading: `common/structure/loader/StructureLoader.kt`
3. MachineType registration: `common/registry/MachineTypeRegisterer.kt` + `impl/machine/MachineTypeRegistryImpl.kt`
4. Machine block registration: `common/registry/BlockRegisterer.kt`
5. UI override chain: `impl/ui/registry/MachineUIRegistryImpl.kt` + `integration/crafttweaker/zenclass/ui/*`

6. **PMKey / inventory semantics (typed counts + slotted views)**:
   - Concepts & conventions: `docs/PMKey.md`
   - Storage implementation & syncing (SlottedResourceStorage / dirty-slot): `docs/Storage.md`

## 4. Maintainer notes

- **Docs policy**: the root-level docs are navigation; topic details live in `docs/`.
- **First-run structure examples**: if `config/prototypemachinery/structures/` is empty, examples are copied from `assets/.../structures/examples/` into `config/.../structures/examples/`.
- **Structure file organization**: nested folders are encouraged (e.g. `structures/components/`), as the loader scans recursively.

- **Kotlin / API discipline**:
  - the project uses Kotlin `explicitApi()` (see `build.gradle.kts`), so public API must declare visibility and types.
  - the root package uses `src/main/kotlin/package.kt` to unify root declarations; avoid excessively deep package nesting.

## 5. Future extension ideas

Based on current implementation, potential future work:

1. **More expressive structure JSON**
   - validators: the schema includes `validators`, but loader support is TODO (see `StructureLoader`)
   - pattern NBT: schema includes `pattern[].nbt`, but it is currently ignored

2. **A validator library**
   - e.g. `HeightValidator`, `BiomeValidator`, `NeighborValidator`

3. **More pattern predicate types**
   - currently only `StatedBlockPredicate` is demonstrated
   - possible extensions: tags, sets, NBT constraints, etc.

4. **Stronger GUI ↔ component system coupling**
   - assemble machines via `MachineComponentType` (energy/items/fluids)
   - auto-generate UI based on components

5. **Better structure preview UX**
   - richer build-assist UI (BOM panel, missing blocks summary, quick-copy structure id)
   - improve localization consistency and reduce hard-coded strings

## 6. Summary

The project already provides a fairly complete, extensible architecture; adding new gameplay/business logic can largely be done by filling in features on top of the existing skeleton.
