# CraftTweaker (ZenScript) Integration

> Original (Chinese): [`docs/CraftTweaker.md`](../CraftTweaker.md)

PrototypeMachinery exposes a *preview-stage* CraftTweaker/ZenScript API surface for:

- registering `MachineType`s from scripts
- registering machine UIs (`UIRegistry`) and binding UI widgets to script data (`UIBindings`)
- client-side render bindings (`RenderBindings`: Gecko structure / machine-type bindings)
- (optionally) querying/updating hatch configs, depending on what your pack uses

## Where the code lives

- Machine type registration:
  - `src/main/kotlin/integration/crafttweaker/zenclass/ZenMachineRegistry.kt`
  - `src/main/kotlin/integration/crafttweaker/zenclass/ZenMachineTypeBuilder.kt`
  - `src/main/kotlin/integration/crafttweaker/CraftTweakerMachineTypeBuilder.kt`

- Script data container (machine data):
  - `src/main/kotlin/integration/crafttweaker/zenclass/data/ZenMachineData.kt`
  - `src/main/kotlin/api/machine/component/type/ZSDataComponentType.kt`

- UI:
  - `src/main/kotlin/integration/crafttweaker/zenclass/ui/PMUI.kt`
  - `src/main/kotlin/integration/crafttweaker/zenclass/ui/UIRegistry.kt`
  - `src/main/kotlin/integration/crafttweaker/zenclass/ui/UIBindings.kt`

- Render bindings (client side):
  - `src/main/kotlin/integration/crafttweaker/zenclass/render/ZenRenderBindings.kt`
  - `src/main/kotlin/integration/crafttweaker/zenclass/render/ZenGeckoBindingBuilder.kt`

- Example scripts:
  - `src/main/resources/assets/prototypemachinery/scripts/examples/*.zs`

## Machine registration (script side)

Script entrypoint: `mods.prototypemachinery.MachineRegistry`.

High-level flow (matches `ZenMachineRegistry` + `MachineTypeRegisterer`):

1. `MachineRegistry.create(modId, path)` returns a `MachineTypeBuilder`.
2. Configure builder: name / structure / componentTypes / recipe groups.
3. `MachineRegistry.register(builder)` queues the machine type into `MachineTypeRegisterer`.
4. During PreInit, `MachineTypeRegisterer.processQueue(...)` writes into the machine type registry.

### Structure reference (recommended)

The builder supports a lazy structure-by-id reference:

- `builder.structure("example_complex_machine")`

This variant is designed to avoid load-order issues: the structure is resolved from the structure registry when the machine type is first accessed (see `ZenMachineTypeBuilder.structure(structureId: String)` and its backing builder).

## UIRegistry and UIBindings

- `UIRegistry.register(machineId, panel)` / `UIRegistry.registerWithPriority(machineId, panel, priority)`
  - registers a UI panel for a machine id.
  - `priority` controls overriding; higher priority wins.

- `UIBindings.*`
  - binds UI widgets to `ZSDataComponent` keys, enabling script-driven data read/write.

### Runtime JSON (Machine UI Editor export)

Besides building UI via `PMUI` builders, `UIRegistry` also supports registering **runtime JSON** exported from the Machine UI Editor:

- `UIRegistry.registerRuntimeJson(machineId, runtimeJson)`
- `UIRegistry.registerRuntimeJsonWithPriority(machineId, runtimeJson, priority)`

This is implemented in `integration/crafttweaker/zenclass/ui/UIRegistry.kt` via `MachineUiRuntimeJson.parsePanelDefinition(runtimeJson)`.

Current behavior (as implemented):

- unknown widgets are ignored (tolerant parsing)
- `visibleIf` / `enabledIf` conditions are supported via a conditional wrapper
- tabs are supported via `TabContainerDefinition` (minimal runtime implementation)

Recommendations:

- Treat runtime JSON as *tool output*; scripts should mostly just register the JSON and the necessary `UIBindings`.
- For the exact field contract / compatibility rules / supported widget types, refer to the contract doc below.

See: [Machine UI Editor: Runtime JSON contract (current state)](./MachineUiEditorRuntime.md)

## RenderBindings (client-side render bindings)

Script entry point: `mods.prototypemachinery.render.RenderBindings`.

Currently this provides declarative GeckoLib bindings (registering resource locations + simple flags; actual rendering is client-only):

- `RenderBindings.bindGeckoToStructure(machineTypeId, structureId, GeckoBinding)`: bind a model to a specific structure node (recommended; respects structure offsets/slices)
- `RenderBindings.bindGeckoToMachineType(machineTypeId, GeckoBinding)`: bind a model to the whole machine type (legacy / fallback)

### `modelOffset(x, y, z)`: in-game alignment tweaks

`GeckoBinding.modelOffset(x, y, z)` adds an **extra local-space translation** (in blocks; fractional values are allowed) that rotates with the structure/controller orientation (front/top).

- For **structure-bound rendering** (`bindGeckoToStructure`): the structure JSON node `offset` (and slice accumulated offsets) is applied automatically; `modelOffset(...)` is an additional manual tweak on top.
- For **machine-type binding** (`bindGeckoToMachineType`): `modelOffset(...)` is applied directly as the extra tweak.

Example script:

- `src/main/resources/assets/prototypemachinery/scripts/examples/structure_render_top_mid_tail_bindings.zs`

Tip: it’s usually easiest to nail the structure JSON offsets first, then fine-tune with `modelOffset(...)` (multiples of 1/16 = 0.0625 are a common “pixel-grid” step).

## See also

- [MachineType registration](./MachineRegistration.md)
- [UI: default ModularUI + scriptable UIRegistry](./UI.md)
- [Tiered hatches (Item / Fluid / Energy)](./Hatches.md)
