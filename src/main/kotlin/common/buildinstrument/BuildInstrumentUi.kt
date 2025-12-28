package github.kasuminova.prototypemachinery.common.buildinstrument

import com.cleanroommc.modularui.api.ISyncedAction
import com.cleanroommc.modularui.drawable.UITexture
import com.cleanroommc.modularui.screen.ModularPanel
import com.cleanroommc.modularui.screen.UISettings
import com.cleanroommc.modularui.value.sync.PanelSyncManager
import com.cleanroommc.modularui.widgets.ButtonWidget
import com.cleanroommc.modularui.widgets.TextWidget
import com.cleanroommc.modularui.widgets.layout.Column
import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.common.buildinstrument.BuildInstrumentNbt.TaskState
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.network.PacketBuffer
import net.minecraft.util.ResourceLocation
import java.awt.Color

/**
 * Build Instrument UI (synced PlayerInventory GUI).
 *
 * 说明：该面板会在服务端也被构建（用于收集 sync values），因此这里必须避免任何 net.minecraft.client.* 引用。
 */
internal object BuildInstrumentUi {

    private const val PANEL_W = 384
    private const val PANEL_H = 228

    private const val BG_TEX_H = 256

    private const val ACTION_KEY = "pm:build_instrument_action"

    private fun guiTex(path: String): UITexture {
        // ResourceLocation path is without textures/ prefix and without .png
        // e.g. textures/gui/gui_build_instrument/build_instrument_base.png -> gui/gui_build_instrument/build_instrument_base
        return UITexture.fullImage(ResourceLocation(PrototypeMachinery.MOD_ID, "gui/gui_build_instrument/$path"))
    }

    private fun buttonTex(u: Int, v: Int): UITexture {
        return UITexture.builder()
            .location(ResourceLocation(PrototypeMachinery.MOD_ID, "gui/gui_build_instrument/button"))
            .imageSize(80, 80)
            .subAreaXYWH(u, v, 20, 14)
            .build()
    }

    // NOTE: base texture is 384x256, but the GUI panel height is 228.
    // We crop the bottom area to avoid stretching and to keep pixel-perfect alignment.
    private val BG = UITexture.builder()
        .location(ResourceLocation(PrototypeMachinery.MOD_ID, "gui/gui_build_instrument/build_instrument_base"))
        .imageSize(PANEL_W, BG_TEX_H)
        .subAreaXYWH(0, 0, PANEL_W, PANEL_H)
        .build()

    // task_status_button textures (from docs)
    private val BTN_START_BUILD_N = buttonTex(0, 0)
    private val BTN_START_BUILD_H = buttonTex(21, 0)
    private val BTN_START_BUILD_P = buttonTex(42, 0)

    private val BTN_BUILDING_N = buttonTex(0, 15)
    private val BTN_BUILDING_H = buttonTex(21, 15)
    private val BTN_BUILDING_P = buttonTex(42, 15)

    private val BTN_PAUSED_N = buttonTex(0, 30)
    private val BTN_PAUSED_H = buttonTex(21, 30)
    private val BTN_PAUSED_P = buttonTex(42, 30)

    private val BTN_START_DIS_N = buttonTex(0, 45)
    private val BTN_START_DIS_H = buttonTex(21, 45)
    private val BTN_START_DIS_P = buttonTex(42, 45)

    private val BTN_DIS_N = buttonTex(0, 60)
    private val BTN_DIS_H = buttonTex(21, 60)
    private val BTN_DIS_P = buttonTex(42, 60)

    // progress bar colors (docs)
    private val COLOR_BUILD = Color(0x17, 0xB8, 0x6D)
    private val COLOR_BUILD_TAIL = Color(0x07, 0x9B, 0x6B)
    private val COLOR_PAUSE = Color(0xEC, 0xA2, 0x3C)
    private val COLOR_PAUSE_TAIL = Color(0xD9, 0x78, 0x2F)
    private val COLOR_DIS = Color(0xD7, 0x3E, 0x42)
    private val COLOR_DIS_TAIL = Color(0xAA, 0x21, 0x2B)

