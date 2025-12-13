package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass

import crafttweaker.annotations.ZenRegister
import github.kasuminova.prototypemachinery.common.registry.MachineTypeRegisterer
import github.kasuminova.prototypemachinery.integration.crafttweaker.CraftTweakerBridge
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod

/**
 * ZenScript class for machine type registration (preview-oriented API surface).
 * 面向预览期的 ZenScript 机器类型注册入口。
 *
 * Usage 示例 / Example:
 * ```zenscript
 * import mods.prototypemachinery.MachineRegistry;
 * 
 * val builder = MachineRegistry.create("my_mod", "my_machine");
 * builder.name("My Machine");
 * // builder.structure(structure); // Set structure
 * MachineRegistry.register(builder);
 * ```
 */
@ZenClass("mods.prototypemachinery.MachineRegistry")
@ZenRegister
public class ZenMachineRegistry private constructor() {

    public companion object {
        /**
         * Create a machine type builder (modId + path).
         * 通过 modId + path 创建机器类型构建器。
         */
        @JvmStatic
        @ZenMethod
        public fun create(modId: String, path: String): ZenMachineTypeBuilder {
            val builder = CraftTweakerBridge.createBuilder(modId, path)
            return ZenMachineTypeBuilder(builder)
        }

        /**
         * Register machine type built via builder.
         * 使用构建器生成的机器类型进行注册。
         */
        @JvmStatic
        @ZenMethod
        public fun register(builder: ZenMachineTypeBuilder) {
            val ctMachineType = builder.build()
            MachineTypeRegisterer.queue(ctMachineType)
        }
    }

}
