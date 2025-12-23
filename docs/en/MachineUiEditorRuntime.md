# Machine UI Editor: Runtime JSON integration (current behavior + contract + limits)

> Original (Chinese): [`docs/MachineUiEditorRuntime.md`](../MachineUiEditorRuntime.md)

This document describes how **Runtime JSON exported by the Web Editor (Machine UI Editor)** is interpreted by PrototypeMachinery.

Goals:

- provide a stable, maintainable **contract** (field names, defaults, compatibility)
- document what the current implementation really supports (and common “looks supported but isn’t” pitfalls)
- keep the Web Editor output aligned with the mod-side Kotlin implementation

Source of truth:

- Runtime JSON parser: `src/main/kotlin/impl/ui/runtime/MachineUiRuntimeJson.kt`
- Conditional wrapper widget factory: `src/main/kotlin/client/gui/builder/factory/ConditionalWidgetFactory.kt`
- Tab container widget factory: `src/main/kotlin/client/gui/builder/factory/TabWidgetFactory.kt`
- Binding expression parser: `src/main/kotlin/client/gui/builder/bindingexpr/UiBindingExpr.kt`
- Binding runtime & sync key strategy: `src/main/kotlin/client/gui/builder/UIBindings.kt`

## Implemented entrypoints (current)

### ZenScript registration

- `mods.prototypemachinery.ui.UIRegistry.registerRuntimeJson(machineId, runtimeJson)`
- `mods.prototypemachinery.ui.UIRegistry.registerRuntimeJsonWithPriority(machineId, runtimeJson, priority)`

Implementation: `src/main/kotlin/integration/crafttweaker/zenclass/ui/UIRegistry.kt`

This means the Web Editor can export ZenScript that registers runtime JSON, and it will load in-game.

## Runtime JSON parser behavior (what the mod does)

### Tolerant parsing

The parser is intentionally tolerant:

- unknown `type` values are ignored (they won’t crash the UI)
- several fields have compatible aliases (e.g. `w`/`width`, `h`/`height`)
- nested container children are parsed recursively

There is also a recursion guard:

- maximum nesting depth: `32` (`MAX_NESTING_DEPTH`)

### Tabs are always materialized as a LEFT tab container

Even if your exported UI does not *visually* look like tabs, the parser always builds a `TabContainerDefinition` as the background layer.

Key details from `parsePanelDefinition`:

- canvas size is required via `canvas.width` and `canvas.height`
- widgets are parsed from `widgets[]`
- widgets are partitioned into:
	- **global widgets** (no `tabId`) → rendered on top of everything
	- **tab widgets** (`tabId` present) → put into the corresponding tab content panel

The produced root structure is effectively:

- `PanelDefinition(backgroundTexture = null)`
	- first child: `TabContainerDefinition(tabPosition = "LEFT")`
		- each tab content is a full-size `PanelDefinition` (with optional per-tab background)
	- then: all global widgets

### Tabs: new format vs legacy A/B

Preferred (new) format:

- `options.tabs[]: { id: string, label?: string, texturePath?: string }`
- `options.activeTabId?: string`

Legacy format (still supported):

- `options.backgroundA.texturePath` / `options.backgroundB.texturePath`
- `options.activeBackground: "A" | "B"`

Initial active tab selection order (implementation order):

1. `options.activeTabId`
2. `options.activeBackground`
3. `tabs[0]?.id`
4. fallback: `"A"`

Legacy tab creation is **conditional**:

- the parser tries to only create A/B tabs if they are referenced by widgets or have explicit backgrounds
- but it will always keep at least one fallback tab

### `tabId` only works at the top-level `widgets[]`

The parser reads `tabId` only when `allowTabTag = true`.

In practice:

- **top-level** widgets in `widgets[]` may have `tabId`
- **nested** widgets inside `children[]` do *not* participate in tab partitioning

This avoids ambiguous semantics like “a container is on tab A but has a child on tab B”.

## Conditional fields: `visibleIf` / `enabledIf`

Both `visibleIf` and `enabledIf` are treated as *boolean binding keys or boolean expressions*.

Important: current implementation does **not** implement a hard “don’t render” visibility.
Instead it uses enable/disable as a lightweight mechanism.

`ConditionalWidgetFactory` behavior:

$$
\mathrm{wrapperEnabled} = (\text{visibleIf} \lor \mathrm{true}) \land (\text{enabledIf} \lor \mathrm{true})
$$

Where a missing key is treated as `true`.

## Binding expression syntax (implementation-as-doc)

Expression format:

- `func(arg0;arg1;...)`
- separator is `;`
- nesting is supported: `clamp(norm(foo;0;100);0;1)`

“Is it an expression?” heuristic (in `UIBindings.looksLikeExpr`):

- contains `(` and ends with `)`

If a string does not look like an expression, it is treated as a raw binding key.

### Bool expressions

Supported functions:

- `not(x)`
- `and(a;b;...)`
- `or(a;b;...)`

