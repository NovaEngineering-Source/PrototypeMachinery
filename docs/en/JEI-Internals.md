# JEI Integration: Internals (maintainer notes)

> Original (Chinese): [`docs/JEI-Internals.md`](../JEI-Internals.md)

This page is aimed at maintainers / addon authors. It indexes the entire `src/main/kotlin/integration/jei` package and summarizes responsibilities and the high-level data flow.

## High-level data flow

- **Indexing**: the recipe wrapper writes inputs/outputs into JEI `IIngredients`, enabling search and lookups.
- **Rendering**: the category uses the *layout* to declare slots and uses providers/renderers to populate JEI ingredient groups.
- **Extensibility points**:
  - Ingredient kind (item/fluid/energy/custom)
  - Node ingredient provider (node → values)
  - Requirement renderer (split nodes, declare slots/widgets)
  - Machine layout (place nodes/slots)
  - Decorator (extra UI: progress, duration text, ...)
  - Fixed slot provider (node-less slots with fixed values)

## Concepts quick reference

### Requirement node vs JEI slot

- **Requirement node**: a node derived from a recipe requirement (input/output/per-tick, etc.).
- **JEI slot**: an interactive slot in JEI GUI (click/focus/tooltip unit).
  - most slots correspond to one node
  - *fixed slots* are node-less and obtain values from a provider

### Kind / handler / provider / renderer

- **Kind**: an abstract ingredient kind (not limited to `VanillaTypes`).
- **Kind handler**: adapts a `JeiSlotKind` to JEI ingredient groups (init/set).
- **Node ingredient provider**: converts a requirement node into displayed/indexable values.
- **Requirement renderer**: splits requirements into nodes and declares slots/variants/widgets.

### Fixed slot providers

Use for catalysts, mould hints, and other clickable things that are not part of recipe requirements.

## File index

> Paths are relative to the project root.

### Plugin entry

- `src/main/kotlin/integration/jei/PMJeiPlugin.kt`
  - JEI plugin entry point (ingredient types, categories, recipes).

### Runtime (materialize layout plan)

- `src/main/kotlin/integration/jei/runtime/JeiPanelRuntime.kt`
  - Builds runtime slots/widgets from layout plan; caches fixed-slot values.

- `src/main/kotlin/integration/jei/runtime/JeiModularPanelRuntime.kt`
  - ModularUI panel runtime adapter for JEI usage.

- `src/main/kotlin/integration/jei/runtime/JeiEmbeddedModularUiInputBridge.kt`
  - Input bridge for embedded ModularUI screens inside JEI.

### Category / Wrapper (JEI API glue)

- `src/main/kotlin/integration/jei/category/PMMachineRecipeCategory.kt`
  - Main recipe category; initializes groups and populates values (including fixed slots).

- `src/main/kotlin/integration/jei/category/PMStructurePreviewCategory.kt`
  - Category for structure preview recipes.

- `src/main/kotlin/integration/jei/wrapper/PMMachineRecipeWrapper.kt`
  - Writes ingredients into JEI indexing (`IIngredients`).

- `src/main/kotlin/integration/jei/wrapper/PMStructurePreviewWrapper.kt`
  - Wrapper for structure preview.

### Layout

- `src/main/kotlin/integration/jei/layout/DefaultJeiMachineLayout.kt`
  - Default layout with auto-placement fallback.

- `src/main/kotlin/integration/jei/layout/ExampleRecipeProcessorHatchesJeiLayout.kt`
  - Example layout for hatch-based recipe processor.

#### Script-driven layout

- `src/main/kotlin/integration/jei/layout/script/ScriptJeiLayoutSpec.kt`
  - Immutable, data-driven layout spec and rules.

- `src/main/kotlin/integration/jei/layout/script/ScriptJeiMachineLayoutDefinition.kt`
  - Executes `ScriptJeiLayoutSpec` onto a real layout builder.

### Registries

- `src/main/kotlin/integration/jei/registry/JeiIngredientKindRegistry.kt`
  - Registry of ingredient kinds used by slots/handlers.

- `src/main/kotlin/integration/jei/registry/JeiNodeIngredientProviderRegistry.kt`
  - Registry mapping requirement type → node ingredient provider.

- `src/main/kotlin/integration/jei/registry/JeiRequirementRendererRegistry.kt`
  - Registry mapping requirement type → renderer.

- `src/main/kotlin/integration/jei/registry/JeiMachineLayoutRegistry.kt`
  - Registry mapping machineId → layout definition.

- `src/main/kotlin/integration/jei/registry/JeiDecoratorRegistry.kt`
  - Registry for decorators (progress arrow, duration text, etc.).

- `src/main/kotlin/integration/jei/registry/JeiFixedSlotProviderRegistry.kt`
  - Provider registry for fixed (node-less) slots.

### Builtins

- `src/main/kotlin/integration/jei/builtin/PMJeiBuiltins.kt`
  - Built-in registrations for vanilla item/fluid/energy/etc.

#### Builtin providers

- `src/main/kotlin/integration/jei/builtin/ingredient/VanillaItemNodeIngredientProvider.kt`
- `src/main/kotlin/integration/jei/builtin/ingredient/VanillaFluidNodeIngredientProvider.kt`

#### Builtin kind handlers

- `src/main/kotlin/integration/jei/builtin/ingredient/VanillaItemKindHandler.kt`
- `src/main/kotlin/integration/jei/builtin/ingredient/VanillaFluidKindHandler.kt`

#### Builtin requirement renderers

- `src/main/kotlin/integration/jei/builtin/requirement/ItemRequirementJeiRenderer.kt`
- `src/main/kotlin/integration/jei/builtin/requirement/FluidRequirementJeiRenderer.kt`
- `src/main/kotlin/integration/jei/builtin/requirement/EnergyRequirementJeiRenderer.kt`
- `src/main/kotlin/integration/jei/builtin/requirement/ParallelismRequirementJeiRenderer.kt`

#### Builtin decorators

- `src/main/kotlin/integration/jei/builtin/decorator/ProgressArrowJeiDecorator.kt`
- `src/main/kotlin/integration/jei/builtin/decorator/RecipeDurationTextJeiDecorator.kt`

### API (for addons)

- `src/main/kotlin/integration/jei/api/JeiRecipeContext.kt`
  - Runtime context for provider/renderer decisions.

- `src/main/kotlin/integration/jei/api/decorator/PMJeiDecorator.kt`
- `src/main/kotlin/integration/jei/api/ingredient/PMJeiIngredientKindHandler.kt`

(See the Chinese page for the full list; this English version focuses on navigation.)
