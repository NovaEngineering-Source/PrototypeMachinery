# JEI / HEI Integration (indexing + default UI + addon extension)

> Original (Chinese): [`docs/JEI.md`](../JEI.md)

This document explains how PrototypeMachinery integrates with JEI/HEI (JEI 4.16, MC 1.12.2), and how addon authors can hook new ingredient types (beyond `VanillaTypes`, e.g. gases, aspects).

## Big picture: indexing vs UI

JEI needs two things for a recipe page:

1. **Indexing (`IIngredients`)** — what are the inputs/outputs (for search and lookup).
2. **Rendering (`IRecipeLayout`)** — what slots exist, where they are, and what they display.

PrototypeMachinery keeps them decoupled:

- Indexing is produced by `PMMachineRecipeWrapper#getIngredients(IIngredients)`.
- The visible page (slots/widgets) is produced by the category + layout + renderer pipeline.

## Core pipeline (how it works)

### 1) Requirements → renderable nodes

A recipe stores requirements as:

- `MachineRecipe.requirements: Map<RecipeRequirementType<*>, List<RecipeRequirementComponent>>`

A requirement renderer (`PMJeiRequirementRenderer<C>`) can:

- split a component into one or multiple `PMJeiRequirementNode<C>`
- expose variants / a default variant
- declare JEI slots (position/size/kind/index/role)
- build additional ModularUI widgets (text, borders, arrows, etc.)

Registration: `JeiRequirementRendererRegistry`.

### 2) Layout decides *where* nodes go

A machine layout (`PMJeiMachineLayoutDefinition`) places nodes/slots.

Built-in default:

- `integration/jei/layout/DefaultJeiMachineLayout.kt`

The default layout also has an auto-placement fallback for nodes that are not explicitly placed (useful for addon requirements), based on `renderer.defaultVariant`.

Registration: `JeiMachineLayoutRegistry`.

`PMJeiPlugin` ensures a default layout exists and registers one example layout (`ExampleRecipeProcessorHatchesJeiLayout`) if not already overridden.

### 3) Slot kind maps a slot to a JEI ingredient type

Slots use an extensible kind id:

- `JeiSlotKind(id: ResourceLocation)`

For vanilla kinds:

- `JeiSlotKinds.ITEM` → item group handler
- `JeiSlotKinds.FLUID` → fluid group handler

For addon kinds:

- define a new `JeiSlotKind` id (e.g. `myaddon:ingredient/gas`)
- register a `PMJeiIngredientKindHandler<T>` that:
  - `init(...)` creates/initializes the JEI group slots
  - `set(...)` sets values for those slots

Registration: `JeiIngredientKindRegistry`.

### 4) Node ingredient provider decides what gets indexed

Renderers focus on UI declarations; **providers** decide the *values* to expose for JEI indexing and display:

- `PMJeiNodeIngredientProvider<C, T>` converts a node into a list of displayed/indexed `T`.

Registration: `JeiNodeIngredientProviderRegistry`.

### 5) Wrapper writes into `IIngredients`

`integration/jei/wrapper/PMMachineRecipeWrapper.kt`:

- iterates `recipe.requirements`
- uses the renderer to split nodes
- uses the provider to obtain displayed values
- groups them by `kindHandler.ingredientType`
- calls `ingredients.setInputLists(...)` / `ingredients.setOutputLists(...)`

If this step is wrong/missing, JEI search (`U`/`R`) will not find the recipe.

## Addon checklist (minimal steps)

### A) Register your custom ingredient type to JEI itself

PrototypeMachinery registries do **not** register custom ingredient types into JEI.

In your addon JEI plugin, you must:

- declare an `IIngredientType<T>`
- register helper/renderer so JEI can render/compare/name/tooltips

Only after JEI recognizes your type do the PM registries make sense.

### B) Create a new `JeiSlotKind`

Choose a stable `ResourceLocation` id.

### C) Register a `PMJeiIngredientKindHandler<T>`

This handler adapts your type into JEI `IRecipeLayout` groups.

### D) Register a node provider for your requirement type

Implement and register `PMJeiNodeIngredientProvider<C, T>` so:

- your requirement nodes become displayed values
- those values are written into `IIngredients` by the wrapper

### E) Register a renderer for the requirement type

Implement and register `PMJeiRequirementRenderer<C>` so:

- nodes are split and declared as slots/widgets
- the layout has something to place

### F) (Optional) Register machine layouts / decorators / fixed-slot providers

- Layout: ensure your nodes are placed nicely.
- Decorators: progress arrow, duration text, etc.
- Fixed-slot providers: show clickable values not directly tied to recipe requirements.

## Notes grounded in current code

- Builtins are registered defensively via `PMJeiBuiltins.ensureRegistered()` (both in plugin and wrapper) so wrappers can be queried early.
- `PMJeiPlugin.registerIngredients` registers a custom energy ingredient type (searchable energy IO). The code intentionally uses the `IIngredientType` overload to avoid JEI creating a different internal ingredient type instance (which would break wrappers).

## Key source index

- JEI plugin entry: `src/main/kotlin/integration/jei/PMJeiPlugin.kt`
- Indexing write: `src/main/kotlin/integration/jei/wrapper/PMMachineRecipeWrapper.kt`
- Main category: `src/main/kotlin/integration/jei/category/PMMachineRecipeCategory.kt`
- Default layout: `src/main/kotlin/integration/jei/layout/DefaultJeiMachineLayout.kt`
- Registries:
  - `src/main/kotlin/integration/jei/registry/JeiIngredientKindRegistry.kt`
  - `src/main/kotlin/integration/jei/registry/JeiNodeIngredientProviderRegistry.kt`
  - `src/main/kotlin/integration/jei/registry/JeiRequirementRendererRegistry.kt`
  - `src/main/kotlin/integration/jei/registry/JeiMachineLayoutRegistry.kt`

- ZenScript fixed slot providers: `src/main/kotlin/integration/crafttweaker/zenclass/jei/FixedSlotProviders.kt`
