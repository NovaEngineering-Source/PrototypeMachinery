package github.kasuminova.prototypemachinery.impl.machine.attribute

import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeModifier
import github.kasuminova.prototypemachinery.api.machine.attribute.StandardMachineAttributes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class MachineAttributeOverlayAndCacheTest {

    @Test
    fun `overlay map keeps per-process modifiers isolated`() {
        val machineMap = MachineAttributeMapImpl()
        val machineSpeed = machineMap.getOrCreateAttribute(StandardMachineAttributes.PROCESS_SPEED, defaultBase = 0.85)

        val processA = OverlayMachineAttributeMapImpl(parent = machineMap, defaultBase = 1.0)
        val processB = OverlayMachineAttributeMapImpl(parent = machineMap, defaultBase = 1.0)

        val speedA = processA.getOrCreateAttribute(StandardMachineAttributes.PROCESS_SPEED, defaultBase = 1.0)
        speedA.addModifier(MachineAttributeModifierImpl.multiplyTotal("x1.5", 0.5, adder = "processA"))

        val speedB = processB.getOrCreateAttribute(StandardMachineAttributes.PROCESS_SPEED, defaultBase = 1.0)

        assertEquals(0.85, machineSpeed.value, 1e-9)
        assertEquals(1.275, speedA.value, 1e-9)
        assertEquals(0.85, speedB.value, 1e-9)
    }

    @Test
    fun `MachineAttributeInstanceImpl caches computed value and invalidates on change`() {
        val applyCount = AtomicInteger(0)

        val instance = MachineAttributeInstanceImpl(StandardMachineAttributes.PROCESS_SPEED, base = 1.0)
        instance.addModifier(object : MachineAttributeModifier {
            override val id: String = "plus1"
            override val amount: Double = 1.0
            override val operation: MachineAttributeModifier.Operation = MachineAttributeModifier.Operation.ADDITION
            override val adder: Any = "test"

            override fun apply(base: Double, current: Double): Double {
                applyCount.incrementAndGet()
                return current + amount
            }
        })

        // first compute
        assertEquals(2.0, instance.value, 1e-9)
        // cached
        assertEquals(2.0, instance.value, 1e-9)
        assertEquals(1, applyCount.get(), "expected cached value to avoid re-applying modifiers")

        // base change invalidates
        instance.base = 2.0
        assertEquals(3.0, instance.value, 1e-9)
        assertEquals(2, applyCount.get(), "expected base change to invalidate cached value")

        // remove modifier invalidates (and stops applying)
        instance.removeModifier("plus1")
        assertEquals(2.0, instance.value, 1e-9)
        assertEquals(2, applyCount.get(), "expected removed modifier to no longer apply")
    }

    @Test
    fun `MachineAttributeMapImpl can roundtrip NBT`() {
        val map = MachineAttributeMapImpl()
        val speed = map.getOrCreateAttribute(StandardMachineAttributes.PROCESS_SPEED, defaultBase = 0.85)
        speed.addModifier(MachineAttributeModifierImpl.multiplyTotal("x1.5", 0.5, adder = "unit-test"))

        val tag = MachineAttributeNbt.writeMachineMap(map)

        val restored = MachineAttributeMapImpl()
        MachineAttributeNbt.readMachineMap(tag, restored)

        val restoredSpeed = restored.getOrCreateAttribute(StandardMachineAttributes.PROCESS_SPEED, defaultBase = 1.0)
        assertEquals(1.275, restoredSpeed.value, 1e-9)
        val mod = restoredSpeed.getModifier("x1.5")
        assertEquals(MachineAttributeModifier.Operation.MULTIPLY_TOTAL, mod?.operation)
    }

    @Test
    fun `OverlayMachineAttributeMapImpl can roundtrip local changes NBT`() {
        val machineMap = MachineAttributeMapImpl()
        machineMap.getOrCreateAttribute(StandardMachineAttributes.PROCESS_SPEED, defaultBase = 0.85)

        val process = OverlayMachineAttributeMapImpl(parent = machineMap, defaultBase = 1.0)
        val speed = process.getOrCreateAttribute(StandardMachineAttributes.PROCESS_SPEED, defaultBase = 1.0)
        speed.addModifier(MachineAttributeModifierImpl.multiplyTotal("x1.5", 0.5, adder = "process"))

        val tag = MachineAttributeNbt.writeOverlayLocal(process)

        val restored = OverlayMachineAttributeMapImpl(parent = machineMap, defaultBase = 1.0)
        MachineAttributeNbt.readOverlayLocal(tag, restored)

        val restoredSpeed = restored.getOrCreateAttribute(StandardMachineAttributes.PROCESS_SPEED, defaultBase = 1.0)
        assertEquals(1.275, restoredSpeed.value, 1e-9)
        val mod = restoredSpeed.getModifier("x1.5")
        assertEquals(MachineAttributeModifier.Operation.MULTIPLY_TOTAL, mod?.operation)
    }
}
