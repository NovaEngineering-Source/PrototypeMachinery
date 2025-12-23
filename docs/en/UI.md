# UI: default ModularUI + scriptable UIRegistry

PrototypeMachinery has two main UI paths:

1. **Default built-in UI**: based on ModularUI, provides default screens for machines/peripherals.
2. **Script UI**: CraftTweaker registers UI definitions through `UIRegistry`, and can bind data via `UIBindings`.

There is also a more tooling/debug-oriented UI:

3. **Structure Preview UI (ModularUI, client read-only)**: opened via `/pm_preview_ui`, used for BOM/material listing plus 3D/layer preview (optional world-scan comparison).

---

Chinese original:

- [`docs/UI.md`](../UI.md)

## Code locations

- Default UI (example: hatches):
  - `src/main/kotlin/common/block/hatch/*/*GUI.kt`
  - `src/main/kotlin/client/gui/sync/*`
  - `src/main/kotlin/client/gui/widget/*`

- Script UI:
  - `src/main/kotlin/integration/crafttweaker/zenclass/ui/PMUI.kt`
  - `src/main/kotlin/integration/crafttweaker/zenclass/ui/UIRegistry.kt`
  - `src/main/kotlin/integration/crafttweaker/zenclass/ui/UIBindings.kt`

- Runtime JSON (exported by Machine UI Editor): interpreter & builders
  - `src/main/kotlin/impl/ui/runtime/MachineUiRuntimeJson.kt`
  - `src/main/kotlin/client/gui/builder/UIBindings.kt`
  - `src/main/kotlin/client/gui/builder/bindingexpr/UiBindingExpr.kt`
  - `src/main/kotlin/client/gui/builder/factory/LayoutWidgetFactory.kt`
  - `src/main/kotlin/client/gui/builder/factory/InteractiveWidgetFactory.kt`
  - `src/main/kotlin/client/gui/builder/factory/ConditionalWidgetFactory.kt`
  - `src/main/kotlin/client/gui/builder/factory/TabWidgetFactory.kt`

- UI registry implementation:
  - `src/main/kotlin/impl/ui/registry/MachineUIRegistryImpl.kt`

- Structure preview UI (ModularUI, read-only):
  - Command: `src/main/kotlin/client/preview/ui/StructurePreviewUiClientCommand.kt`
  - Screen assembly: `src/main/kotlin/client/preview/ui/StructurePreviewUiScreen.kt`
  - 3D view widget: `src/main/kotlin/client/preview/ui/widget/StructurePreview3DWidget.kt`
  - Host gate (allow world scan, etc): `src/main/kotlin/client/preview/ui/StructurePreviewUiHostConfig.kt`

## Sync principles (important)

- **The server is authoritative**: ModularUI sync should reflect server-side state.
- The client UI should not "rebuild" resource lists and overwrite server sync results.

(This is also the prerequisite for stable incremental sync in `ResourceStorage`.)

## Script UI: two entry points (builders vs runtime JSON)

Currently, script UI can be built in two primary ways:

1. **Builders (`PMUI`)**: build UI with a builder API in ZenScript (good for handwritten/programmatic UIs).
2. **Runtime JSON (Machine UI Editor export)**: register exported runtime JSON in ZenScript; the mod parses it at runtime and builds a ModularUI screen (good for tooling/visual editors).

Runtime JSON registration entry points live in `UIRegistry` (ZenScript):

- `UIRegistry.registerRuntimeJson(...)`
- `UIRegistry.registerRuntimeJsonWithPriority(...)`

For runtime parsing and compatibility strategies, see:

- `src/main/kotlin/impl/ui/runtime/MachineUiRuntimeJson.kt`

Important: the "contract" of runtime JSON (supported widget types, tabs/conditions/binding expressions, etc.) is defined by the implementation.

- See: [Machine UI Editor: Runtime JSON contract](./MachineUiEditorRuntime.md)

## Bindings (`UIBindings`) and expression keys

Script UI can bind data through `UIBindings`.

An "expression key" feature was added recently (for bool/double bindings). Syntax examples:

- `not(key)`, `and(a;b)`, `or(a;b)`
- `norm(value;min;max)`, `clamp(value;min;max)`

Expressions can be nested. Parameters are separated by `;`.

Implementation:

- Expression parsing: `client/gui/builder/bindingexpr/UiBindingExpr.kt`
- Binding creation and syncKey rules: `client/gui/builder/UIBindings.kt`

Notes:

- Expression bindings are currently **read-only** (they compose/map existing bindings).
- ModularUI sync keys are internally separated by type (bool/double/string) to avoid conflicts when different types reuse the same key name.

## Conditional (`visibleIf` / `enabledIf`) and Tabs

Both runtime JSON and builders support:

- `visibleIf` / `enabledIf`
- Tabs (`tabId` + `options.tabs` / legacy A/B backgrounds)

Current semantics in this project:

- `visibleIf` and `enabledIf` both end up in an enable gate (`isEnabled = visible && enabled`).
  - In other words, they behave closer to "disabled / non-interactive" and do not strictly guarantee "not visible and not rendered".
- Tabs are built as a `TabContainer` + one content panel per tab.
  - For nested containers, internal `tabId` is stripped during export/parse to avoid complex nested-tab semantics.

Implementation:

- Conditional wrapper: `client/gui/builder/factory/ConditionalWidgetFactory.kt`
- Tabs building: `client/gui/builder/factory/TabWidgetFactory.kt`
- Runtime JSON tabs/legacy compat: `impl/ui/runtime/MachineUiRuntimeJson.kt`

