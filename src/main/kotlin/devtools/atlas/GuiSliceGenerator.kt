package github.kasuminova.prototypemachinery.devtools.atlas

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Build-time tool: slice large GUI sheets into small textures.
 *
 * This tool is executed from Gradle (JavaExec) and writes outputs into build/ generated resources.
 * Call sites in game should ONLY reference the generated small textures by ResourceLocation.
 */
internal object GuiSliceGenerator {

    @Serializable
    internal data class Manifest(
        /** Path to the source PNG on disk, relative to project root. */
        val source: String,
        val imageWidth: Int,
        val imageHeight: Int,
        val outputNamespace: String,
        /** Root path under textures/ (no leading textures/). Example: gui/preview/slices */
        val outputRoot: String,
        /** Name used for generated atlas index file. Example: gui_preview */
        val atlasId: String,
        /** Default padding (in pixels) for all sprites, can be overridden per sprite. */
        val pad: Int = 0,
        val sprites: List<Sprite>
    )

    @Serializable
    internal data class Sprite(
        /** Full sprite id as ResourceLocation string (namespace:path WITHOUT textures/ prefix and .png suffix). */
        val id: String,
        val x: Int,
        val y: Int,
        val w: Int,
        val h: Int,
        /** Optional per-sprite padding override. */
        val pad: Int? = null
    )

    @Serializable
    internal data class AtlasIndex(
        val atlasId: String,
        val sprites: List<String>
    )

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    @JvmStatic
    public fun main(args: Array<String>) {
        val projectRoot = args.getOrNull(0)?.let(::File) ?: File(".").absoluteFile
        val outRoot = args.getOrNull(1)?.let(::File) ?: File(projectRoot, "build/generated/gui-slices")

        val manifestsDir = File(projectRoot, "src/main/resources/assets/prototypemachinery/pm_gui_slices")
        require(manifestsDir.isDirectory) {
            "Missing manifests dir: ${manifestsDir.absolutePath}"
        }

        val atlasIndexSprites = LinkedHashSet<String>()
        var atlasId: String? = null

        manifestsDir.listFiles { f -> f.isFile && f.extension.equals("json", ignoreCase = true) }
            ?.sortedBy { it.name }
            ?.forEach { file ->
                val manifest = json.decodeFromString(Manifest.serializer(), file.readText(Charsets.UTF_8))
                if (atlasId == null) atlasId = manifest.atlasId
                if (atlasId != manifest.atlasId) {
                    throw IllegalStateException("Multiple atlasId in manifests not supported yet: $atlasId vs ${manifest.atlasId}")
                }

                val srcPng = File(projectRoot, manifest.source)
                require(srcPng.isFile) { "Missing source png: ${srcPng.absolutePath}" }

                val sheet = ImageIO.read(srcPng)
                require(sheet.width == manifest.imageWidth && sheet.height == manifest.imageHeight) {
                    "Sheet size mismatch for ${srcPng.name}: expected ${manifest.imageWidth}x${manifest.imageHeight}, got ${sheet.width}x${sheet.height}"
                }

                for (sprite in manifest.sprites) {
                    val pad = sprite.pad ?: manifest.pad
                    val outFile = spriteOutFile(outRoot, sprite.id)
                    outFile.parentFile.mkdirs()

                    val img = sliceWithPadding(sheet, sprite.x, sprite.y, sprite.w, sprite.h, pad)
                    ImageIO.write(img, "PNG", outFile)

                    atlasIndexSprites.add(sprite.id)
                }
            }

        // Write atlas index for runtime enumeration.
        val finalAtlasId = atlasId ?: "gui_preview"
        val index = AtlasIndex(finalAtlasId, atlasIndexSprites.toList())
        val indexFile = File(outRoot, "assets/prototypemachinery/pm_gui_atlas/${finalAtlasId}.json")
        indexFile.parentFile.mkdirs()
        indexFile.writeText(json.encodeToString(AtlasIndex.serializer(), index), Charsets.UTF_8)

        println("[GuiSliceGenerator] Wrote ${atlasIndexSprites.size} sprites + atlas index to: ${outRoot.absolutePath}")
    }

    private fun spriteOutFile(outRoot: File, spriteId: String): File {
        val parts = spriteId.split(":", limit = 2)
        require(parts.size == 2) { "Invalid sprite id (expected namespace:path): $spriteId" }
        val ns = parts[0]
        val path = parts[1]
        return File(outRoot, "assets/$ns/textures/$path.png")
    }

    private fun sliceWithPadding(sheet: BufferedImage, x: Int, y: Int, w: Int, h: Int, pad: Int): BufferedImage {
        require(w > 0 && h > 0) { "Invalid sprite size: ${w}x$h" }
        require(x >= 0 && y >= 0 && x + w <= sheet.width && y + h <= sheet.height) {
            "Sprite rect out of bounds: ($x,$y,$w,$h) in ${sheet.width}x${sheet.height}"
        }
        if (pad <= 0) {
            return sheet.getSubimage(x, y, w, h)
        }

        val out = BufferedImage(w + pad * 2, h + pad * 2, BufferedImage.TYPE_INT_ARGB)

        // Copy center
        for (dy in 0 until h) {
            for (dx in 0 until w) {
                out.setRGB(dx + pad, dy + pad, sheet.getRGB(x + dx, y + dy))
            }
        }

        // Extend edges (clamp)
        for (dy in 0 until h) {
            val left = sheet.getRGB(x, y + dy)
            val right = sheet.getRGB(x + w - 1, y + dy)
            for (p in 0 until pad) {
                out.setRGB(p, dy + pad, left)
                out.setRGB(pad + w + p, dy + pad, right)
            }
        }
        for (dx in 0 until w) {
            val top = sheet.getRGB(x + dx, y)
            val bottom = sheet.getRGB(x + dx, y + h - 1)
            for (p in 0 until pad) {
                out.setRGB(dx + pad, p, top)
                out.setRGB(dx + pad, pad + h + p, bottom)
            }
        }

        // Corners
        val tl = sheet.getRGB(x, y)
        val tr = sheet.getRGB(x + w - 1, y)
        val bl = sheet.getRGB(x, y + h - 1)
        val br = sheet.getRGB(x + w - 1, y + h - 1)
        for (py in 0 until pad) {
            for (px in 0 until pad) {
                out.setRGB(px, py, tl)
                out.setRGB(pad + w + px, py, tr)
                out.setRGB(px, pad + h + py, bl)
                out.setRGB(pad + w + px, pad + h + py, br)
            }
        }

        return out
    }
}
