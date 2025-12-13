package github.kasuminova.prototypemachinery.impl.ui.registry

import github.kasuminova.prototypemachinery.api.ui.definition.PanelDefinition
import net.minecraft.util.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MachineUIRegistryImplTest {

    @BeforeEach
    fun reset() {
        MachineUIRegistryImpl.clearAll()
    }

    @Test
    fun `resolve returns null when empty`() {
        assertNull(MachineUIRegistryImpl.resolve(ResourceLocation("test", "machine")))
    }

    @Test
    fun `higher priority wins`() {
        val id = ResourceLocation("test", "machine")
        val low = PanelDefinition(width = 10, height = 10)
        val high = PanelDefinition(width = 20, height = 20)

        MachineUIRegistryImpl.register(id, low, priority = 0, owner = "a")
        MachineUIRegistryImpl.register(id, high, priority = 10, owner = "b")

        assertEquals(high, MachineUIRegistryImpl.resolve(id))
    }

    @Test
    fun `last registration wins when same priority`() {
        val id = ResourceLocation("test", "machine")
        val first = PanelDefinition(width = 10, height = 10)
        val second = PanelDefinition(width = 11, height = 11)

        MachineUIRegistryImpl.register(id, first, priority = 5, owner = "a")
        MachineUIRegistryImpl.register(id, second, priority = 5, owner = "b")

        assertEquals(second, MachineUIRegistryImpl.resolve(id))
    }

    @Test
    fun `clear removes registrations`() {
        val id = ResourceLocation("test", "machine")
        MachineUIRegistryImpl.register(id, PanelDefinition(width = 10, height = 10), priority = 0, owner = "a")

        MachineUIRegistryImpl.clear(id)
        assertNull(MachineUIRegistryImpl.resolve(id))
    }
}
