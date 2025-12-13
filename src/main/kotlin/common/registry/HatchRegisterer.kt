package github.kasuminova.prototypemachinery.common.registry

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.common.block.hatch.HatchType
import github.kasuminova.prototypemachinery.common.block.hatch.energy.EnergyHatchBlock
import github.kasuminova.prototypemachinery.common.block.hatch.energy.EnergyHatchBlockEntity
import github.kasuminova.prototypemachinery.common.block.hatch.fluid.FluidHatchBlock
import github.kasuminova.prototypemachinery.common.block.hatch.fluid.FluidHatchBlockEntity
import github.kasuminova.prototypemachinery.common.block.hatch.fluid.FluidIOHatchBlock
import github.kasuminova.prototypemachinery.common.block.hatch.fluid.FluidIOHatchBlockEntity
import github.kasuminova.prototypemachinery.common.block.hatch.item.ItemHatchBlock
import github.kasuminova.prototypemachinery.common.block.hatch.item.ItemHatchBlockEntity
import github.kasuminova.prototypemachinery.common.block.hatch.item.ItemIOHatchBlock
import github.kasuminova.prototypemachinery.common.block.hatch.item.ItemIOHatchBlockEntity
import github.kasuminova.prototypemachinery.common.util.register
import net.minecraft.block.Block
import net.minecraft.item.Item
import net.minecraft.item.ItemBlock
import net.minecraftforge.event.RegistryEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

/**
 * # HatchRegisterer - Hatch Block Registerer
 * # HatchRegisterer - 仓位方块注册器
 *
 * Registers all hatch blocks (item, fluid, energy) for all tiers.
 *
 * 为所有等级注册所有仓位方块（物品、流体、能量）。
 */
internal object HatchRegisterer {

    /** All registered item hatch blocks. 所有已注册的物品仓方块。 */
    private val itemHatchBlocks = mutableMapOf<String, ItemHatchBlock>()

    /** All registered item IO hatch blocks. 所有已注册的物品交互仓方块。 */
    private val itemIOHatchBlocks = mutableMapOf<String, ItemIOHatchBlock>()

    /** All registered fluid hatch blocks. 所有已注册的流体仓方块。 */
    private val fluidHatchBlocks = mutableMapOf<String, FluidHatchBlock>()

    /** All registered fluid IO hatch blocks. 所有已注册的流体交互仓方块。 */
    private val fluidIOHatchBlocks = mutableMapOf<String, FluidIOHatchBlock>()

    /** All registered energy hatch blocks. 所有已注册的能量仓方块。 */
    private val energyHatchBlocks = mutableMapOf<String, EnergyHatchBlock>()

    @SubscribeEvent
    fun onRegisterBlocks(event: RegistryEvent.Register<Block>) {
        PrototypeMachinery.logger.info("Registering hatch blocks...")

        registerItemHatches(event)
        registerFluidHatches(event)
        registerEnergyHatches(event)
        registerBlockEntities()

        val totalCount = itemHatchBlocks.size + itemIOHatchBlocks.size +
                fluidHatchBlocks.size + fluidIOHatchBlocks.size + energyHatchBlocks.size
        PrototypeMachinery.logger.info("Registered $totalCount hatch blocks")
    }

    @SubscribeEvent
    fun onRegisterItems(event: RegistryEvent.Register<Item>) {
        PrototypeMachinery.logger.info("Registering hatch item blocks...")

        // Register item blocks for all hatches
        for (block in itemHatchBlocks.values) {
            registerItemBlock(event, block)
        }
        for (block in itemIOHatchBlocks.values) {
            registerItemBlock(event, block)
        }
        for (block in fluidHatchBlocks.values) {
            registerItemBlock(event, block)
        }
        for (block in fluidIOHatchBlocks.values) {
            registerItemBlock(event, block)
        }
        for (block in energyHatchBlocks.values) {
            registerItemBlock(event, block)
        }
    }

    private fun registerItemHatches(event: RegistryEvent.Register<Block>) {
        // Register input/output hatches for all 10 tiers
        for (tier in 1..10) {
            // Input hatch
            val inputConfig = HatchConfigRegistry.getItemHatchConfig(tier, HatchType.INPUT)
            val inputBlock = ItemHatchBlock(inputConfig)
            event.registry.register(inputBlock)
            itemHatchBlocks["item_input_hatch_$tier"] = inputBlock

            // Output hatch
            val outputConfig = HatchConfigRegistry.getItemHatchConfig(tier, HatchType.OUTPUT)
            val outputBlock = ItemHatchBlock(outputConfig)
            event.registry.register(outputBlock)
            itemHatchBlocks["item_output_hatch_$tier"] = outputBlock

            // IO hatch
            val ioConfig = HatchConfigRegistry.getItemIOHatchConfig(tier)
            val ioBlock = ItemIOHatchBlock(ioConfig)
            event.registry.register(ioBlock)
            itemIOHatchBlocks["item_io_hatch_$tier"] = ioBlock
        }
    }

