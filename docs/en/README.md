# PrototypeMachinery Docs (English)

This folder contains English translations of the main project documentation.

- If a page is missing in `docs/en/`, please refer to the Chinese version in `docs/`.
- The code is always the source of truth; docs aim to explain conventions and architecture.

## Index

Core concepts:

- [Public API entry point (PrototypeMachineryAPI)](./API.md)
- [Machine Attributes](./Attributes.md)
- [Machine runtime & recipe execution architecture](./MachineLogic.md)
- [PMKey system (resource keys)](./PMKey.md)
- [Key-level IO (PMKey-based IO)](./KeyLevelIO.md)
- [Resource storage (ResourceStorage / EnergyStorage)](./Storage.md)
- [Task scheduler (TaskScheduler)](./TaskScheduler.md)
- [Lifecycle & load order](./Lifecycle.md)
- [Localization / i18n](./Localization.md)

Registration:

- [MachineType registration](./MachineRegistration.md)
- [Block / item / block-entity registration pipeline](./RegistrationPipeline.md)

Multiblock structures:

- [Structure system overview](./Structures.md)
- [Structure loader features](./StructureLoadingFeatures.md)
- [MachineStructure JSON guide](./StructureJsonGuide.md)
- [Structure preview (client)](./StructurePreview.md)

Modules:

- [Tiered hatch system (10 tiers)](./Hatches.md)

UI:

- [UI: default ModularUI + scriptable UIRegistry](./UI.md)
- [Machine UI Editor: Runtime JSON contract](./MachineUiEditorRuntime.md)

Integrations:

- [CraftTweaker (ZenScript)](./CraftTweaker.md)
- [JEI / HEI integration](./JEI.md)
- [JEI integration internals (maintainers)](./JEI-Internals.md)

Chinese docs index (complete):

- [`docs/README.md`](../README.md)
