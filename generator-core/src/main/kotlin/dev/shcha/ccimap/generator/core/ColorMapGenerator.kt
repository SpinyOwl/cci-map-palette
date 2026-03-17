package dev.shcha.ccimap.generator.core

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.TreeMap
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.imageio.ImageIO

data class ColorGenerationRequest(
    val sourceJar: Path,
    val outputFile: Path,
    val sourceNamespace: String,
    val blockstatePattern: String = "assets/{namespace}/blockstates/*.json",
    val preferredTextureKeys: List<String> = listOf(
        "up",
        "top",
        "all",
        "end",
        "wool",
        "side",
        "carpet_side",
        "particle"
    )
)

class ColorMapGenerator {
    private val objectMapper: ObjectMapper = ObjectMapper().registerKotlinModule()
    private val mapType = object : TypeReference<Map<String, Any?>>() {}

    fun generate(request: ColorGenerationRequest) {
        require(Files.exists(request.sourceJar)) { "Source jar does not exist: ${request.sourceJar}" }

        ZipFile(request.sourceJar.toFile()).use { zip ->
            val blockstates = zip.entries().asSequence()
                .filter { it.name.matches(globToRegex(request.blockstatePattern.replace("{namespace}", request.sourceNamespace))) }
                .sortedBy { it.name }
                .toList()

            require(blockstates.isNotEmpty()) {
                "No blockstates matched pattern '${request.blockstatePattern}' for namespace '${request.sourceNamespace}'"
            }

            val colors = linkedMapOf<String, String>()
            for (blockstate in blockstates) {
                val blockId = Path.of(blockstate.name).fileName.toString().removeSuffix(".json")
                val modelId = extractModelId(readText(zip, blockstate), blockstate.name)
                val modelEntryPath = toModelEntryPath(modelId)
                val modelJson = readJsonMap(zip, modelEntryPath)
                val textures = readTextures(modelJson, modelEntryPath)
                val textureId = resolveTextureReference(textures, request.preferredTextureKeys)
                    ?: error("No usable texture found in model: $modelEntryPath")

                colors[blockId] = averageHexColor(zip, textureId)
            }

            val outputDir = request.outputFile.parent
            if (outputDir != null) {
                Files.createDirectories(outputDir)
            }

            Files.writeString(
                request.outputFile,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(TreeMap(colors)) + System.lineSeparator()
            )
        }
    }

    private fun readTextures(modelJson: Map<String, Any?>, modelEntryPath: String): Map<String, String> {
        val texturesValue = modelJson["textures"]
            ?: error("No textures section found in model: $modelEntryPath")
        require(texturesValue is Map<*, *>) { "Unexpected textures section in model: $modelEntryPath" }

        return texturesValue.entries.associate { (key, value) ->
            key.toString() to value.toString()
        }
    }

    private fun readJsonMap(zip: ZipFile, entryPath: String): Map<String, Any?> {
        val jsonText = readText(zip, requireEntry(zip, entryPath))
        return objectMapper.readValue(jsonText, mapType)
    }

    private fun extractModelId(blockstateText: String, entryPath: String): String {
        val match = MODEL_REGEX.find(blockstateText)
            ?: error("No model found in blockstate: $entryPath")
        return match.groupValues[1]
    }

    private fun resolveTextureReference(textures: Map<String, String>, preferredKeys: List<String>): String? {
        for (key in preferredKeys) {
            val value = textures[key] ?: continue
            var resolved = value
            while (resolved.startsWith("#")) {
                val lookup = resolved.removePrefix("#")
                resolved = textures[lookup] ?: error("Unresolved texture reference: $value")
            }
            return resolved
        }
        return null
    }

    private fun averageHexColor(zip: ZipFile, textureId: String): String {
        val separatorIndex = textureId.indexOf(':')
        require(separatorIndex >= 0) { "Texture id is missing namespace: $textureId" }

        val namespace = textureId.substring(0, separatorIndex)
        val path = textureId.substring(separatorIndex + 1)
        val entry = requireEntry(zip, "assets/$namespace/textures/$path.png")
        val image = zip.getInputStream(entry).use(ImageIO::read)
            ?: error("Unable to decode image for texture: $textureId")

        return averageHexColor(image)
    }

    private fun averageHexColor(image: BufferedImage): String {
        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0L

        for (x in 0 until image.width) {
            for (y in 0 until image.height) {
                val argb = image.getRGB(x, y)
                val alpha = argb ushr 24 and 0xFF
                if (alpha == 0) {
                    continue
                }

                r += argb ushr 16 and 0xFF
                g += argb ushr 8 and 0xFF
                b += argb and 0xFF
                count++
            }
        }

        if (count == 0L) {
            return "#000000"
        }

        return "#%02X%02X%02X".format((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
    }

    private fun readText(zip: ZipFile, entry: ZipEntry): String =
        zip.getInputStream(entry).bufferedReader().use { it.readText() }

    private fun requireEntry(zip: ZipFile, entryPath: String): ZipEntry =
        zip.getEntry(entryPath) ?: throw IOException("Missing zip entry: $entryPath")

    private fun toModelEntryPath(modelId: String): String {
        val separatorIndex = modelId.indexOf(':')
        require(separatorIndex >= 0) { "Model id is missing namespace: $modelId" }
        val namespace = modelId.substring(0, separatorIndex)
        val path = modelId.substring(separatorIndex + 1)
        return "assets/$namespace/models/$path.json"
    }

    private fun globToRegex(glob: String): Regex {
        val regex = buildString {
            append('^')
            for (char in glob) {
                when (char) {
                    '*' -> append("[^/]*")
                    '.' -> append("\\.")
                    '/' -> append('/')
                    else -> append(Regex.escape(char.toString()))
                }
            }
            append('$')
        }
        return Regex(regex)
    }

    private companion object {
        val MODEL_REGEX = Regex("\"model\"\\s*:\\s*\"([^\"]+)\"")
    }
}