    enum class Action(val id: Int) {
        CLICK_TASK_BUTTON(0),
        SET_MATERIAL_SELECTION(1)
    }

    fun build(data: com.cleanroommc.modularui.factory.PlayerInventoryGuiData, syncManager: PanelSyncManager, settings: UISettings): ModularPanel {
        // Register server-side action endpoint (client will call it on button press)
        syncManager.registerSyncedAction(
            ACTION_KEY,
            /* executeClient = */ false,
            /* executeServer = */ true,
            ISyncedAction { buf ->
                val actionId = buf.readVarInt()
                when (actionId) {
                    Action.CLICK_TASK_BUTTON.id -> {
                        BuildInstrumentTaskManager.handleTaskButtonPressed(data.player, data.slotIndex)
                    }

                    Action.SET_MATERIAL_SELECTION.id -> {
                        val requirementKey = buf.readString(32767)
                        val selectedOptionKey = buf.readString(32767)
                        val stack = data.usedItemStack
                        if (!stack.hasTagCompound()) stack.tagCompound = NBTTagCompound()
                        BuildInstrumentNbt.writeMaterialSelection(stack.tagCompound!!, requirementKey, selectedOptionKey)
                    }
                }
            }
        )

        val panel = ModularPanel.defaultPanel("build_instrument")
            .size(PANEL_W, PANEL_H)
            .background(BG)

        // Root container (absolute positioning)
        val root = Column().pos(0, 0).size(PANEL_W, PANEL_H)
        panel.child(root)

        val tagProvider: () -> NBTTagCompound? = {
            data.player.inventory.getStackInSlot(data.slotIndex).tagCompound
        }

        // Quick hint if unbound
        if (!BuildInstrumentNbt.isBound(tagProvider())) {
            root.child(
                TextWidget("未绑定控制器：请右键控制器进行绑定")
                    .pos(10, PANEL_H - 21)
                    .color(com.cleanroommc.modularui.utils.Color.WHITE.main)
                    .shadow(true)
            )
        }

        // --- Client-side widgets ---
        // IMPORTANT: do NOT use FMLCommonHandler#side here.
        // In integrated server, physical side is CLIENT even on the server thread.
        // Using world.isRemote guarantees we only add client-only widgets on the client.
        if (data.player.world.isRemote) {
            PrototypeMachinery.proxy.addBuildInstrumentClientWidgets(root, tagProvider, syncManager)
        }

        // --- Task status button ---
        val taskButton = TaskButton(tagProvider)
            .pos(357, 205)
            .size(20, 14)
            .apply {
                onMousePressed { mouseButton ->
                    if (mouseButton != 0) return@onMousePressed false
                    // Call server action.
                    syncManager.callSyncedAction(ACTION_KEY) { p: PacketBuffer ->
                        p.writeVarInt(Action.CLICK_TASK_BUTTON.id)
                    }
                    true
                }
            }
        root.child(taskButton)

        return panel
    }

    private class TaskButton(private val tagProvider: () -> NBTTagCompound?) : ButtonWidget<TaskButton>() {
        override fun getCurrentBackground(
            theme: com.cleanroommc.modularui.api.ITheme,
            widgetTheme: com.cleanroommc.modularui.theme.WidgetThemeEntry<*>
        ): com.cleanroommc.modularui.api.drawable.IDrawable? {
            val hovering = isHovering
            val state = BuildInstrumentNbt.readTaskState(tagProvider())
            return when (state) {
                TaskState.IDLE -> if (hovering) BTN_START_BUILD_H else BTN_START_BUILD_N
                TaskState.BUILDING -> if (hovering) BTN_BUILDING_H else BTN_BUILDING_N
                TaskState.PAUSED_BUILDING -> if (hovering) BTN_PAUSED_H else BTN_PAUSED_N
                TaskState.DISASSEMBLING -> if (hovering) BTN_DIS_H else BTN_DIS_N
                TaskState.PAUSED_DISASSEMBLING -> if (hovering) BTN_PAUSED_H else BTN_PAUSED_N
            }
        }
    }
}