## Common pitfall: default Panel background bleeding through (`MC_BACKGROUND`)

### Symptom

- Your UI renders your own PNG, but in transparent areas (e.g. left tab strip / rounded corners / cutouts) you see an **opaque tiled vanilla-like background**.
- This is **not** the vanilla container's semi-transparent dark overlay (`drawDefaultBackground()`), and not part of your PNG.

### Root cause

ModularUI `ModularPanel` uses the `PANEL` theme. ModularUI sets its default background as:

- `IThemeApi.PANEL` default theme background = `GuiTextures.MC_BACKGROUND`
- `GuiTextures.MC_BACKGROUND` = `modularui:gui/background/vanilla_background` (tiled texture)

When building script UIs, if a `PanelDefinition` does **not** specify `backgroundTexture` (e.g. your root panel only calls `setSize()` but not `setBackground()`), the resulting `ModularPanel` falls back to the theme default background, which "bleeds" through transparent regions.

### This project's fix strategy (without modifying ModularUI)

We explicitly disable the default fallback background at build time: if `PanelDefinition.backgroundTexture` is null, set:

- `background(IDrawable.EMPTY)`

This overrides the theme background while rendering nothing.

Fix locations:

- `src/main/kotlin/client/gui/UIBuilderHelper.kt`
  - `buildPanel(...)`: when `bgPath == null`, call `panel.background(IDrawable.EMPTY)`
- `src/main/kotlin/client/gui/builder/factory/LayoutWidgetFactory.kt`
  - `buildNestedPanel(...)`: nested panels also set `IDrawable.EMPTY` when no background is specified

### Notes

- This is a **behavior change**: previously "no background" showed ModularUI's default (`MC_BACKGROUND`), now it becomes "fully transparent / draw nothing".
  - If you need the default background, set it explicitly in scripts/builders.
- `IDrawable.EMPTY` is different from `IDrawable.NONE`:
  - `EMPTY`: explicitly override and draw nothing
  - `NONE`: often means "no hover/overlay" and fall back to normal logic

## Localization (i18n) and comments

Default GUI text comes mainly from two sources:

1. **Language files (needs translation)**: button tooltips, fixed labels, etc.
2. **Dynamic runtime text (not translated by this mod)**: e.g. fluid names (`FluidStack.localizedName`) and formatted numbers.

Language files:

- English: `src/main/resources/assets/prototypemachinery/lang/en_us.lang`
- Chinese: `src/main/resources/assets/prototypemachinery/lang/zh_cn.lang`

### Structure projection preview i18n

Structure projection preview (HUD/chat/commands) uses these namespaces:

- `/pm_preview`: `pm.preview.*`
  - e.g. `pm.preview.started` / `pm.preview.stopped` / `pm.preview.unknown_structure`

`/pm_preview_ui` (GUI preview) reuses `pm.preview.*` messages for consistency (e.g. unknown structure).

- Projection HUD/chat: `pm.projection.*`
  - e.g. `pm.projection.hud.orientation_status`, `pm.projection.chat.locked`
- Keybind names: `key.pm.preview.*`
  - `key.pm.preview.lock_orientation`
  - `key.pm.preview.rotate_positive`
  - `key.pm.preview.rotate_negative`

If keybind keys are missing, the Controls screen shows the raw key string. It is recommended to keep both languages complete.

### Shared hatch GUI keys

These keys are shared by multiple hatch GUIs (including `FluidHatchGUI` and `FluidIOHatchGUI`):

- `prototypemachinery.gui.hatch.auto_input`: tooltip for auto-input toggle
- `prototypemachinery.gui.hatch.auto_output`: tooltip for auto-output toggle
- `prototypemachinery.gui.hatch.clear`: tooltip for clear internal storage

(Optional/reserved)

- `prototypemachinery.gui.hatch.input` / `prototypemachinery.gui.hatch.output`: labels for INPUT/OUTPUT

### Dynamic text in fluid hatch GUIs (no translation needed)

Fluid hatch GUIs display dynamic text:

- Fluid name: `FluidStack.localizedName` (provided by the fluid/mod itself)
- Amount: formatted by `NumberFormatUtil` (compact label + full tooltip)

Therefore this mod does not need (and does not try) to override per-fluid name translations.

## See also

- [Resource storage](./Storage.md)
- [CraftTweaker integration](../CraftTweaker.md)
- [Machine UI Editor Runtime JSON contract](./MachineUiEditorRuntime.md)
- [Structure preview (projection / GUI)](./StructurePreview.md)

## GUI texture specs & slicing/atlas pipeline (structure preview)

Structure preview GUI textures and spec docs:

- Texture directory: `src/main/resources/assets/prototypemachinery/textures/gui/gui_structure_preview/`
- Spec doc (layout/interaction/resource naming):
  - `src/main/resources/assets/prototypemachinery/textures/gui/gui_structure_preview/gui_structure_preview.md`

For stitching many small textures into a `TextureMap` (reducing bind calls), this project also provides:

- Build-time slicer: `src/main/kotlin/devtools/atlas/GuiSliceGenerator.kt`
- Slice manifests: `src/main/resources/assets/prototypemachinery/pm_gui_slices/*.json`
- Runtime atlas: `src/main/kotlin/client/atlas/PmGuiAtlas.kt`

## GUI texture specs (Machine UI / gui_states)

Machine UI Editor and runtime UI support `gui_states` texture templates for some widgets (9-slice, dividers, etc).

- Spec doc: `src/main/resources/assets/prototypemachinery/textures/gui/gui_states/gui_states.md`
