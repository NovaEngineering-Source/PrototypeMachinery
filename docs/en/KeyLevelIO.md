# Key-level IO (PMKey-based IO)

PrototypeMachinery internally unified Structure IO as **key-level** IO:
use `PMKey<T>` + `Long` counts, instead of repeatedly constructing/comparing `ItemStack` / `FluidStack`.

(Chinese version is the canonical, more detailed explanation; this is the English translation.)

## Goals

- **Performance**: reduce `ItemStack`/`FluidStack` allocations, copying, and NBT comparisons.
- **Consistency**: scanning (parallelism calculation) and runtime execution use the same key semantics.
- **Extensibility**: addons can implement custom containers/storages against stable key-level APIs.

## Where is the API

Interfaces live in:

- `src/main/kotlin/api/machine/component/container/StructureKeyContainers.kt`

Main interfaces:

- `StructureItemKeyContainer`
- `StructureFluidKeyContainer`

They are **StructureComponent**-level container views created when a structure is formed/refreshed, and stored in `StructureComponentMap`.

## Core semantics

### Key + Long

- Item: `PMKey<ItemStack>` + `Long`
- Fluid: `PMKey<FluidStack>` + `Long` (mB, etc.)

The APIs look like:

- `insert(key, amount, mode): Long`
- `extract(key, amount, mode): Long`

Return value is the **actually inserted/extracted** amount.

### TransactionMode (EXECUTE / SIMULATE)

- `TransactionMode.SIMULATE`: compute only, no side effects.
- `TransactionMode.EXECUTE`: apply side effects.

Typical usage:

- scanning/parallelism constraints: `SIMULATE`
- recipe execution: `EXECUTE`

## Unchecked methods (rollback-only)

Some APIs provide unchecked variants such as `insertUnchecked` / `extractUnchecked`:

- purpose: transactional rollback (restore container state)
- semantics: ignore PortMode (input/output) and other external rules; only restore state

Unchecked methods are **not** intended for bypassing rules in normal logic.

## Forge capability adapter strategy (Int boundary)

Internally amounts are `Long`, but Forge capabilities are usually `Int`-based.
So capability-backed containers must chunk/clamp on the boundary:

- use `Int.MAX_VALUE / 2` as a safe per-chunk maximum
- split larger operations into multiple chunks

This implies:

- capabilities are compatibility adapters; for true large-number/zero-allocation behavior, prefer storage-backed key-level implementations.

## Recommended usage

- scanning/constraints: only `insert/extract(..., SIMULATE)`, avoid materializing stacks
- execution/transactions:
  - commit: `insert/extract(..., EXECUTE)`
  - rollback: `insertUnchecked/extractUnchecked` (also typically `EXECUTE`)

---

Chinese original:

- [`docs/KeyLevelIO.md`](../KeyLevelIO.md)
