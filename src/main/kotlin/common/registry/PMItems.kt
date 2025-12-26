package github.kasuminova.prototypemachinery.common.registry

import github.kasuminova.prototypemachinery.common.item.BuildInstrumentItem
import github.kasuminova.prototypemachinery.common.item.ControllerOrientationToolItem
import github.kasuminova.prototypemachinery.common.item.ScannerInstrumentItem

/**
 * Central place to hold non-block items.
 */
internal object PMItems {
    val controllerOrientationTool = ControllerOrientationToolItem()
    val scannerInstrument = ScannerInstrumentItem()
    val buildInstrument = BuildInstrumentItem()
}
