# Machine Attributes

This project uses a unified **Machine Attributes** system to represent numeric factors such as speed/efficiency/parallelism, and to support stacking at different layers:

- **machine baseline** (per `MachineInstance`)
- **process overlay** (per `RecipeProcess`)

---

Chinese original:

- [`docs/Attributes.md`](../Attributes.md)

## Building blocks

- `MachineAttributeType`: attribute definition (`ResourceLocation id` + localization key)
- `MachineAttributeInstance`: runtime value (`base` + `modifiers` → computed `value`)
- `MachineAttributeModifier`: modifier (with an `operation`, and an optional `adder` to trace the source)
- `MachineAttributeMap`: attribute container (typically attached to `MachineInstance` / `RecipeProcess`)

## Modifier evaluation order

The project uses a fixed order:

1. `ADDITION`
2. `MULTIPLY_BASE`
3. `MULTIPLY_TOTAL`

Default semantics:

- Addition: `current + amount`
- Multiply base: `current + base * amount`
- Multiply total: `current * (1 + amount)`

## Two-layer map: baseline + overlay

- Machine baseline: `MachineAttributeMapImpl`
- Process overlay: `OverlayMachineAttributeMapImpl(parent = owner.attributeMap)`

An overlay allows each `RecipeProcess` to apply extra modifiers **without affecting other processes**.

Example:

- machine baseline `PROCESS_SPEED = 0.85`
- process A adds `MULTIPLY_TOTAL amount=0.5` (i.e. ×1.5)

Then:

- machine: `0.85`
- process A: `0.85 * 1.5 = 1.275`
- process B: still `0.85`

## NBT serialization

Serialization helper:

- `src/main/kotlin/impl/machine/attribute/MachineAttributeNbt.kt`

Rules:

- `MachineAttributeMapImpl`: persists everything (base + modifiers)
- `OverlayMachineAttributeMapImpl`: persists only **local changes** (local modifiers + base override)

### Limitation: `adder`

`MachineAttributeModifier.adder` is currently for debugging/tracing:

- When writing NBT: stores `adder.toString()`
- When reading NBT: restores it as a `String`

It does **not** guarantee a lossless round-trip back to the original object.

## TODO: the attribute registry is a temporary implementation

Today, NBT deserialization mostly resolves attribute types via:

- `StandardMachineAttributes.getById(...)`

This behaves like a temporary built-in "registry":

- It only covers built-in attributes.
- Third-party attributes may round-trip as `UnknownMachineAttributeType(id)`.
  - The id can still be computed/shown, but you may not get the exact original type instance back.

Future direction: provide a proper global attribute registry for stable third-party registrations and deserialization.

See also:

- [Localization / i18n](./Localization.md)
