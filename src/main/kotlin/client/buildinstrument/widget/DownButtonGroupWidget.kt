package github.kasuminova.prototypemachinery.client.buildinstrument.widget

import com.cleanroommc.modularui.api.widget.Interactable
import com.cleanroommc.modularui.drawable.UITexture
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.theme.WidgetThemeEntry
import com.cleanroommc.modularui.value.BoolValue
import com.cleanroommc.modularui.widgets.layout.Column
import github.kasuminova.prototypemachinery.PrototypeMachinery
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

/**
 * Down button group widget for Build Instrument.
 */
@SideOnly(Side.CLIENT)
internal class DownButtonGroupWidget(
    private val onMaterialPreviewToggle: (Boolean) -> Unit
) : Column(), Interactable {

    companion object {
        private fun guiTex(path: String): UITexture {
            return UITexture.fullImage(
                ResourceLocation(PrototypeMachinery.MOD_ID, "gui/gui_structure_preview/$path")
            )
        }

        private val COMP_SWITCH_DEFAULT by lazy { guiTex("down_button_default/component_switch_default") }
        private val COMP_SWITCH_SELECTED by lazy { guiTex("down_button_selected/component_switch_selected") }
        private val COMP_SWITCH_PRESSED by lazy { guiTex("down_button_pressed/component_switch_pressed") }

        private val DOWN_BUTTON_BASE_ON by lazy { guiTex("down_button_base_on") }

        private val LED_OFF by lazy { guiTex("down_button_base_off-led") }
        private val LED_ON by lazy { guiTex("down_button_base_on-led") }

        private val MAT_PREVIEW_DEFAULT by lazy { guiTex("down_button_default/material_preview_ui_default") }
        private val MAT_PREVIEW_SELECTED by lazy { guiTex("down_button_selected/material_preview_ui_selected") }
        private val MAT_PREVIEW_PRESSED by lazy { guiTex("down_button_pressed/material_preview_ui_pressed") }
    }

    public val componentSwitchEnabled: BoolValue = BoolValue(false)
    public val materialPreviewEnabled: BoolValue = BoolValue(false)

    private var slideOffset: Float = 0f
    private val targetOffset: Float
        get() = if (componentSwitchEnabled.boolValue) 19f else 0f

    override fun onInit() {
        size(40, 16)
        super.onInit()
    }

    override fun draw(context: ModularGuiContext, widgetTheme: WidgetThemeEntry<*>) {
        val target = targetOffset
        if (slideOffset != target) {
            val delta = (target - slideOffset) * 0.3f
            slideOffset += delta
            if (kotlin.math.abs(target - slideOffset) < 0.5f) {
                slideOffset = target
            }
        }

        val compSwitchX = 28f
        val compSwitchY = 1f
        val compSwitchHover = isMouseInArea(compSwitchX.toInt(), compSwitchY.toInt(), 12, 14)
        val compSwitchTex = when {
            componentSwitchEnabled.boolValue -> COMP_SWITCH_PRESSED
            compSwitchHover -> COMP_SWITCH_SELECTED
            else -> COMP_SWITCH_DEFAULT
        }
        compSwitchTex.draw(compSwitchX, compSwitchY, 12f, 14f)

        GlStateManager.pushMatrix()
        GlStateManager.translate(slideOffset, 0f, 0f)

        val baseX = 0f
        val baseY = 3f
        DOWN_BUTTON_BASE_ON.draw(baseX, baseY, 24f, 13f)

        val ledTex = if (componentSwitchEnabled.boolValue) LED_ON else LED_OFF
        ledTex.draw(baseX + 2f, baseY + 3f, 1f, 7f)

        val matBtnX = baseX + 6f
        val matBtnY = baseY - 3f
        val matBtnDisabled = componentSwitchEnabled.boolValue
        if (!matBtnDisabled) {
            val matBtnHover = isMouseInArea(
                (matBtnX + slideOffset).toInt(), matBtnY.toInt(), 12, 14
            )
            val matBtnTex = when {
                materialPreviewEnabled.boolValue -> MAT_PREVIEW_PRESSED
                matBtnHover -> MAT_PREVIEW_SELECTED
                else -> MAT_PREVIEW_DEFAULT
            }
            matBtnTex.draw(matBtnX, matBtnY, 12f, 14f)
        }

        GlStateManager.popMatrix()

        super.draw(context, widgetTheme)
    }

    override fun onMousePressed(mouseButton: Int): Interactable.Result {
        if (mouseButton != 0) return Interactable.Result.IGNORE

        val relX = context.mouseX - area.x
        val relY = context.mouseY - area.y

        if (relX in 28 until 40 && relY in 1 until 15) {
            componentSwitchEnabled.boolValue = !componentSwitchEnabled.boolValue
            return Interactable.Result.SUCCESS
        }

        if (!componentSwitchEnabled.boolValue) {
            val matBtnX = 6 + slideOffset.toInt()
            val matBtnY = 0
            if (relX in matBtnX until (matBtnX + 12) && relY in matBtnY until (matBtnY + 14)) {
                materialPreviewEnabled.boolValue = !materialPreviewEnabled.boolValue
                onMaterialPreviewToggle(materialPreviewEnabled.boolValue)
                return Interactable.Result.SUCCESS
            }
        }

        return Interactable.Result.IGNORE
    }

    private fun isMouseInArea(x: Int, y: Int, w: Int, h: Int): Boolean {
        val relX = context.mouseX - area.x
        val relY = context.mouseY - area.y
        return relX in x until (x + w) && relY in y until (y + h)
    }
}
