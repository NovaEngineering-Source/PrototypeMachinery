# CraftTweaker (ZenScript) Integration

> Original (Chinese): [`docs/CraftTweaker.md`](../CraftTweaker.md)

PrototypeMachinery exposes a *preview-stage* CraftTweaker/ZenScript API surface for:

- registering `MachineType`s from scripts
- registering machine UIs (`UIRegistry`) and binding UI widgets to script data (`UIBindings`)
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

## See also

- [MachineType registration](./MachineRegistration.md)
- [UI: default ModularUI + scriptable UIRegistry](./UI.md)
- [Tiered hatches (Item / Fluid / Energy)](./Hatches.md)
