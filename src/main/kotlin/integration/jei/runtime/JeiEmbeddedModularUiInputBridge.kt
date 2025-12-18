package github.kasuminova.prototypemachinery.integration.jei.runtime

import com.cleanroommc.modularui.ModularUIConfig
import com.cleanroommc.modularui.api.UpOrDown
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.ScaledResolution
import net.minecraftforge.client.event.GuiScreenEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse

/**
 * Enables mouse interaction for ModularUI panels rendered inside JEI/HEI recipe GUI.
 *
 * JEI's [mezz.jei.api.recipe.IRecipeCategory] API does not provide mouse click callbacks,
 * so we hook Forge GUI input events and forward them to the active [JeiModularPanelRuntime].
 */
@SideOnly(Side.CLIENT)
internal object JeiEmbeddedModularUiInputBridge {

    private data class Active(
        val hostGui: GuiScreen,
        val runtime: JeiModularPanelRuntime,
        val x: Int,
        val y: Int,
        val w: Int,
        val h: Int,
        val touchedAtMs: Long,
    )

    @Volatile
    private var active: Active? = null

    @Volatile
    private var registered: Boolean = false

    private var pressedButton: Int = -1
    private var pressedAtMs: Long = -1L
    private var lastDragX: Int = Int.MIN_VALUE
    private var lastDragY: Int = Int.MIN_VALUE

    private var lastChar: Char? = null

    fun register() {
        if (registered) return
        MinecraftForge.EVENT_BUS.register(this)
        registered = true
    }

    fun markActive(hostGui: GuiScreen, runtime: JeiModularPanelRuntime, x: Int, y: Int, w: Int, h: Int) {
        active = Active(hostGui = hostGui, runtime = runtime, x = x, y = y, w = w, h = h, touchedAtMs = Minecraft.getSystemTime())
    }

    private fun isFresh(a: Active): Boolean {
        // If we haven't been re-marked recently, we're probably no longer drawing the embedded UI.
        // This prevents swallowing mouse input when the player switches to a different JEI category.
        return (Minecraft.getSystemTime() - a.touchedAtMs) <= 250L
    }

    private fun scaledMouse(): Pair<Int, Int> {
        val mc = Minecraft.getMinecraft()
        val sr = ScaledResolution(mc)
        val mx = Mouse.getEventX() * sr.scaledWidth / mc.displayWidth
        val my = sr.scaledHeight - (Mouse.getEventY() * sr.scaledHeight / mc.displayHeight) - 1
        return Pair(mx, my)
    }

    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.END) return

        val a = active ?: return
        val mc = Minecraft.getMinecraft()
        if (mc.currentScreen !== a.hostGui) {
            active = null
            return
        }
        if (!isFresh(a)) {
            active = null
            return
        }

        // Some widgets/animations/state machines advance on tick (20 TPS).
        a.runtime.onUpdateTick()
    }

    private fun isInside(a: Active, mx: Int, my: Int): Boolean {
        return mx >= a.x && my >= a.y && mx < (a.x + a.w) && my < (a.y + a.h)
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onMouseInput(event: GuiScreenEvent.MouseInputEvent.Pre) {
        val a = active ?: return
        if (event.gui !== a.hostGui) return

        if (!isFresh(a)) {
            active = null
            return
        }

        val (mx, my) = scaledMouse()
        if (!isInside(a, mx, my)) return

        val mc = Minecraft.getMinecraft()
        val pt = mc.renderPartialTicks

        // Keep ModularUI mouse state fresh for hover/tooltips.
        a.runtime.updateMouseState(mx, my, pt)

        val button = Mouse.getEventButton()
        val down = Mouse.getEventButtonState()

        // Mouse wheel
        val wheel = Mouse.getEventDWheel()
        if (wheel != 0) {
            val dir = if (wheel > 0) UpOrDown.UP else UpOrDown.DOWN
            // Match ModularUI's default behavior (see ClientScreenHandler): pass raw abs(wheel)
            // so embedded UIs behave the same as /pm_preview_ui.
            val amount = kotlin.math.abs(wheel)
            // Always consume wheel while hovering the embedded preview to prevent JEI page scrolling.
            // (Even if the runtime decides not to act on it.)
            a.runtime.onMouseScroll(dir, amount)
            event.isCanceled = true
            return
        }

        // Press / release
        if (button != -1) {
            if (down) {
                pressedButton = button
                pressedAtMs = Minecraft.getSystemTime()
                lastDragX = mx
                lastDragY = my

                // Pre-handler is used by ModularUI to drop draggables consistently.
                if (a.runtime.onMouseInputPre(button, true) || a.runtime.onMousePressed(button)) {
                    event.isCanceled = true
                }
            } else {
                // Release
                pressedButton = -1
                pressedAtMs = -1L
                lastDragX = Int.MIN_VALUE
                lastDragY = Int.MIN_VALUE

                if (a.runtime.onMouseInputPre(button, false) || a.runtime.onMouseRelease(button)) {
                    event.isCanceled = true
                }
            }
            return
        }

        // Drag (button == -1, movement event)
        if (pressedButton != -1 && Mouse.isButtonDown(pressedButton)) {
            if (lastDragX != mx || lastDragY != my) {
                lastDragX = mx
                lastDragY = my
                val since = if (pressedAtMs > 0L) (Minecraft.getSystemTime() - pressedAtMs) else 0L
                if (a.runtime.onMouseDrag(pressedButton, since)) {
                    event.isCanceled = true
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onKeyboardInput(event: GuiScreenEvent.KeyboardInputEvent.Pre) {
        val a = active ?: return
        if (event.gui !== a.hostGui) return

        if (!isFresh(a)) {
            active = null
            return
        }

        val (mx, my) = scaledMouse()
        if (!isInside(a, mx, my)) return

        val c0 = Keyboard.getEventCharacter()
        val key = Keyboard.getEventKey()
        val state = Keyboard.getEventKeyState()

        if (state) {
            // pressing a key
            lastChar = c0

            // Mirror ModularUI debug toggle: CTRL + SHIFT + ALT + C
            if (key == 46 && GuiScreen.isCtrlKeyDown() && GuiScreen.isShiftKeyDown() && GuiScreen.isAltKeyDown()) {
                ModularUIConfig.guiDebugMode = !ModularUIConfig.guiDebugMode
                event.isCanceled = true
                return
            }

            if (a.runtime.onKeyPressed(c0, key)) {
                event.isCanceled = true
            }
        } else {
            // releasing a key
            val typed = lastChar ?: return
            if (a.runtime.onKeyRelease(typed, key)) {
                event.isCanceled = true
            }
        }
    }
}
