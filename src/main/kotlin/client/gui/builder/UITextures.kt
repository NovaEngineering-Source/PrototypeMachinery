package github.kasuminova.prototypemachinery.client.gui.builder

import com.cleanroommc.modularui.api.drawable.IDrawable
import com.cleanroommc.modularui.drawable.UITexture
import github.kasuminova.prototypemachinery.client.gui.drawable.SeparatedNineSliceTexture
import net.minecraft.util.ResourceLocation

import java.util.concurrent.ConcurrentHashMap

public class UITextures {

    // ==========================================
    // Default texture atlas (states.png)
    // ==========================================

    private val statesLoc: ResourceLocation = ResourceLocation("prototypemachinery", "textures/gui/states.png")
    private val statesTexSize: Int = 256

    // Tab textures (kept consistent with DefaultMachineUI)
    private val tabW: Int = 21
    private val tabH: Int = 22
    private val tabTexOffset: Int = 3
    private val tab1X: Int = 185
    private val tab1Y: Int = 182
    private val tab2X: Int = 185
    private val tab2Y: Int = 207

    // Button textures (27x15) - Normal / Hover / Pressed / Hover+Pressed
    private val btnW: Int = 27
    private val btnH: Int = 15
    // NOTE: These coordinates must match *our* states.png.
    // The old values were copied from another atlas layout and pointed to fully-transparent pixels,
    // causing buttons/progress to not render (only debug borders were visible).
    private val btnNormalX: Int = 0
    private val btnNormalY: Int = 76
    private val btnHoverX: Int = 0
    private val btnHoverY: Int = 92

    // Hover + Pressed
    private val btnHoverPressedX: Int = 0
    private val btnHoverPressedY: Int = 124
    private val btnPressedX: Int = 0
    private val btnPressedY: Int = 108

    // Progress textures (55x12) - Empty / Full
    private val progW: Int = 55
    private val progH: Int = 12
    private val progEmptyX: Int = 0
    private val progEmptyY: Int = 18
    private val progFullX: Int = 0
    private val progFullY: Int = 32

    // Slot textures (18x18)
    private val slotW: Int = 18
    private val slotH: Int = 18
    private val slotX: Int = 0
    private val slotY: Int = 0

    // Fluid slot frame (18x71)
    private val fluidFrameW: Int = 18
    private val fluidFrameH: Int = 71
    private val fluidFrameX: Int = 73
    private val fluidFrameY: Int = 19

    // Slider handle
    public val sliderHandleW: Int = 12
    public val sliderHandleH: Int = 15
    private val sliderHandleX: Int = 146
    private val sliderHandleY: Int = 1

    private fun statesTexture(x: Int, y: Int, w: Int, h: Int, adaptableBorder: Int? = null): UITexture {
        val builder = UITexture.builder()
            .location(statesLoc)
            .imageSize(statesTexSize, statesTexSize)
            .subAreaXYWH(x, y, w, h)

        if (adaptableBorder != null && adaptableBorder > 0) {
            builder.adaptable(adaptableBorder)
        }

        return builder.build()
    }

    public val defaultButtonNormal: IDrawable = statesTexture(btnNormalX, btnNormalY, btnW, btnH, adaptableBorder = 4)
    public val defaultButtonHover: IDrawable = statesTexture(btnHoverX, btnHoverY, btnW, btnH, adaptableBorder = 4)
    public val defaultButtonPressed: IDrawable = statesTexture(btnPressedX, btnPressedY, btnW, btnH, adaptableBorder = 4)
    public val defaultButtonHoverPressed: IDrawable = statesTexture(btnHoverPressedX, btnHoverPressedY, btnW, btnH, adaptableBorder = 4)

    public val defaultProgressEmpty: UITexture = statesTexture(progEmptyX, progEmptyY, progW, progH, adaptableBorder = 4)
    public val defaultProgressFull: UITexture = statesTexture(progFullX, progFullY, progW, progH, adaptableBorder = 4)

    public val defaultSlotBackground: IDrawable = statesTexture(slotX, slotY, slotW, slotH)
    public val defaultFluidFrame: IDrawable = statesTexture(fluidFrameX, fluidFrameY, fluidFrameW, fluidFrameH, adaptableBorder = 4)
    public val defaultSliderHandle: IDrawable = statesTexture(sliderHandleX, sliderHandleY, sliderHandleW, sliderHandleH)

