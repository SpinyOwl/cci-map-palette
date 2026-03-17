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
    val assetJars: List<Path> = emptyList(),
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
        "particle",
        "torch",
        "cross",
        "layer0"
    )
)

class ColorMapGenerator {
    private val objectMapper: ObjectMapper = ObjectMapper().registerKotlinModule()
    private val mapType = object : TypeReference<Map<String, Any?>>() {}

    fun generate(request: ColorGenerationRequest) {
        require(Files.exists(request.sourceJar)) { "Source jar does not exist: ${request.sourceJar}" }
        request.assetJars.forEach { assetJar ->
            require(Files.exists(assetJar)) { "Asset jar does not exist: $assetJar" }
        }

        val jars = listOf(request.sourceJar, *request.assetJars.toTypedArray())
        val zipFiles = jars.map { ZipFile(it.toFile()) }
        try {
            val primaryZip = zipFiles.first()
            val blockstates = primaryZip.entries().asSequence()
                .filter { it.name.matches(globToRegex(request.blockstatePattern.replace("{namespace}", request.sourceNamespace))) }
                .sortedBy { it.name }
                .toList()

            require(blockstates.isNotEmpty()) {
                "No blockstates matched pattern '${request.blockstatePattern}' for namespace '${request.sourceNamespace}'"
            }

            val colors = linkedMapOf<String, String>()
            var skippedBlocks = 0
            for (blockstate in blockstates) {
                val blockId = Path.of(blockstate.name).fileName.toString().removeSuffix(".json")
                try {
                    val modelId = extractModelId(readText(primaryZip, blockstate), blockstate.name)
                    val modelEntryPath = toModelEntryPath(modelId)
                    val textures = resolveModelTextures(zipFiles, modelEntryPath)
                    val textureId = resolveTextureReference(textures, request.preferredTextureKeys)
                        ?: error("No usable texture found in model: $modelEntryPath")

                    colors[blockId] = averageHexColor(zipFiles, textureId)
                } catch (exception: Exception) {
                    skippedBlocks++
                    System.err.println("Skipping block '$blockId': ${exception.message}")
                }
            }

            require(colors.isNotEmpty()) {
                "No colors were generated for namespace '${request.sourceNamespace}'."
            }

            val outputDir = request.outputFile.parent
            if (outputDir != null) {
                Files.createDirectories(outputDir)
            }

            Files.writeString(
                request.outputFile,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(TreeMap(colors)) + System.lineSeparator()
            )

            if (skippedBlocks > 0) {
                System.err.println("Generated ${colors.size} colors and skipped $skippedBlocks blocks for namespace '${request.sourceNamespace}'.")
            }
        } finally {
            zipFiles.asReversed().forEach(ZipFile::close)
        }
    }

    private fun readJsonMap(zips: List<ZipFile>, entryPath: String): Map<String, Any?> {
        val (zip, entry) = requireEntry(zips, entryPath)
        val jsonText = readText(zip, entry)
        return objectMapper.readValue(jsonText, mapType)
    }

    private fun resolveModelTextures(
        zips: List<ZipFile>,
        modelEntryPath: String,
        visited: MutableSet<String> = linkedSetOf()
    ): Map<String, String> {
        require(visited.add(modelEntryPath)) { "Model parent cycle detected: ${visited.joinToString(" -> ")} -> $modelEntryPath" }

        val modelJson = readJsonMap(zips, modelEntryPath)
        val parentTextures = (modelJson["parent"] as? String)
            ?.let { parentId -> resolveParentModelEntryPath(parentId, modelEntryPath) }
            ?.let { parentEntryPath ->
                try {
                    resolveModelTextures(zips, parentEntryPath, visited)
                } catch (exception: IOException) {
                    if (parentEntryPath.startsWith("assets/minecraft/models/")) {
                        emptyMap()
                    } else {
                        throw exception
                    }
                }
            }
            ?: emptyMap()

        val ownTextures = readOwnTextures(modelJson, modelEntryPath)
        return parentTextures + ownTextures
    }

    private fun readOwnTextures(modelJson: Map<String, Any?>, modelEntryPath: String): Map<String, String> {
        val texturesValue = modelJson["textures"] ?: return emptyMap()
        require(texturesValue is Map<*, *>) { "Unexpected textures section in model: $modelEntryPath" }

        return texturesValue.entries.associate { (key, value) ->
            key.toString() to value.toString()
        }
    }

    private fun extractModelId(blockstateText: String, entryPath: String): String {
        val match = MODEL_REGEX.find(blockstateText)
            ?: error("No model found in blockstate: $entryPath")
        return match.groupValues[1]
    }

    private fun resolveTextureReference(textures: Map<String, String>, preferredKeys: List<String>): String? {
        for (key in preferredKeys) {
            val value = textures[key] ?: continue
            return resolveTextureValue(textures, value)
        }

        for (value in textures.values) {
            return resolveTextureValue(textures, value)
        }

        return null
    }

    private fun resolveTextureValue(textures: Map<String, String>, initialValue: String): String {
        var resolved = initialValue
        while (resolved.startsWith("#")) {
            val lookup = resolved.removePrefix("#")
            resolved = textures[lookup] ?: error("Unresolved texture reference: $initialValue")
        }
        return resolved
    }

    private fun averageHexColor(zips: List<ZipFile>, textureId: String): String {
        val separatorIndex = textureId.indexOf(':')
        require(separatorIndex >= 0) { "Texture id is missing namespace: $textureId" }

        val namespace = textureId.substring(0, separatorIndex)
        val path = textureId.substring(separatorIndex + 1)
        val (zip, entry) = requireEntry(zips, "assets/$namespace/textures/$path.png")
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

    private fun requireEntry(zips: List<ZipFile>, entryPath: String): Pair<ZipFile, ZipEntry> =
        zips.firstNotNullOfOrNull { zip ->
            zip.getEntry(entryPath)?.let { entry -> zip to entry }
        } ?: throw IOException("Missing zip entry: $entryPath")

    private fun toModelEntryPath(modelId: String): String {
        val separatorIndex = modelId.indexOf(':')
        require(separatorIndex >= 0) { "Model id is missing namespace: $modelId" }
        val namespace = modelId.substring(0, separatorIndex)
        val path = modelId.substring(separatorIndex + 1)
        return "assets/$namespace/models/$path.json"
    }

    private fun resolveParentModelEntryPath(parentId: String, currentEntryPath: String): String {
        if (parentId.contains(':')) {
            return toModelEntryPath(parentId)
        }

        val currentNamespace = currentEntryPath.removePrefix("assets/").substringBefore('/')
        return if (parentId.contains('/')) {
            toModelEntryPath("minecraft:$parentId")
        } else {
            toModelEntryPath("$currentNamespace:$parentId")
        }
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
