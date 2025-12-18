package github.kasuminova.prototypemachinery.integration.jei.runtime

import com.cleanroommc.modularui.screen.ClientScreenHandler
import com.cleanroommc.modularui.screen.ModularPanel
import com.cleanroommc.modularui.screen.ModularScreen
import com.cleanroommc.modularui.screen.UISettings
import github.kasuminova.prototypemachinery.PrototypeMachinery
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.GlStateManager
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import java.nio.FloatBuffer

/**
 * Minimal ModularUI runtime host for JEI.
 *
 * Unlike [JeiPanelRuntime], this runtime does not understand requirement nodes or JEI ingredient slots.
 * It only renders a prebuilt [ModularPanel] inside the JEI recipe area.
 */
public class JeiModularPanelRuntime(
    public val panel: ModularPanel,
    public val width: Int,
    public val height: Int,
) {

    private val screen: ModularScreen = ModularScreen("prototypemachinery", panel)

    private var constructedOverlayFor: GuiScreen? = null
    private var lastScaledW: Int = -1
    private var lastScaledH: Int = -1

    init {
        // Important: without settings, ModularGuiContext#getUISettings may throw when queried by other systems.
        screen.context.setSettings(UISettings())

        // JEI draws at arbitrary origins; we will position this panel explicitly per draw call.
        panel.pos(0, 0)
    }

    private fun ensureOverlay(host: GuiScreen): Boolean {
        if (constructedOverlayFor == null) {
            screen.constructOverlay(host)
            constructedOverlayFor = host
            lastScaledW = -1
            lastScaledH = -1
        } else if (constructedOverlayFor !== host) {
            PrototypeMachinery.logger.warn(
                "JEI: attempted to draw a cached JeiModularPanelRuntime on a different GuiScreen. " +
                    "Expected='${constructedOverlayFor!!::class.java.name}', actual='${host::class.java.name}'. " +
                    "Skipping draw; runtime should be rebuilt by JEI."
            )
            return false
        }

        val mc = Minecraft.getMinecraft()
        val sr = ScaledResolution(mc)
        val sw = sr.scaledWidth
        val sh = sr.scaledHeight

        if (sw != lastScaledW || sh != lastScaledH) {
            screen.onResize(sw, sh)
            lastScaledW = sw
            lastScaledH = sh
        }

        return true
    }

    /**
     * Draw this runtime at the given absolute screen origin (top-left of JEI recipe background).
     */
    public fun drawAt(
        originX: Int,
        originY: Int,
        mouseX: Int,
        mouseY: Int,
        partialTicks: Float,
    ) {
        val mc = Minecraft.getMinecraft()
        val host = mc.currentScreen ?: return
        if (!ensureOverlay(host)) return

        // JEI/HEI calls IRecipeWrapper#drawInfo with the GL matrix translated to the recipe origin.
        // Many ModularUI widgets (notably the 3D preview) use glScissor/glViewport which are *not*
        // affected by that translation. We therefore read and compensate the translation so that:
        // - panel coordinates are in absolute screen space
        // - scissor/viewport computations remain correct
        val (glTx, glTy) = readModelViewTranslationPx()

        // Position the panel in absolute screen coords.
        val absX = originX + glTx
        val absY = originY + glTy
        panel.pos(absX, absY)

        // Mark this runtime as active so we can forward mouse input events.
        JeiEmbeddedModularUiInputBridge.markActive(hostGui = host, runtime = this, x = absX, y = absY, w = width, h = height)

        // Update context mouse state for hover effects/tooltips in absolute screen coords.
        updateMouseState(mouseX + glTx, mouseY + glTy, partialTicks)

        // Update hover states (ModularUI computes hovering in onFrameUpdate).
        screen.onFrameUpdate()

        // Cancel JEI's translation while ModularUI draws, to avoid double-offset.
        GlStateManager.pushMatrix()
        GlStateManager.translate((-glTx).toFloat(), (-glTy).toFloat(), 0.0f)
        try {
            screen.drawScreen()
            screen.drawForeground()

            // Optional: ModularUI debug overlay (toggled by CTRL+SHIFT+ALT+C).
            // In vanilla ModularUI this is drawn by OverlayStack/ClientScreenHandler.
            ClientScreenHandler.drawDebugScreen(if (screen.context.isHovered) screen else null, screen)

            // ClientScreenHandler/GuiDraw debug routines may disable Texture2D/Alpha (e.g. via Platform.setupDrawColor()).
            // If we don't restore state, JEI elements drawn afterwards can appear "texture-less".
            GlStateManager.enableTexture2D()
            GlStateManager.enableAlpha()
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f)
        } finally {
            GlStateManager.popMatrix()
        }
    }

    internal fun updateMouseState(absMouseX: Int, absMouseY: Int, partialTicks: Float) {
        screen.context.updateState(absMouseX, absMouseY, partialTicks)
    }

    internal fun onMousePressed(button: Int): Boolean {
        return screen.onMousePressed(button)
    }

    internal fun onMouseRelease(button: Int): Boolean {
        return screen.onMouseRelease(button)
    }

    internal fun onMouseDrag(button: Int, timeSinceClickMs: Long): Boolean {
        return screen.onMouseDrag(button, timeSinceClickMs)
    }

    internal fun onMouseScroll(direction: com.cleanroommc.modularui.api.UpOrDown, amount: Int): Boolean {
        return screen.onMouseScroll(direction, amount)
    }

    internal fun onMouseInputPre(button: Int, pressed: Boolean): Boolean {
        return screen.onMouseInputPre(button, pressed)
    }

    internal fun onKeyPressed(typedChar: Char, keyCode: Int): Boolean {
        return screen.onKeyPressed(typedChar, keyCode)
    }

    internal fun onKeyRelease(typedChar: Char, keyCode: Int): Boolean {
        return screen.onKeyRelease(typedChar, keyCode)
    }

    internal fun onUpdateTick() {
        screen.onUpdate()
    }

    private fun readModelViewTranslationPx(): Pair<Int, Int> {
        val buf = MODELVIEW_BUF.get()
        buf.clear()
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, buf)
        // Column-major: translation is indices 12/13/14.
        val tx = buf.get(12)
        val ty = buf.get(13)
        return Pair(tx.toInt(), ty.toInt())
    }

    private companion object {
        private val MODELVIEW_BUF: ThreadLocal<FloatBuffer> = ThreadLocal.withInitial {
            BufferUtils.createFloatBuffer(16)
        }
    }
}