    private fun tabTexture(x: Int, y: Int, offsetIndex: Int): IDrawable {
        return statesTexture(x + (offsetIndex * (tabW + tabTexOffset)), y, tabW, tabH)
    }

    public val defaultTab1Inactive: IDrawable get() = tabTexture(tab1X, tab1Y, 0)
    public val defaultTab1Hover: IDrawable get() = tabTexture(tab1X, tab1Y, 1)
    public val defaultTab1Active: IDrawable get() = tabTexture(tab1X, tab1Y, 2)

    public val defaultTab2Inactive: IDrawable get() = tabTexture(tab2X, tab2Y, 0)
    public val defaultTab2Hover: IDrawable get() = tabTexture(tab2X, tab2Y, 1)
    public val defaultTab2Active: IDrawable get() = tabTexture(tab2X, tab2Y, 2)

    public fun normalizeTexturePath(texturePath: String?): String? {
        val p = texturePath?.trim()
        return if (p.isNullOrEmpty()) null else p
    }

    public fun parseTexture(texturePath: String): IDrawable {
        return UITexture.fullImage(ResourceLocation(texturePath))
    }

    // ==========================================
    // gui_states helpers
    // ==========================================

    private val guiStatesCache: MutableMap<String, IDrawable> = ConcurrentHashMap()

    /**
     * Build a full-image drawable for a gui_states PNG.
     *
     * @param relPath path relative to `textures/`, without `.png`, e.g. `gui/gui_states/empty_button/normal/default_n`
     */
    public fun guiStatesFull(relPath: String): IDrawable {
        val key = "full:$relPath"
        return guiStatesCache.getOrPut(key) {
            UITexture.fullImage(ResourceLocation("prototypemachinery", relPath))
        }
    }

    /**
     * Build a 9-slice (adaptable) drawable for a gui_states PNG.
     */
    public fun guiStatesAdaptable(
        relPath: String,
        imageW: Int,
        imageH: Int,
        borderLeft: Int,
        borderTop: Int,
        borderRight: Int,
        borderBottom: Int,
        subX: Int = 0,
        subY: Int = 0,
        subW: Int = imageW,
        subH: Int = imageH
    ): IDrawable {
        val key = "adapt:$relPath:$imageW,$imageH:sub=$subX,$subY,$subW,$subH:b=$borderLeft,$borderTop,$borderRight,$borderBottom"
        return guiStatesCache.getOrPut(key) {
            UITexture.builder()
                .location(ResourceLocation("prototypemachinery", relPath))
                .imageSize(imageW, imageH)
                .subAreaXYWH(subX, subY, subW, subH)
                .adaptable(borderLeft, borderTop, borderRight, borderBottom)
                .build()
        }
    }

    /**
     * Build a 9-slice drawable for gui_states *_expand textures, but do NOT render the 1px separator lines.
     *
     * This is needed because the documented gui_states templates include separator pixels between slices.
     * With standard 9-slice those separators are still drawn (just not stretched), resulting in visible lines.
     */
    public fun guiStatesSeparatedAdaptable(
        relPath: String,
        imageW: Int,
        imageH: Int,
        borderLeft: Int,
        borderTop: Int,
        borderRight: Int,
        borderBottom: Int,
        subX: Int = 0,
        subY: Int = 0,
        subW: Int = imageW,
        subH: Int = imageH,
        separatorPx: Int = 1
    ): IDrawable {
        val key = "adapt_sep:$relPath:$imageW,$imageH:sub=$subX,$subY,$subW,$subH:b=$borderLeft,$borderTop,$borderRight,$borderBottom:sep=$separatorPx"
        return guiStatesCache.getOrPut(key) {
            val u0 = subX.toFloat() / imageW.toFloat()
            val v0 = subY.toFloat() / imageH.toFloat()
            val u1 = (subX + subW).toFloat() / imageW.toFloat()
            val v1 = (subY + subH).toFloat() / imageH.toFloat()

            SeparatedNineSliceTexture(
                location = ResourceLocation("prototypemachinery", relPath),
                u0 = u0,
                v0 = v0,
                u1 = u1,
                v1 = v1,
                imageWidth = imageW,
                imageHeight = imageH,
                bl = borderLeft,
                bt = borderTop,
                br = borderRight,
                bb = borderBottom,
                separatorPx = separatorPx
            )
        }
    }
}