    private fun registerFluidHatches(event: RegistryEvent.Register<Block>) {
        // Register input/output hatches for all 10 tiers
        for (tier in 1..10) {
            // Input hatch
            val inputConfig = HatchConfigRegistry.getFluidHatchConfig(tier, HatchType.INPUT)
            val inputBlock = FluidHatchBlock(inputConfig)
            event.registry.register(inputBlock)
            fluidHatchBlocks["fluid_input_hatch_$tier"] = inputBlock

            // Output hatch
            val outputConfig = HatchConfigRegistry.getFluidHatchConfig(tier, HatchType.OUTPUT)
            val outputBlock = FluidHatchBlock(outputConfig)
            event.registry.register(outputBlock)
            fluidHatchBlocks["fluid_output_hatch_$tier"] = outputBlock

            // IO hatch
            val ioConfig = HatchConfigRegistry.getFluidIOHatchConfig(tier)
            val ioBlock = FluidIOHatchBlock(ioConfig)
            event.registry.register(ioBlock)
            fluidIOHatchBlocks["fluid_io_hatch_$tier"] = ioBlock
        }
    }

    private fun registerEnergyHatches(event: RegistryEvent.Register<Block>) {
        // Register input/output/io hatches for all 10 tiers
        for (tier in 1..10) {
            // Input hatch
            val inputConfig = HatchConfigRegistry.getEnergyHatchConfig(tier, HatchType.INPUT)
            val inputBlock = EnergyHatchBlock(inputConfig)
            event.registry.register(inputBlock)
            energyHatchBlocks["energy_input_hatch_$tier"] = inputBlock

            // Output hatch
            val outputConfig = HatchConfigRegistry.getEnergyHatchConfig(tier, HatchType.OUTPUT)
            val outputBlock = EnergyHatchBlock(outputConfig)
            event.registry.register(outputBlock)
            energyHatchBlocks["energy_output_hatch_$tier"] = outputBlock

            // IO hatch
            val ioConfig = HatchConfigRegistry.getEnergyHatchConfig(tier, HatchType.IO)
            val ioBlock = EnergyHatchBlock(ioConfig)
            event.registry.register(ioBlock)
            energyHatchBlocks["energy_io_hatch_$tier"] = ioBlock
        }
    }

    private fun registerBlockEntities() {
        ItemHatchBlockEntity::class.register("item_hatch_block_entity")
        ItemIOHatchBlockEntity::class.register("item_io_hatch_block_entity")
        FluidHatchBlockEntity::class.register("fluid_hatch_block_entity")
        FluidIOHatchBlockEntity::class.register("fluid_io_hatch_block_entity")
        EnergyHatchBlockEntity::class.register("energy_hatch_block_entity")

        PrototypeMachinery.logger.info("Registered hatch block entities")
    }

    private fun registerItemBlock(event: RegistryEvent.Register<Item>, block: Block) {
        val itemBlock = ItemBlock(block)
        itemBlock.registryName = block.registryName
        event.registry.register(itemBlock)
    }

    // region Getters

    /**
     * Gets an item hatch block by key.
     * 通过键获取物品仓方块。
     */
    fun getItemHatchBlock(key: String): ItemHatchBlock? = itemHatchBlocks[key]

    /**
     * Gets an item IO hatch block by key.
     * 通过键获取物品交互仓方块。
     */
    fun getItemIOHatchBlock(key: String): ItemIOHatchBlock? = itemIOHatchBlocks[key]

    /**
     * Gets a fluid hatch block by key.
     * 通过键获取流体仓方块。
     */
    fun getFluidHatchBlock(key: String): FluidHatchBlock? = fluidHatchBlocks[key]

    /**
     * Gets a fluid IO hatch block by key.
     * 通过键获取流体交互仓方块。
     */
    fun getFluidIOHatchBlock(key: String): FluidIOHatchBlock? = fluidIOHatchBlocks[key]

    /**
     * Gets an energy hatch block by key.
     * 通过键获取能量仓方块。
     */
    fun getEnergyHatchBlock(key: String): EnergyHatchBlock? = energyHatchBlocks[key]

    /**
     * Gets all item hatch blocks.
     * 获取所有物品仓方块。
     */
    fun getAllItemHatchBlocks(): Collection<ItemHatchBlock> = itemHatchBlocks.values

    /**
     * Gets all item IO hatch blocks.
     * 获取所有物品交互仓方块。
     */
    fun getAllItemIOHatchBlocks(): Collection<ItemIOHatchBlock> = itemIOHatchBlocks.values

    /**
     * Gets all fluid hatch blocks.
     * 获取所有流体仓方块。
     */
    fun getAllFluidHatchBlocks(): Collection<FluidHatchBlock> = fluidHatchBlocks.values

    /**
     * Gets all fluid IO hatch blocks.
     * 获取所有流体交互仓方块。
     */
    fun getAllFluidIOHatchBlocks(): Collection<FluidIOHatchBlock> = fluidIOHatchBlocks.values

    /**
     * Gets all energy hatch blocks.
     * 获取所有能量仓方块。
     */
    fun getAllEnergyHatchBlocks(): Collection<EnergyHatchBlock> = energyHatchBlocks.values

    /**
     * Gets all hatch blocks.
     * 获取所有仓位方块。
     */
    fun getAllHatchBlocks(): List<Block> {
        return buildList {
            addAll(itemHatchBlocks.values)
            addAll(itemIOHatchBlocks.values)
            addAll(fluidHatchBlocks.values)
            addAll(fluidIOHatchBlocks.values)
            addAll(energyHatchBlocks.values)
        }
    }

    // endregion

}
