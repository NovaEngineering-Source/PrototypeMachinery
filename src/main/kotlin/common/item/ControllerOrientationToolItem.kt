package github.kasuminova.prototypemachinery.common.item

import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.tuning.OrientationToolTuning
import github.kasuminova.prototypemachinery.common.advancement.PMAdvancements
import github.kasuminova.prototypemachinery.common.block.MachineBlock
import github.kasuminova.prototypemachinery.common.block.entity.MachineBlockEntity
import github.kasuminova.prototypemachinery.common.registry.PMCreativeTabs
import github.kasuminova.prototypemachinery.common.util.TwistMath
import net.minecraft.block.state.IBlockState
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.enchantment.EnumEnchantmentType
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.SharedMonsterAttributes
import net.minecraft.entity.ai.attributes.AttributeModifier
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.init.Items
import net.minecraft.init.SoundEvents
import net.minecraft.inventory.EntityEquipmentSlot
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.ActionResult
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.ResourceLocation
import net.minecraft.util.SoundCategory
import net.minecraft.util.math.BlockPos
import net.minecraft.util.text.TextComponentTranslation
import net.minecraft.world.World

/**
 * A simple wrench-like tool for rotating machine controllers.
 *
 * ## Interaction Rules
 * - **Click FRONT face**: Rotate twist clockwise (0→1→2→3→0)
 * - **Click other face**: Set FACING to that clicked direction
 * - **Sneak + click**: Cycle through all 24 orientations
 */
internal class ControllerOrientationToolItem : Item() {

    init {
        registryName = ResourceLocation(PrototypeMachinery.MOD_ID, "orientation_tool")
        translationKey = "${PrototypeMachinery.MOD_ID}.orientation_tool"
        maxStackSize = 1

        setCreativeTab(PMCreativeTabs.MAIN)

        // It is both a tool and a weapon; allow durability damage.
        // 耐久未在需求中指定，这里给一个相对耐用的默认值（可后续按平衡调整）。
        setMaxDamage(OrientationToolTuning.maxDurability)

        // Item model predicate for switching wrench_off/wrench_on.
        // 物品模型 predicate：根据 NBT 开关切换开/关模型。
        addPropertyOverride(ResourceLocation("enabled")) { stack, _, _ ->
            if (stack != null && isEnabled(stack)) 1.0f else 0.0f
        }
    }

    override fun onItemRightClick(worldIn: World, playerIn: EntityPlayer, handIn: EnumHand): ActionResult<ItemStack> {
        val stack = playerIn.getHeldItem(handIn)

        // Sneak + right click in air to toggle mode.
        // 潜行 + 右键空气切换模式（不与“潜行点击控制器=循环24向”冲突）。
        if (playerIn.isSneaking) {
            if (worldIn.isRemote) {
                return ActionResult(EnumActionResult.SUCCESS, stack)
            }
            val enabled = isEnabled(stack)
            val newEnabled = !enabled
            setEnabled(stack, newEnabled)

            val key = if (newEnabled) "pm.orientation_tool.enabled" else "pm.orientation_tool.disabled"
            playerIn.sendStatusMessage(TextComponentTranslation(key), true)
            worldIn.playSound(
                null,
                playerIn.posX,
                playerIn.posY,
                playerIn.posZ,
                SoundEvents.UI_BUTTON_CLICK,
                SoundCategory.PLAYERS,
                0.35f,
                if (newEnabled) 1.25f else 0.9f
            )

            return ActionResult(EnumActionResult.SUCCESS, stack)
        }

        return ActionResult(EnumActionResult.PASS, stack)
    }

    override fun onItemUse(
        player: EntityPlayer,
        worldIn: World,
        pos: BlockPos,
        hand: EnumHand,
        facing: EnumFacing,
        hitX: Float,
        hitY: Float,
        hitZ: Float
    ): EnumActionResult {
        val stack = player.getHeldItem(hand)

        // Closed mode: behave like a multi-tool; do not rotate controllers.
        if (!isEnabled(stack)) {
            return EnumActionResult.PASS
        }

        val te = worldIn.getTileEntity(pos) as? MachineBlockEntity ?: return EnumActionResult.PASS

        if (worldIn.isRemote) {
            return EnumActionResult.SUCCESS
        }

        val ok = applyToController(
            world = worldIn,
            pos = pos,
            player = player,
            te = te,
            clickedSide = facing,
            isSneaking = player.isSneaking
        )

        if (ok) {
            // Enabled mode interaction consumes more durability.
            stack.damageItem(OrientationToolTuning.durabilityCostRotateEnabled, player)
        }

        return if (ok) EnumActionResult.SUCCESS else EnumActionResult.FAIL
    }

