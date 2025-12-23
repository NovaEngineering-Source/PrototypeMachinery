# Tiered hatch system (10 tiers: Item / Fluid / Energy)

PrototypeMachinery implements a **10-tier hatch system** to provide machine peripherals for:

- Item INPUT / OUTPUT / IO
- Fluid INPUT / OUTPUT / IO
- Energy INPUT / OUTPUT / IO

It also includes:

- a unified per-tier configuration registry (capacity / rate / limits)
- CraftTweaker hot-reload of hatch configs, applied to **already loaded** tile entities
- default ModularUI GUIs (with incremental sync)
- Forge capability integration

---

Chinese original:

- [`docs/Hatches.md`](../Hatches.md)

## Code locations

- Registration: `src/main/kotlin/common/registry/HatchRegisterer.kt`
- Configuration:
  - `src/main/kotlin/common/registry/HatchConfigRegistry.kt`
  - `src/main/kotlin/common/registry/HatchConfigUpdateBridge.kt`
- Blocks / block entities:
  - `src/main/kotlin/common/block/hatch/item/*`
  - `src/main/kotlin/common/block/hatch/fluid/*`
  - `src/main/kotlin/common/block/hatch/energy/*`
- GUIs: `src/main/kotlin/common/block/hatch/*/*GUI.kt`

## Modes: INPUT / OUTPUT / IO

- INPUT: external access can only insert into the hatch
- OUTPUT: external access can only extract from the hatch
- IO: both insert and extract are allowed

For items/fluids this is typically enforced by wrapping `IItemHandler` / `IFluidHandler` and restricting the allowed operations.

## Tiers (1..10)

Each tier maps to a configuration set (capacity / rate / type limits, ...), maintained by `HatchConfigRegistry`.

### Hot-reload config updates

When scripts update hatch configs:

1. update the tier entry in `HatchConfigRegistry`
2. `HatchConfigUpdateBridge` iterates loaded worlds / tile entities
3. matching hatch TEs call `applyConfig(...)` to refresh storage parameters

## No facing

Hatch blocks do not carry a facing/orientation in blockstates.

This avoids user confusion about "input/output direction" at the block-facing level.

Rendering uses the unified `variants.normal`.

## See also

- [Resource storage](./Storage.md)
- [CraftTweaker integration](../CraftTweaker.md)