Unknown functions are treated as raw keys (no crash).

### Double expressions

Supported:

- numeric literal: `0`, `0.5`, `-1`
- `clamp(x;min;max)`
- `norm(x;min;max)`

Notes:

- `norm` returns `0.0` if `max - min == 0.0`
- nested expressions are supported for doubles (the runtime resolves nested expressions recursively)

### Expressions are read-only

Expression-derived bindings do not provide setters; they are always read-only.

## Sync key strategy (avoid ModularUI collisions)

ModularUI requires `(key, id)` to uniquely map to a single SyncHandler type.
The same logical key name may be used as bool/double/string across widgets, so PrototypeMachinery namespaces by type:

- `prototypemachinery:ui_binding:bool:<raw>`
- `prototypemachinery:ui_binding:double:<raw>`
- `prototypemachinery:ui_binding:string:<raw>`

Implementation: `UIBindings.syncKey(...)`.

## Texture path normalization (runtime JSON → ModularUI texture arg)

`MachineUiRuntimeJson` normalizes texture paths into ModularUI resource arguments:

- `gui/gui_controller_a.png` → `prototypemachinery:gui/gui_controller_a`
- `prototypemachinery:textures/gui/foo.png` → `prototypemachinery:gui/foo`
- `mymod:gui/bar` → `mymod:gui/bar`

See: `normalizePmUiTextureArgForRuntime(...)`.

## Stable contract (recommended fields)

### Top-level structure

The parser requires:

- `canvas.width: int`
- `canvas.height: int`

It reads (optional):

- `options` (tabs/background selection)
- `widgets[]`

### Common widget fields

Most widgets use:

- `type: string` (case-insensitive)
- `x: int`, `y: int`
- size fields (varies by type): `w`/`width`, `h`/`height`
- optional `visibleIf: string`, `enabledIf: string`

Tab partitioning:

- `tabId?: string` is only read for top-level entries in `widgets[]`
- missing/empty `tabId` means “global widget”

## Supported widget types (current parser)

The runtime JSON parser supports the following `type` values (case-insensitive; internally `.lowercase()`):

### Containers / layouts (recursive)

- `panel`
- `row`
- `column`
- `grid`
- scroll containers:
	- `scroll_container`
	- `scrollcontainer`
	- `scroll`
	- `scrollarea`

Container notes:

- `panel` supports background aliases: `backgroundTexture`, `backgroundTexturePath`, `background`, `texturePath`
- nested `children[]` are parsed with `allowTabTag = false`

### Leaf widgets

- `text`
	- required: `w`, `h`
	- fields: `text`, `color` (`#RRGGBB` or `#AARRGGBB`, alpha ignored), `shadow` (default `true`), `align` (`left|center|right`)

- `progress`
	- required: `w`, `h`
	- fields: `direction` (`left|right|up|down`), `runTexturePath` / `baseTexturePath`, `progressKey`, `tooltipTemplate`

- `slotGrid` (exported name is commonly `slotGrid`; lowercased it becomes `slotgrid`)
	- required: `cols`, `rows`
	- fields: `slotKey` (default `default`), `startIndex` (default `0`), `canInsert`/`canExtract` (default `true`)

- `button`
	- default size: `27x15`
	- fields: `text`, `actionKey`, `skin`

- `toggle` / `toggleButton` / `toggle_button`
	- default size: `27x15`
	- fields: `skin`, `stateKey`, `textOn`, `textOff`, `textureOn*`, `textureOff*`

- `slider`
	- default size: `100x14`
	- fields: `min`, `max`, `step`, `valueKey`, `horizontal` (default `true`), `skin`

- `textField` / `text_field` / `textfield` / `input_box` / `inputbox`
	- default size: `56x13`
	- fields: `valueKey`, `inputType` (default `string`), `minLong`, `maxLong`, `skin`

- `image`
	- required: size + texture
	- texture field aliases: `texture`, `texturePath`, `path`

- `playerInventory` / `player_inventory`
	- default size: `162x76`

## Known limitations / intentional simplifications

### `visibleIf` vs `enabledIf` are currently equivalent

Both are used as enable-gates, and both affect `wrapper.isEnabled`.
If you need “invisible but still occupies space” vs “visible but not interactive”, the current implementation does not distinguish those.

### Expression language is deliberately tiny

There is intentionally no:

- comparison ops like `eq(...)`, `lt(...)`
- string expressions (concat/format)

Recommended strategy:

- implement complex logic in script-side bindings
- keep expressions for light UI wiring (normalization and simple boolean composition)

### Tabs: LEFT is the "real" implementation

The runtime JSON parser always creates a `TabContainerDefinition(tabPosition = "LEFT")`.
The tab widget factory has a more refined implementation for `LEFT` (uses `PagedWidget`, does not render inactive pages).

Other positions exist as a fallback path (button toggles), but the JSON interpreter does not currently emit them.
