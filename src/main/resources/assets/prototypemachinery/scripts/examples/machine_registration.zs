#loader preinit

/*
 * PrototypeMachinery - Example Machine Registration Script
 * 机器注册示例脚本
 * 
 * Place this file in: scripts/prototypemachinery/
 * 将此文件放置在：scripts/prototypemachinery/
 * 
 * This script demonstrates how to register custom machines using the
 * PrototypeMachinery CraftTweaker API.
 * 此脚本演示如何使用 PrototypeMachinery 的 CraftTweaker API 注册自定义机器。
 */

import mods.prototypemachinery.MachineRegistry;

// Example 1: Simple Machine (using structure ID reference)
// 示例 1：简单机器（使用结构 ID 引用）
val simpleMachine = MachineRegistry.create("prototypemachinery", "simple_machine");
simpleMachine.name("Simple Machine");
simpleMachine.structure("example_simple_machine");  // Reference structure by ID (recommended)
// 通过 ID 引用结构（推荐方式，避免加载顺序问题）

MachineRegistry.register(simpleMachine);


// Example 2: Slice Machine (Variable Height)
// 示例 2：切片机器（可变高度）
val sliceMachine = MachineRegistry.create("prototypemachinery", "slice_machine");
sliceMachine.name("Expandable Tower");
sliceMachine.structure("example_slice_machine");  // Lazy loading ensures structure is available

MachineRegistry.register(sliceMachine);


// Example 3: Complex Machine with Advanced Features
// 示例 3：具有高级功能的复杂机器
val complexMachine = MachineRegistry.create("prototypemachinery", "complex_processor");
complexMachine.name("Complex Processor");
complexMachine.structure("example_complex_machine");
// complexMachine.addComponentType(ComponentTypes.RECIPE_PROCESSOR);
// complexMachine.addComponentType(ComponentTypes.ITEM_CONTAINER);
// complexMachine.addComponentType(ComponentTypes.ENERGY_CONSUMER);

MachineRegistry.register(complexMachine);


// Example 4: Modular Machine with Child Structures
// 示例 4：具有子结构的模块化机器
val modularMachine = MachineRegistry.create("prototypemachinery", "modular_machine");
modularMachine.name("Modular Machine");
modularMachine.structure("example_parent_with_child");  // This structure references child_structure

MachineRegistry.register(modularMachine);


/*
 * Best Practices / 最佳实践:
 * 
 * 1. Naming Conventions / 命名规范:
 *    - Use descriptive IDs: "advanced_processor" not "proc1"
 *    - Use snake_case for IDs
 *    - Use proper namespace to avoid conflicts
 * 
 * 2. Structure References / 结构引用:
 *    - Structure JSON files must exist in config/prototypemachinery/structures/
 *    - Structure IDs must match exactly (case-sensitive)
 *    - Use structure("id") for lazy loading (recommended)
 *    - Structures are loaded before CraftTweaker scripts, so references are always safe
 *    - 结构 JSON 文件必须存在于 config/prototypemachinery/structures/
 *    - 结构 ID 必须完全匹配（区分大小写）
 *    - 使用 structure("id") 进行延迟加载（推荐）
 *    - 结构在 CraftTweaker 脚本之前加载，因此引用总是安全的
 * 
 * 3. Organization / 组织结构:
 *    - Group related machines in the same script
 *    - Use comments to explain complex configurations
 * 
 * 4. Error Handling / 错误处理:
 *    - Check server logs for registration errors
 *    - Duplicate IDs will be logged and rejected
 *    - Missing structures will cause errors at load time
 * 
 * 5. Performance / 性能:
 *    - Register all machines in PreInit phase
 *    - Avoid registering too many similar machines
 *    - Use structure variants instead of duplicate machines
 */
