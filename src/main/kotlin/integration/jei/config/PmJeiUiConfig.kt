package github.kasuminova.prototypemachinery.integration.jei.config

import github.kasuminova.prototypemachinery.PrototypeMachinery
import net.minecraftforge.common.config.Config

/**
 * Forge @Config for JEI UI-related tweaks.
 *
 * 配置文件位置：config/${PrototypeMachinery.MOD_ID}_jei.cfg
 *
 * NOTE:
 * We intentionally keep this in a separate config file to avoid merging unrelated categories into
 * the main `${PrototypeMachinery.MOD_ID}.cfg` (which is already used by other @Config classes).
 */
@Config(modid = PrototypeMachinery.MOD_ID, name = "${PrototypeMachinery.MOD_ID}_jei")
public object PmJeiUiConfig {

    @JvmField
    public var ui: Ui = Ui()

    public class Ui {

        /**
         * Y offset (pixels) for the tiny energy IO led texture drawn at the very top of JEI energy bars.
         *
         * 0 means "exactly at the top edge".
         * Negative values move it up; positive values move it down.
         */
        @JvmField
        public var energyLedYOffset: Int = 0
    }
}
