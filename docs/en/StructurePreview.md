# Structure preview (client)

This document introduces two client-side structure preview features:

1. **In-world projection preview** (`/pm_preview`): renders the structure as a ghost/outline/block-model projection.
2. **GUI structure preview (ModularUI)** (`/pm_preview_ui`): shows materials/BOM with 3D and layer views (optionally with world-scan comparison).

> This is a developer/debugging oriented feature: it is fully client-side rendering, and does not place blocks or modify the world.

---

Chinese original:

- [`docs/StructurePreview.md`](../StructurePreview.md)

## Quick start

### 1) Start projection: `/pm_preview`

Client commands:

- `/pm_preview <structureId> [sliceCount] [distance] [mode]`
- `/pm_preview off` (or `/pm_preview stop`)

Parameters:

- `structureId`: structure id (from JSON `id` or code registration)
- `sliceCount`: optional; overrides the preview slice count for slice structures
- `distance`: optional; max render distance (blocks)
- `mode`: optional; render mode

Implementation:

- `src/main/kotlin/client/preview/StructurePreviewClientCommand.kt`

### 1b) Open GUI: `/pm_preview_ui`

Client command:

- `/pm_preview_ui <structureId> [sliceCount]`

This opens a **read-only** ModularUI screen for structure preview and BOM browsing.

Implementation:

- `src/main/kotlin/client/preview/ui/StructurePreviewUiClientCommand.kt`

### 2) Render modes

- `ghost` (default): translucent blocks (faster)
- `outline`: wireframe outlines
- `both`: ghost + outline
- `block_model` / `block` / `model`: render real block models (slower; best for small structures)

### 3) Lock/rotate orientation (24 poses)

By default the projection follows the player's facing (front/top). You can lock and rotate at any time:

- `R`: lock/unlock projection orientation
- `[` / `]`: rotate (negative/positive)

Rotation axis selection (modifier keys):

- no modifier: Yaw (rotate around UP)
- `Shift`: Pitch (rotate around EAST)
- `Ctrl`: Roll (rotate around SOUTH)

This system is based on `StructureOrientation(front, top)`, hence the **24** orthogonal poses.

Keybinding implementation:

- `src/main/kotlin/client/preview/ProjectionKeyBindings.kt`

## HUD & localization (i18n)

HUD/chat messages use lang keys:

- preview commands: `pm.preview.*`
- projection HUD/chat: `pm.projection.*`
- keybind names: `key.pm.preview.*`

Language files:

- `src/main/resources/assets/prototypemachinery/lang/en_us.lang`
- `src/main/resources/assets/prototypemachinery/lang/zh_cn.lang`

## Performance & limitations

- Large projections can be CPU/GPU heavy, especially with `block_model`.
- Rendering/caching budgets are centralized in `ProjectionConfig`:
  - `src/main/kotlin/client/preview/ProjectionConfig.kt`

The projection expands structure entries/statuses and renders in batches under a budget. A "gradually filling" effect is expected.

## Stress test: huge structure example

A large example structure is included for stress testing:

- JSON: `src/main/resources/assets/prototypemachinery/structures/examples/huge_preview_64x16x64.json`
- Generator: `scripts/generate_huge_structure.py`

It is used to validate loading, preview expansion, and render/cache strategies under extreme size.

## Related code

- World projection manager: `src/main/kotlin/client/preview/WorldProjectionManager.kt`
- Ray marcher (DDA): `src/main/kotlin/client/preview/ProjectionRayMarcher.kt`
- HUD renderer: `src/main/kotlin/client/preview/ProjectionHudRenderer.kt`
- Preview model API: `src/main/kotlin/api/machine/structure/preview/StructurePreviewModel.kt`

---

## GUI structure preview (ModularUI)

The GUI preview aims to be **host-agnostic read-only UI + optional world scan/cache + reusable 3D widget**.

### Main capabilities

- **3D block-model view**: render block models inside the GUI, aiming for world-like rendering.
- **Layer mode**: view a single Y layer with a layer slider.
- **Collapsible menus and clipping**: top/bottom/left panels slide and clip within their rectangles.
- **Wireframe overlay toggle**: compare wireframe boundaries/axes and block models.
- **Optional client world scan**: scan the world at an anchor and produce MATCH/MISMATCH/UNLOADED/UNKNOWN statuses.
  - gated by host config: read-only hosts (e.g. JEI) may disable scanning.

### View & interactions (current behavior)

- Mode toggle: bottom `layer_preview` acts as a toggle.
  - off: 3D view
  - on: Layer view + right-side slider enabled
- Camera (3D):
  - drag: rotate
  - wheel: zoom
  - top `preview_reset`: reset zoom/camera
- Wireframe toggle: bottom `replace_block` button is currently reused as the wireframe overlay toggle.

For detailed layout/interaction specs, see:

- `src/main/resources/assets/prototypemachinery/textures/gui/gui_structure_preview/gui_structure_preview.md`

### World scan and host gate

GUI supports optional client-side world scan to map the `StructurePreviewModel` onto actual world blocks and compute statuses.

- Scan cache: `src/main/kotlin/client/preview/scan/StructurePreviewWorldScanCache.kt`
- Scanner: `src/main/kotlin/client/preview/scan/StructurePreviewWorldScanner.kt`
- Host config: `src/main/kotlin/client/preview/ui/StructurePreviewUiHostConfig.kt`
  - when `allowWorldScan=false`, the UI must not access `mc.world` (JEI/read-only host).

### 3D rendering strategy (implementation notes)

`StructurePreview3DWidget` is designed to render without depending on the real world (JEI-friendly):

- inputs: `StructurePreviewModel` + optional `statusProvider` (from world scan)
- block model building: `BlockRendererDispatcher.renderBlock(...)` fills a `BufferBuilder`, then uploads to VBO
- neighbor culling: builds an in-structure `IBlockAccess` to provide neighbor states to `renderBlock` and get world-like face culling
- performance:
  - group blocks into fixed-size chunks (e.g. $16^3$)
  - incremental VBO build cursor and per-frame render budget
  - `dispose()` releases VBOs when UI closes to avoid GPU memory leaks
- world-like passes:
  - SOLID/CUTOUT: depth test on, depth writes, usually no blending
  - TRANSLUCENT: rendered last with blending, and may disable depthMask when needed

Key files:

- GUI entry: `src/main/kotlin/client/preview/ui/StructurePreviewUiScreen.kt`
- GUI command: `src/main/kotlin/client/preview/ui/StructurePreviewUiClientCommand.kt`
- 3D widget: `src/main/kotlin/client/preview/ui/widget/StructurePreview3DWidget.kt`

---

## GUI textures & resources (slices / spec / atlas)

GUI textures:

- `src/main/resources/assets/prototypemachinery/textures/gui/gui_structure_preview/`

Spec document:

- `.../textures/gui/gui_structure_preview/gui_structure_preview.md`

Optional build-time slicing + runtime atlas pipeline:

- Build-time slicer: `src/main/kotlin/devtools/atlas/GuiSliceGenerator.kt`
  - manifests: `src/main/resources/assets/prototypemachinery/pm_gui_slices/*.json`
  - outputs: `build/generated/gui-slices/assets/.../textures/...`
- Runtime atlas: `src/main/kotlin/client/atlas/PmGuiAtlas.kt`
  - stitches sprites into `textures/gui/pm_gui_atlas.png`
  - runtime index: `assets/prototypemachinery/pm_gui_atlas/<atlasId>.json` (generated by build-time tool)

The structure preview UI currently mostly references stable sliced PNG paths; the atlas pipeline is an optional optimization/organization tool for many small sprites.
