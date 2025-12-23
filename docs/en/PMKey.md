# PMKey system (resource keys)

PMKey (PrototypeMachinery Key) provides a unified abstraction for "stackable resources" as a stable, comparable, and serializable key.

It mainly serves:

- `ResourceStorage` (type-counted storage for items/fluids)
- GUI list display and incremental sync
- NBT persistence (avoid storing complex object graphs directly in NBT)

## Design goals

- **Stable**: the same resource should yield the same key across contexts.
- **Hashable / comparable**: suitable as map keys for fast lookup.
- **Compact**: minimize payload size for sync and NBT.

## Code locations

- API: `src/main/kotlin/api/key/*`
- Implementations: `src/main/kotlin/impl/key/*`

(Exact types and construction entry points should follow the code; this page focuses on conventions.)

## Relationship with storage

In `ResourceStorage`, item/fluid entries are represented as:

- key: identifies **which** item/fluid (including required metadata / NBT identity)
- amount: **how many** (uses `Long` for large capacities)

See:

- [Resource storage (ResourceStorage / EnergyStorage)](./Storage.md)

## Semantics & implementation conventions (items example)

PMKey is fundamentally "type key + count":

- the key (type identity) must be stable, hashable, serializable
- `count` expresses the amount stored, typically `Long`

Using the item implementation `PMItemKeyImpl` (`src/main/kotlin/impl/key/item/PMItemKeyImpl.kt`) as an example:

- `equals/hashCode` are based on the **unique prototype** and intentionally ignore `count`
  - therefore, using a `PMKey` as a Map key means "resource type", not "type + amount".
- when falling back to vanilla `ItemStack` (GUI rendering, capability bridging), `PMItemKeyImpl.get()` clamps `ItemStack.count` to a safe range (roughly `Int.MAX_VALUE / 2`)
  - goal: reduce `Int` overflow and third-party compatibility risks

If you are building large-capacity inventories with both "type-counted" core and "slotted" UI views, also read:

- `docs/Storage.md` (SlottedResourceStorage / ItemResourceStorage / dirty-slot incremental sync)

---

Chinese original:

- [`docs/PMKey.md`](../PMKey.md)
