package github.kasuminova.prototypemachinery.common.advancement

import github.kasuminova.prototypemachinery.PrototypeMachinery
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.util.ResourceLocation

/**
 * Small helper for granting built-in advancements.
 *
 * 1.12.2 Advancements are loaded from assets automatically; no explicit registration required.
 */
internal object PMAdvancements {

    private val CHERISH_TOOL_ID = ResourceLocation(PrototypeMachinery.MOD_ID, "cherish_tool")
    private const val CHERISH_TOOL_CRITERION = "trigger"

    fun grantCherishTool(player: EntityPlayerMP) {
        val adv = player.server.advancementManager.getAdvancement(CHERISH_TOOL_ID) ?: return
        val progress = player.advancements.getProgress(adv)
        if (!progress.isDone) {
            player.advancements.grantCriterion(adv, CHERISH_TOOL_CRITERION)
        }
    }
}
