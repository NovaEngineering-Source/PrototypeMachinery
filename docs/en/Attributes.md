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

## Attribute registry (implemented)

PrototypeMachinery provides an authoritative global registry:

- `MachineAttributeRegistry` (`src/main/kotlin/api/machine/attribute/MachineAttributeRegistry.kt`)

The built-in set in `StandardMachineAttributes` is registered into this registry.

Current deserialization behavior:

- NBT deserialization resolves types via `MachineAttributeRegistry.require(id)`.
- Unknown ids are treated as an error (no placeholder fallback).

Addon recommendation:

- Register your custom attribute types during mod init via `MachineAttributeRegistry.register(...)`.

See also:

- [Localization / i18n](./Localization.md)
