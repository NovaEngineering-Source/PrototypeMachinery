package github.kasuminova.prototypemachinery.impl.ecs

import github.kasuminova.prototypemachinery.api.ecs.TopologicalComponentMap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TopologicalComponentMapTest {

    private lateinit var map: TopologicalComponentMap<String, String>

    @BeforeEach
    fun setUp() {
        // Initialize map
        map = TopologicalComponentMapImpl()
    }


    @Test
    fun `test basic add and order`() {
        map.add("A", "ComponentA")
        map.add("B", "ComponentB", setOf("A"))
        map.add("C", "ComponentC", setOf("B"))

        val order = map.orderedComponents.map { it.key }
        assertEquals(listOf("A", "B", "C"), order)
    }

    @Test
    fun `test add after`() {
        map.add("A", "ComponentA")
        map.addAfter("A", "B", "ComponentB")

        val order = map.orderedComponents.map { it.key }
        assertEquals(listOf("A", "B"), order)

        // Add C after B
        map.addAfter("B", "C", "ComponentC")
        val order2 = map.orderedComponents.map { it.key }
        assertEquals(listOf("A", "B", "C"), order2)
    }

    @Test
    fun `test add before`() {
        map.add("A", "ComponentA")
        map.addBefore("A", "B", "ComponentB")

        val order = map.orderedComponents.map { it.key }
        assertEquals(listOf("B", "A"), order)
    }

    @Test
    fun `test add first`() {
        map.add("A", "ComponentA")
        map.add("B", "ComponentB")
        // Currently A and B are roots.

        map.addFirst("C", "ComponentC")

        val order = map.orderedComponents.map { it.key }
        assertEquals("C", order[0])
        assertTrue(order.containsAll(listOf("A", "B", "C")))
    }

    @Test
    fun `test add tail`() {
        map.add("A", "ComponentA")
        map.add("B", "ComponentB")

        map.addTail("C", "ComponentC")

        val order = map.orderedComponents.map { it.key }
        assertEquals("C", order.last())
    }

    @Test
    fun `test soft dependencies`() {
        // Add A depending on B, but B doesn't exist yet
        map.add("A", "ComponentA", setOf("B"))

        // Should only contain A
        assertEquals(listOf("A"), map.orderedComponents.map { it.key })

        // Add B
        map.add("B", "ComponentB")

        // Now should be B, A
        assertEquals(listOf("B", "A"), map.orderedComponents.map { it.key })
    }

    @Test
    fun `test add before with soft dependency`() {
        // Add B before A, but A doesn't exist yet
        map.addBefore("A", "B", "ComponentB")

        // Should contain B
        assertEquals(listOf("B"), map.orderedComponents.map { it.key })

        // Add A
        map.add("A", "ComponentA")

        // Should be B, A
        assertEquals(listOf("B", "A"), map.orderedComponents.map { it.key })
    }

    @Test
    fun `test cycle detection`() {
        map.add("A", "ComponentA", setOf("B"))

        assertThrows<IllegalStateException> {
            map.add("B", "ComponentB", setOf("A"))
            // Accessing orderedComponents triggers the sort and cycle check
            map.orderedComponents
        }
    }

    @Test
    fun `test remove`() {
        map.add("A", "ComponentA")
        map.add("B", "ComponentB", setOf("A"))

        assertEquals(listOf("A", "B"), map.orderedComponents.map { it.key })

        map.remove("A")

        assertEquals(listOf("B"), map.orderedComponents.map { it.key })
    }

    @Test
    fun `test get`() {
        map.add("A", "ComponentA")
        assertEquals("ComponentA", map.get("A"))
        assertNull(map.get("B"))
    }

    @Test
    fun `test complex insertion order`() {
        // 1. 首先添加 B
        map.add("B", "ComponentB")

        // 2. 使用 addLast (addTail) 添加 E
        map.addTail("E", "ComponentE")

        // 3. 添加 D，要求在 C 之后，在 E 之前 (此时 C 尚未存在，测试软依赖)
        // Use addBefore to establish D -> E dependency
        map.addBefore("E", "D", "ComponentD")
        // Update D to establish C -> D dependency
        map.add("D", "ComponentD", setOf("C"))

        // 4. 添加 C，要求在 B 之后，在 E 之前
        // Use addBefore to establish C -> E dependency
        map.addBefore("E", "C", "ComponentC")
        // Update C to establish B -> C dependency
        map.add("C", "ComponentC", setOf("B"))

        // 5. 使用 addFirst 添加 A
        map.addFirst("A", "ComponentA")

        val order = map.orderedComponents.map { it.key }
        println("Actual order: $order")
        assertEquals(listOf("A", "B", "C", "D", "E"), order)
    }
}