    override fun hitEntity(stack: ItemStack, target: EntityLivingBase, attacker: EntityLivingBase): Boolean {
        val enabled = isEnabled(stack)

        // Close mode: award advancement on attack.
        if (!enabled && !attacker.world.isRemote) {
            (attacker as? EntityPlayerMP)?.let { PMAdvancements.grantCherishTool(it) }
        }

        stack.damageItem(
            if (enabled) OrientationToolTuning.durabilityCostAttackEnabled else OrientationToolTuning.durabilityCostAttackDisabled,
            attacker
        )
        return true
    }

    override fun onBlockDestroyed(
        stack: ItemStack,
        worldIn: World,
        state: IBlockState,
        pos: BlockPos,
        entityLiving: EntityLivingBase
    ): Boolean {
        // Only consume durability if the block actually has hardness.
        if (state.getBlockHardness(worldIn, pos) != 0.0f) {
            stack.damageItem(
                if (isEnabled(stack)) OrientationToolTuning.durabilityCostBreakEnabled else OrientationToolTuning.durabilityCostBreakDisabled,
                entityLiving
            )
        }
        return true
    }

    override fun getDestroySpeed(stack: ItemStack, state: IBlockState): Float {
        // Enabled mode is not meant for mining.
        if (isEnabled(stack)) {
            return 1.0f
        }

        // Closed mode: behave like an iron-tier pickaxe/axe/shovel hybrid.
        val pick = Items.IRON_PICKAXE.getDestroySpeed(stack, state)
        val axe = Items.IRON_AXE.getDestroySpeed(stack, state)
        val shovel = Items.IRON_SHOVEL.getDestroySpeed(stack, state)
        return maxOf(pick, axe, shovel)
    }

    override fun getHarvestLevel(
        stack: ItemStack,
        toolClass: String,
        player: EntityPlayer?,
        blockState: IBlockState?
    ): Int {
        if (!isEnabled(stack) && (toolClass == TOOL_CLASS_PICKAXE || toolClass == TOOL_CLASS_AXE || toolClass == TOOL_CLASS_SHOVEL)) {
            return IRON_HARVEST_LEVEL
        }
        return super.getHarvestLevel(stack, toolClass, player, blockState)
    }

    override fun getToolClasses(stack: ItemStack): MutableSet<String> {
        if (isEnabled(stack)) {
            return mutableSetOf()
        }
        return mutableSetOf(TOOL_CLASS_PICKAXE, TOOL_CLASS_AXE, TOOL_CLASS_SHOVEL)
    }

    override fun canHarvestBlock(state: IBlockState, stack: ItemStack): Boolean {
        if (isEnabled(stack)) {
            return false
        }
        return Items.IRON_PICKAXE.canHarvestBlock(state) ||
            Items.IRON_AXE.canHarvestBlock(state) ||
            Items.IRON_SHOVEL.canHarvestBlock(state)
    }

    override fun getItemEnchantability(): Int = OrientationToolTuning.enchantability

    override fun canApplyAtEnchantingTable(stack: ItemStack, enchantment: Enchantment): Boolean {
        val t = enchantment.type
        if (t == EnumEnchantmentType.DIGGER || t == EnumEnchantmentType.WEAPON || t == EnumEnchantmentType.BREAKABLE) {
            return true
        }
        return super.canApplyAtEnchantingTable(stack, enchantment)
    }

    override fun isBookEnchantable(stack: ItemStack, book: ItemStack): Boolean {
        val enchants = EnchantmentHelper.getEnchantments(book)
        if (enchants.isEmpty()) {
            return false
        }
        return enchants.keys.all { canApplyAtEnchantingTable(stack, it) }
    }

    override fun hasEffect(stack: ItemStack): Boolean {
        // Enabled mode has a subtle visual cue.
        return isEnabled(stack) || super.hasEffect(stack)
    }

    override fun addInformation(
        stack: ItemStack,
        worldIn: World?,
        tooltip: MutableList<String>,
        flagIn: ITooltipFlag
    ) {
        val enabled = isEnabled(stack)
        val stateKey = if (enabled) "tooltip.prototypemachinery.orientation_tool.state.on" else "tooltip.prototypemachinery.orientation_tool.state.off"
        tooltip.add(TextComponentTranslation("tooltip.prototypemachinery.orientation_tool.state", TextComponentTranslation(stateKey)).formattedText)
        tooltip.add(TextComponentTranslation("tooltip.prototypemachinery.orientation_tool.toggle_hint").formattedText)
        super.addInformation(stack, worldIn, tooltip, flagIn)
    }

