# Resource storage (ResourceStorage / EnergyStorage)

This project's storage implementations target the "industrial mod large-capacity container" use-case:

- **type-counted** (by resource type) instead of "slots + ItemStack stacks"
- amounts are `Long` to avoid `Int` overflow
- incremental GUI sync: server changes -> client GUI only receives deltas

## Code locations

API:

- `src/main/kotlin/api/storage/ResourceStorage.kt`
- `src/main/kotlin/api/storage/ObservableResourceStorage.kt`
- `src/main/kotlin/api/storage/ResourceStorageListener.kt`

Implementations:

- `src/main/kotlin/impl/storage/ResourceStorageImpl.kt`
- `src/main/kotlin/impl/storage/ItemResourceStorage.kt`
- `src/main/kotlin/impl/storage/FluidResourceStorage.kt`
- `src/main/kotlin/impl/storage/EnergyStorageImpl.kt`

GUI sync (ModularUI):

- `src/main/kotlin/client/gui/sync/ResourceStorageSyncHandler.kt`
- `src/main/kotlin/client/gui/sync/ResourceSlotSyncHandler.kt`
- `src/main/kotlin/client/gui/sync/EnergySyncHandler.kt`

## ResourceStorage: type-counted

Typical constraints:

- `maxTypes`: max number of distinct resource types (distinct PMKeys)
- `maxCountPerType`: max amount per type

So it behaves more like a dictionary than a traditional inventory.

Key points:

- **PMKey as index**: same resource type is merged into one entry.
- **listeners**: notify UI sync layer on changes.
- **NBT persistence**: store a list/map of key + amount.
- **pendingChanges**: indicates whether incremental changes exist.

> The client should not treat its local snapshot as authoritative; the server is the source of truth.

## SlottedResourceStorage: a slotted view model

To bridge large-capacity type-counted storage with traditional slot interaction, the project provides `SlottedResourceStorage`:

- API: `src/main/kotlin/api/storage/SlottedResourceStorage.kt`
- capabilities:
  - `slotCount`: fixed number of slots (stable indices, good for grids)
  - `getSlot(index)`: read the key shown in that slot (may be empty)
  - `extractFromSlot(index, amount, simulate)`: extract via slot interaction
  - `drainPendingSlotChanges()`: returns-and-clears dirty slot indices since last drain

Important: the slotted view does **not** require a strict "one resource type per slot" mapping.
The same type may appear in multiple slots (segmentation, parallel interaction).

## ItemResourceStorage: map core + virtual slot segmentation

Current item storage (`src/main/kotlin/impl/storage/ItemResourceStorage.kt`) combines:

- core totals: `totals: Map<UniquePMItemKey, Long>`
- slotted view: allocate multiple slots per key, segmenting by `slotCap`
  - total $N$, slot cap $C$ => slots needed is about $\lceil N / C \rceil$
  - each slot shows `min(C, remaining)`

To remain compatible with vanilla `ItemStack.count` (`Int`) and common assumptions:

- `PMItemKeyImpl.get()` clamps counts to about `Int.MAX_VALUE / 2`
- `ItemResourceStorage` also clamps `slotCap` similarly

## Incremental sync: dirty-slot

`ResourceSlotSyncHandler` (`src/main/kotlin/client/gui/sync/ResourceSlotSyncHandler.kt`) prefers dirty-slot deltas:

- server storage records which slot indices changed
- sync layer calls `drainPendingSlotChanges()` and only refreshes those slots
- fallback: if dirty slots are not recorded correctly, sync layer can fall back to full sync to preserve correctness

This significantly reduces CPU/network overhead in high-slot-count, high-frequency tick scenarios.

## EnergyStorageImpl

Energy storage uses a separate implementation (compatible with `IEnergyStorage`):

- capacity / maxReceive / maxExtract can be updated dynamically
- when capacity changes, stored energy is clamped to avoid exceeding new capacity

## Relationship with hatches

Hatches hold the corresponding storage instances internally:

- Item hatch -> `ItemResourceStorage`
- Fluid hatch -> `FluidResourceStorage`
- Energy hatch -> `EnergyStorageImpl`

and expose Forge capabilities:

- `IItemHandler` / `IFluidHandler` / `IEnergyStorage`

See:

- [Hatches system (Item / Fluid / Energy)](../Hatches.md)

---

Chinese original:

- [`docs/Storage.md`](../Storage.md)
