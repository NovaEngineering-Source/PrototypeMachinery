package github.kasuminova.prototypemachinery.common.structure.tools

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class MmceStructureMigrationUtilTest {

    @Test
    fun `modifiers are merged as alternatives`() {
      val tmp = createTempFile(prefix = "mmce_machine_", suffix = ".json")
        tmp.writeText(
            """
            {
              "registryname": "example_machine",
              "parts": [
                { "x": -1, "y": 0, "z": 0, "elements": ["modularmachinery:blockenergyinputhatch@0"] }
              ],
              "modifiers": [
                { "x": -1, "y": 0, "z": 0, "elements": "modularmachinery:blockenergyinputhatch@4" },
                { "x": -1, "y": 0, "z": 0, "elements": "modularmachinery:blockenergyinputhatch@5" }
              ]
            }
            """.trimIndent()
        )

        val result = MmceStructureMigrationUtil.migrateMachineJson(
            machineJsonFile = tmp,
            variableContext = emptyMap(),
            outStructureId = null,
            includeNbtConstraints = false,
            dynamicPatternMode = MmceStructureMigrationUtil.DynamicPatternMode.IGNORE,
            dynamicPatternFixedSize = null,
        )

        assertEquals(1, result.structure.pattern.size)
        val el = result.structure.pattern.single()
        assertEquals(-1, el.pos.x)
        assertEquals(0, el.pos.y)
        assertEquals(0, el.pos.z)

        // base + 2 modifier alternatives
        val alt = el.alternatives ?: emptyList()
        assertTrue(alt.isNotEmpty(), "expected alternatives from modifiers")
        // At least 2 alternatives because base is represented by (blockId, meta) and modifiers should add more.
        assertTrue(alt.size >= 2)
    }

    @Test
    fun `dynamic patterns are materialized with fixed size`() {
      val tmp = createTempFile(prefix = "mmce_machine_dyn_", suffix = ".json")
        tmp.writeText(
            """
            {
              "registryname": "dyn_machine",
              "dynamic-patterns": [
                {
                  "name": "workers",
                  "minSize": 1,
                  "maxSize": 4,
                  "structure-size-offset-start": {"x": -2, "y": 0, "z": 0},
                  "structure-size-offset": {"x": -1, "y": 0, "z": 0},
                  "parts": [
                    { "x": 0, "y": 0, "z": 0, "elements": ["modularmachinery:blockcasing@0"] }
                  ],
                  "parts-end": [
                    { "x": 0, "y": 0, "z": 0, "elements": ["modularmachinery:blockcasing@1"] }
                  ]
                }
              ],
              "parts": [
                { "x": 1, "y": 0, "z": 0, "elements": ["modularmachinery:blockcasing@2"] }
              ]
            }
            """.trimIndent()
        )

        val result = MmceStructureMigrationUtil.migrateMachineJson(
            machineJsonFile = tmp,
            variableContext = emptyMap(),
            outStructureId = null,
            includeNbtConstraints = false,
            dynamicPatternMode = MmceStructureMigrationUtil.DynamicPatternMode.FIXED,
            dynamicPatternFixedSize = 2,
        )

        // Base part stays in the root template.
        assertTrue(result.structure.pattern.any { it.pos.x == 1 && it.pos.y == 0 && it.pos.z == 0 })

        // Dynamic pattern should be migrated into child structures.
        assertTrue(result.structure.children.isNotEmpty(), "expected dynamic pattern to be referenced as child")
        assertTrue(result.additionalStructures.isNotEmpty(), "expected additional child structures to be generated")

        val slice = result.additionalStructures.firstOrNull { it.type == "slice" }
        assertTrue(slice != null, "expected a slice structure")
        slice!!

        assertEquals(2, slice.minCount)
        assertEquals(2, slice.maxCount)
        assertEquals(-1, slice.sliceOffset!!.x)
        assertEquals(0, slice.sliceOffset!!.y)
        assertEquals(0, slice.sliceOffset!!.z)

        // slice contains one block at local origin
        assertTrue(slice.pattern.any { it.pos.x == 0 && it.pos.y == 0 && it.pos.z == 0 })

        // Should warn that it migrated dynamic pattern
        assertTrue(result.warnings.any { it.contains("dynamic-pattern") })
    }

    private fun createTempFile(prefix: String, suffix: String): File =
      Files.createTempFile(prefix, suffix).toFile().also { it.deleteOnExit() }
}