    override fun getAttributeModifiers(slot: EntityEquipmentSlot, stack: ItemStack): Multimap<String, AttributeModifier> {
        val map = HashMultimap.create<String, AttributeModifier>()
        if (slot == EntityEquipmentSlot.MAINHAND) {
            val damage = if (isEnabled(stack)) OrientationToolTuning.attackDamageEnabled else OrientationToolTuning.attackDamageDisabled
            map.put(
                SharedMonsterAttributes.ATTACK_DAMAGE.name,
                AttributeModifier(ATTACK_DAMAGE_MODIFIER, "Tool modifier", damage, 0)
            )
            map.put(
                SharedMonsterAttributes.ATTACK_SPEED.name,
                AttributeModifier(ATTACK_SPEED_MODIFIER, "Tool modifier", OrientationToolTuning.attackSpeed, 0)
            )
        }
        return map
    }

    internal companion object {

        private const val NBT_ENABLED: String = "pm_orientation_tool_enabled"

        private const val TOOL_CLASS_PICKAXE: String = "pickaxe"
        private const val TOOL_CLASS_AXE: String = "axe"
        private const val TOOL_CLASS_SHOVEL: String = "shovel"

        private const val IRON_HARVEST_LEVEL: Int = 2

        internal fun isEnabled(stack: ItemStack): Boolean {
            val tag = stack.tagCompound ?: return false
            return tag.getBoolean(NBT_ENABLED)
        }

        internal fun setEnabled(stack: ItemStack, enabled: Boolean) {
            val tag = stack.tagCompound ?: net.minecraft.nbt.NBTTagCompound().also { stack.tagCompound = it }
            tag.setBoolean(NBT_ENABLED, enabled)
        }

        internal fun applyToController(
            world: World,
            pos: BlockPos,
            player: EntityPlayer,
            te: MachineBlockEntity,
            clickedSide: EnumFacing,
            isSneaking: Boolean,
        ): Boolean {
            val state = world.getBlockState(pos)

            val curFacing = runCatching { state.getValue(MachineBlock.FACING) }
                .getOrDefault(EnumFacing.NORTH)
            val curTwist = te.twist

            val playerName = runCatching { player.name }.getOrDefault("<unknown>")

            // Sneaking: cycle through all 24 orientations
            if (isSneaking) {
                val (newFacing, newTwist) = nextOrientation(curFacing, curTwist)

                PrototypeMachinery.logger.info(
                    "[OrientationTool] sneak-cycle pos={} player={} (facing,twist) ({},{}) -> ({},{})",
                    pos, playerName, curFacing.name, curTwist, newFacing.name, newTwist
                )

                if (newFacing != curFacing) {
                    world.setBlockState(pos, state.withProperty(MachineBlock.FACING, newFacing), 3)
                }
                te.setTwist(newTwist)
                return true
            }

            // Click front face -> rotate twist clockwise
            if (clickedSide == curFacing) {
                val newTwist = TwistMath.nextTwist(curTwist)

                PrototypeMachinery.logger.info(
                    "[OrientationTool] rotate-twist pos={} player={} facing={} twist {} -> {}",
                    pos, playerName, curFacing.name, curTwist, newTwist
                )

                te.setTwist(newTwist)
                return true
            }

            // Click other face -> set facing to that direction, reset twist to 0
            val newFacing = clickedSide

            PrototypeMachinery.logger.info(
                "[OrientationTool] set-facing pos={} player={} (facing,twist) ({},{}) -> ({},0)",
                pos, playerName, curFacing.name, curTwist, newFacing.name
            )

            if (newFacing != curFacing) {
                world.setBlockState(pos, state.withProperty(MachineBlock.FACING, newFacing), 3)
            }
            te.setTwist(0)

            return true
        }

        /**
         * Cycles to the next of 24 orientations in a fixed order.
         * Order: increment twist (0→1→2→3), then move to next facing, reset twist to 0.
         */
        private fun nextOrientation(facing: EnumFacing, twist: Int): Pair<EnumFacing, Int> {
            val newTwist = (twist + 1) and 3
            if (newTwist != 0) {
                return facing to newTwist
            }

            // Twist wrapped around, move to next facing
            val facings = EnumFacing.values()
            val idx = facings.indexOf(facing)
            val newFacing = facings[(idx + 1) % facings.size]
            return newFacing to 0
        }
    }
}
