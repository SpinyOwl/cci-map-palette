package dev.shcha.ccimap.generator.core

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.roundToInt
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
                    val resolvedModel = resolveModel(zipFiles, modelEntryPath)

                    colors[blockId] = resolveBlockHexColor(
                        blockId = blockId,
                        zips = zipFiles,
                        resolvedModel = resolvedModel,
                        preferredTextureKeys = request.preferredTextureKeys
                    )
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

    private data class ResolvedModel(
        val textures: Map<String, String>,
        val elements: List<Map<String, Any?>> = emptyList(),
        val loader: String? = null,
        val tintIndex: Int? = null,
        val baseTintIndex: Int? = null
    )

    private data class RgbColor(
        val red: Int,
        val green: Int,
        val blue: Int
    ) {
        fun toHex(): String = "#%02X%02X%02X".format(red, green, blue)
    }

    private fun readJsonMap(zips: List<ZipFile>, entryPath: String): Map<String, Any?> {
        val (zip, entry) = requireEntry(zips, entryPath)
        val jsonText = readText(zip, entry)
        return objectMapper.readValue(jsonText, mapType)
    }

    private fun resolveModel(
        zips: List<ZipFile>,
        modelEntryPath: String,
        visited: MutableSet<String> = linkedSetOf()
    ): ResolvedModel {
        require(visited.add(modelEntryPath)) { "Model parent cycle detected: ${visited.joinToString(" -> ")} -> $modelEntryPath" }

        val modelJson = readJsonMap(zips, modelEntryPath)
        val parentModel = (modelJson["parent"] as? String)
            ?.let { parentId -> resolveParentModelEntryPath(parentId, modelEntryPath) }
            ?.let { parentEntryPath ->
                try {
                    resolveModel(zips, parentEntryPath, visited)
                } catch (exception: IOException) {
                    if (parentEntryPath.startsWith("assets/minecraft/models/")) {
                        null
                    } else {
                        throw exception
                    }
                }
            }

        val ownTextures = readOwnTextures(modelJson, modelEntryPath)
        val ownElements = readOwnElements(modelJson, modelEntryPath)

        return ResolvedModel(
            textures = (parentModel?.textures ?: emptyMap()) + ownTextures,
            elements = ownElements ?: parentModel?.elements.orEmpty(),
            loader = modelJson["loader"]?.toString() ?: parentModel?.loader,
            tintIndex = (modelJson["tint_index"] as? Number)?.toInt() ?: parentModel?.tintIndex,
            baseTintIndex = (modelJson["base_tint_index"] as? Number)?.toInt() ?: parentModel?.baseTintIndex
        )
    }

    private fun readOwnTextures(modelJson: Map<String, Any?>, modelEntryPath: String): Map<String, String> {
        val texturesValue = modelJson["textures"] ?: return emptyMap()
        require(texturesValue is Map<*, *>) { "Unexpected textures section in model: $modelEntryPath" }

        return texturesValue.entries.associate { (key, value) ->
            key.toString() to value.toString()
        }
    }

    private fun readOwnElements(modelJson: Map<String, Any?>, modelEntryPath: String): List<Map<String, Any?>>? {
        val elementsValue = modelJson["elements"] ?: return null
        require(elementsValue is List<*>) { "Unexpected elements section in model: $modelEntryPath" }

        return elementsValue.mapIndexed { index, element ->
            require(element is Map<*, *>) { "Unexpected element at index $index in model: $modelEntryPath" }
            @Suppress("UNCHECKED_CAST")
            element as Map<String, Any?>
        }
    }

    private fun extractModelId(blockstateText: String, entryPath: String): String {
        val match = MODEL_REGEX.find(blockstateText)
            ?: error("No model found in blockstate: $entryPath")
        return match.groupValues[1]
    }

    private fun resolveBlockHexColor(
        blockId: String,
        zips: List<ZipFile>,
        resolvedModel: ResolvedModel,
        preferredTextureKeys: List<String>
    ): String {
        val faceColor = resolveFaceBasedColor(zips, resolvedModel)
        if (faceColor != null) {
            return faceColor
        }

        val loaderColor = resolveLoaderBasedColor(blockId, zips, resolvedModel)
        if (loaderColor != null) {
            return loaderColor
        }

        val textureId = resolveTextureReference(resolvedModel.textures, preferredTextureKeys)
            ?: error("No usable texture found in model")
        return averageColor(zips, textureId).toHex()
    }

    private fun resolveFaceBasedColor(zips: List<ZipFile>, resolvedModel: ResolvedModel): String? {
        if (resolvedModel.elements.isEmpty()) {
            return null
        }

        val faceColors = mutableListOf<RgbColor>()
        for (element in resolvedModel.elements) {
            val facesValue = element["faces"] as? Map<*, *> ?: continue
            for ((_, faceValue) in facesValue) {
                val faceMap = faceValue as? Map<*, *> ?: continue
                val textureRef = faceMap["texture"]?.toString() ?: continue
                val textureId = resolveTextureValue(resolvedModel.textures, textureRef)
                val textureColor = averageColor(zips, textureId)
                val tintColor = parseFaceColor(faceMap)
                faceColors += if (tintColor != null) {
                    blend(textureColor, tintColor)
                } else {
                    textureColor
                }
            }
        }

        if (faceColors.isEmpty()) {
            return null
        }

        val red = faceColors.sumOf { it.red } / faceColors.size
        val green = faceColors.sumOf { it.green } / faceColors.size
        val blue = faceColors.sumOf { it.blue } / faceColors.size
        return RgbColor(red, green, blue).toHex()
    }

    private fun resolveLoaderBasedColor(
        blockId: String,
        zips: List<ZipFile>,
        resolvedModel: ResolvedModel
    ): String? {
        if (resolvedModel.loader != "xycraft_core:connected_textures") {
            return null
        }

        val tint = tintFromBlockId(blockId)
        val textureKeys = listOf("base", "texture_connected", "texture_single")
            .filter { resolvedModel.textures.containsKey(it) }
        if (textureKeys.isEmpty()) {
            return null
        }

        val colors = textureKeys.map { textureKey ->
            val textureId = resolveTextureValue(resolvedModel.textures, resolvedModel.textures.getValue(textureKey))
            val textureColor = averageColor(zips, textureId)
            when {
                textureKey == "base" && (resolvedModel.baseTintIndex ?: -1) >= 0 && tint != null -> blend(textureColor, tint to 1.0)
                textureKey.startsWith("texture_") && (resolvedModel.tintIndex ?: -1) >= 0 && tint != null -> blend(textureColor, tint to 1.0)
                else -> textureColor
            }
        }

        val red = colors.sumOf { it.red } / colors.size
        val green = colors.sumOf { it.green } / colors.size
        val blue = colors.sumOf { it.blue } / colors.size
        return RgbColor(red, green, blue).toHex()
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

    private fun averageColor(zips: List<ZipFile>, textureId: String): RgbColor {
        val separatorIndex = textureId.indexOf(':')
        require(separatorIndex >= 0) { "Texture id is missing namespace: $textureId" }

        val namespace = textureId.substring(0, separatorIndex)
        val path = textureId.substring(separatorIndex + 1)
        val (zip, entry) = requireEntry(zips, "assets/$namespace/textures/$path.png")
        val image = zip.getInputStream(entry).use(ImageIO::read)
            ?: error("Unable to decode image for texture: $textureId")

        return averageColor(image)
    }

    private fun averageColor(image: BufferedImage): RgbColor {
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
            return RgbColor(0, 0, 0)
        }

        return RgbColor((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
    }

    private fun parseFaceColor(faceMap: Map<*, *>): Pair<RgbColor, Double>? {
        val forgeData = faceMap["forge_data"] as? Map<*, *> ?: return null
        val rawColor = forgeData["color"]?.toString()?.trim() ?: return null
        val normalized = rawColor.removePrefix("#")
        require(normalized.length == 8) { "Unsupported face color format: $rawColor" }

        val alpha = normalized.substring(0, 2).toInt(16) / 255.0
        val red = normalized.substring(2, 4).toInt(16)
        val green = normalized.substring(4, 6).toInt(16)
        val blue = normalized.substring(6, 8).toInt(16)
        return RgbColor(red, green, blue) to alpha
    }

    private fun blend(base: RgbColor, tint: Pair<RgbColor, Double>): RgbColor {
        val (overlay, alpha) = tint
        return RgbColor(
            red = blendChannel(base.red, overlay.red, alpha),
            green = blendChannel(base.green, overlay.green, alpha),
            blue = blendChannel(base.blue, overlay.blue, alpha)
        )
    }

    private fun blendChannel(base: Int, overlay: Int, alpha: Double): Int {
        return (base * (1.0 - alpha) + overlay * alpha).roundToInt().coerceIn(0, 255)
    }

    private fun tintFromBlockId(blockId: String): RgbColor? {
        val suffix = KNOWN_COLOR_SUFFIXES.firstOrNull { blockId.endsWith("_$it") } ?: return null
        return TINT_COLORS[suffix]
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
        val KNOWN_COLOR_SUFFIXES = listOf(
            "light_blue",
            "light_gray",
            "white",
            "orange",
            "magenta",
            "yellow",
            "lime",
            "pink",
            "gray",
            "cyan",
            "purple",
            "blue",
            "brown",
            "green",
            "red",
            "black",
            "dark",
            "light"
        )
        val TINT_COLORS = mapOf(
            "white" to RgbColor(249, 255, 254),
            "orange" to RgbColor(249, 128, 29),
            "magenta" to RgbColor(199, 78, 189),
            "light_blue" to RgbColor(58, 179, 218),
            "yellow" to RgbColor(254, 216, 61),
            "lime" to RgbColor(128, 199, 31),
            "pink" to RgbColor(243, 139, 170),
            "gray" to RgbColor(71, 79, 82),
            "light_gray" to RgbColor(157, 157, 151),
            "cyan" to RgbColor(22, 156, 156),
            "purple" to RgbColor(137, 50, 184),
            "blue" to RgbColor(0, 100, 255),
            "brown" to RgbColor(131, 84, 50),
            "green" to RgbColor(0, 255, 0),
            "red" to RgbColor(255, 0, 0),
            "black" to RgbColor(29, 29, 33),
            "dark" to RgbColor(30, 30, 30),
            "light" to RgbColor(254, 254, 254)
        )
    }
}
