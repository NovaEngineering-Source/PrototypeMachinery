# Block / item / block-entity registration pipeline

PrototypeMachinery does not put every "complex object" into Forge registries directly. Instead:

- **MachineType**: registered into an internal registry (`MachineTypeRegistryImpl`) during PreInit
- **Machine controller block (`MachineBlock`)**: created and registered during Forge's block registry events, one per MachineType
- **Block entities**: registered centrally (e.g. `MachineBlockEntity`)

---

Chinese original:

- [`docs/RegistrationPipeline.md`](../RegistrationPipeline.md)

## Code locations

- MachineType enqueue/process: `src/main/kotlin/common/registry/MachineTypeRegisterer.kt`
- Machine block registration: `src/main/kotlin/common/registry/BlockRegisterer.kt`
- `MachineBlock`: `src/main/kotlin/common/block/MachineBlock.kt`
- `MachineBlockEntity`: `src/main/kotlin/common/block/entity/MachineBlockEntity.kt`

## Simplified order

1. PreInit: scripts/code enqueue -> `MachineTypeRegisterer.processQueue(event)` registers MachineTypes
2. Block Registry Event: `BlockRegisterer` iterates `MachineTypeRegistryImpl.all()` and registers `MachineBlock`
3. Later item registration events (if any): register `ItemBlock` based on cached `machineBlocks`

## See also

- [Lifecycle & load order](./Lifecycle.md)
- [MachineType registration](./MachineRegistration.md)
