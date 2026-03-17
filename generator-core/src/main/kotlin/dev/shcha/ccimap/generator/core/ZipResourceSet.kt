package dev.shcha.ccimap.generator.core

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.imageio.ImageIO

internal class ZipResourceSet(
    jarPaths: List<Path>,
    private val objectMapper: ObjectMapper
) : AutoCloseable {
    private val mapType = object : TypeReference<Map<String, Any?>>() {}
    private val zipFiles = jarPaths.map { ZipFile(it.toFile()) }
    val primaryZip: ZipFile = zipFiles.first()

    fun listPrimaryEntries(pattern: Regex): List<ZipEntry> =
        primaryZip.entries().asSequence()
            .filter { it.name.matches(pattern) }
            .sortedBy { it.name }
            .toList()

    fun readText(zip: ZipFile, entry: ZipEntry): String =
        zip.getInputStream(entry).bufferedReader().use { it.readText().removePrefix("\uFEFF") }

    fun readJsonMap(entryPath: String): Map<String, Any?> {
        val (zip, entry) = requireEntry(entryPath)
        return objectMapper.readValue(readText(zip, entry), mapType)
    }

    fun loadTextureImage(textureId: String): BufferedImage {
        val normalizedTextureId = if (':' in textureId) textureId else "minecraft:$textureId"
        val separatorIndex = normalizedTextureId.indexOf(':')
        val namespace = normalizedTextureId.substring(0, separatorIndex)
        val path = normalizedTextureId.substring(separatorIndex + 1)
        val entryPath = "assets/$namespace/textures/$path.png"
        val match = zipFiles.firstNotNullOfOrNull { zip ->
            zip.getEntry(entryPath)?.let { entry -> zip to entry }
        }
        if (match != null) {
            val (zip, entry) = match
            return zip.getInputStream(entry).use(ImageIO::read)
                ?: error("Unable to decode image for texture: $normalizedTextureId")
        }

        val fallbackColor = GeneratorSupport.builtinTextureFallbacks[normalizedTextureId]
            ?: throw IOException("Missing zip entry: $entryPath")
        return BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).apply {
            setRGB(0, 0, (0xFF shl 24) or (fallbackColor.red shl 16) or (fallbackColor.green shl 8) or fallbackColor.blue)
        }
    }

    fun requireEntry(entryPath: String): Pair<ZipFile, ZipEntry> =
        zipFiles.firstNotNullOfOrNull { zip ->
            zip.getEntry(entryPath)?.let { entry -> zip to entry }
        } ?: throw IOException("Missing zip entry: $entryPath")

    override fun close() {
        zipFiles.asReversed().forEach(ZipFile::close)
    }
}
