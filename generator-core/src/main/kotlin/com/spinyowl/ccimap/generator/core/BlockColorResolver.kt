package com.spinyowl.ccimap.generator.core

import java.awt.image.BufferedImage
import kotlin.math.roundToInt

internal class BlockColorResolver(
    private val resources: ZipResourceSet,
    private val modelResolver: BlockModelResolver
) {
    fun resolveBlockStateHexColor(
        sourceNamespace: String,
        blockId: String,
        modelIds: List<String>,
        preferredTextureKeys: List<String>
    ): String {
        val colors = modelIds.mapNotNull { modelId ->
            try {
                val resolvedModel = modelResolver.resolveModel(modelResolver.toModelEntryPath(modelId))
                GeneratorSupport.parseHexColor(resolveBlockHexColor(blockId, resolvedModel, preferredTextureKeys))
            } catch (_: Exception) {
                null
            }
        }

        if (colors.isEmpty()) {
            resolveItemModelHexColor(sourceNamespace, blockId, preferredTextureKeys)?.let { return it }
            resolveSyntheticBlockHexColor(sourceNamespace, blockId)?.let { return it }
        }

        require(colors.isNotEmpty()) { "No usable model variant found in blockstate" }
        return GeneratorSupport.averageColors(colors).toHex()
    }

    fun resolveGtceuMaterialBlockColor(definition: GtceuMaterialDefinition): String {
        val primary = GeneratorSupport.parseRgb(definition.primaryColor) ?: return DEFAULT_GTCEU_FALLBACK_COLOR
        val secondary = GeneratorSupport.parseRgb(definition.secondaryColor) ?: primary
        val iconSet = definition.iconSet ?: return primary.toHex()
        val modelPath = "assets/gtceu/models/block/material_sets/$iconSet/block.json"

        return try {
            val resolvedModel = modelResolver.resolveModel(modelPath)
            val botTextures = resolvedModel.textures.filterKeys { it.startsWith("bot_") }.values
                .map { resolveTextureValue(resolvedModel.textures, it) }.distinct()
            val topTextures = resolvedModel.textures.filterKeys { it.startsWith("top_") }.values
                .map { resolveTextureValue(resolvedModel.textures, it) }.distinct()

            val tintedLayers = mutableListOf<RgbColor>()
            tintedLayers += botTextures.map { tintTexture(it, primary) }
            tintedLayers += topTextures.map { tintTexture(it, secondary) }

            if (tintedLayers.isEmpty()) primary.toHex() else GeneratorSupport.averageColors(tintedLayers).toHex()
        } catch (_: Exception) {
            primary.toHex()
        }
    }

    private fun resolveBlockHexColor(blockId: String, resolvedModel: ResolvedModel, preferredTextureKeys: List<String>): String {
        if (resolvedModel.compositeChildren.isNotEmpty()) {
            val childColors = resolvedModel.compositeChildren.mapNotNull { child ->
                try {
                    GeneratorSupport.parseHexColor(resolveBlockHexColor(blockId, child, preferredTextureKeys))
                } catch (_: Exception) {
                    null
                }
            }
            if (childColors.isNotEmpty()) {
                return GeneratorSupport.averageColors(childColors).toHex()
            }
        }

        resolveFaceBasedColor(resolvedModel)?.let { return it }
        resolveLoaderBasedColor(blockId, resolvedModel)?.let { return it }

        val textureId = resolveTextureReference(resolvedModel.textures, preferredTextureKeys)
            ?: error("No usable texture found in model")
        return averageColor(textureId).toHex()
    }

    private fun resolveItemModelHexColor(sourceNamespace: String, blockId: String, preferredTextureKeys: List<String>): String? {
        val itemModelPath = "assets/$sourceNamespace/models/item/$blockId.json"
        return try {
            val resolvedModel = modelResolver.resolveModel(itemModelPath)
            resolveBlockHexColor(blockId, resolvedModel, preferredTextureKeys)
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveSyntheticBlockHexColor(sourceNamespace: String, blockId: String): String? {
        val textureIds = GeneratorSupport.syntheticBlockTextureFallbacks["$sourceNamespace:$blockId"] ?: return null
        val colors = textureIds.map { textureId -> averageColor(textureId) }
        return GeneratorSupport.averageColors(colors).toHex()
    }

    private fun resolveFaceBasedColor(resolvedModel: ResolvedModel): String? {
        if (resolvedModel.elements.isEmpty()) return null

        val faceColors = mutableListOf<RgbColor>()
        for (element in resolvedModel.elements) {
            val facesValue = element["faces"] as? Map<*, *> ?: continue
            for ((_, faceValue) in facesValue) {
                val faceMap = faceValue as? Map<*, *> ?: continue
                val textureRef = faceMap["texture"]?.toString() ?: continue
                val textureColor = try {
                    val textureId = resolveTextureValue(resolvedModel.textures, textureRef)
                    averageColor(textureId)
                } catch (_: Exception) {
                    continue
                }
                val tintColor = parseFaceColor(faceMap)
                faceColors += if (tintColor != null) blend(textureColor, tintColor) else textureColor
            }
        }

        return if (faceColors.isEmpty()) null else GeneratorSupport.averageColors(faceColors).toHex()
    }

    private fun resolveLoaderBasedColor(blockId: String, resolvedModel: ResolvedModel): String? {
        if (resolvedModel.loader != "xycraft_core:connected_textures") return null

        val tint = GeneratorSupport.tintFromBlockId(blockId)
        val textureKeys = listOf("base", "texture_connected", "texture_single")
            .filter { resolvedModel.textures.containsKey(it) }
        if (textureKeys.isEmpty()) return null

        val colors = textureKeys.map { textureKey ->
            val textureId = resolveTextureValue(resolvedModel.textures, resolvedModel.textures.getValue(textureKey))
            val textureColor = averageColor(textureId)
            when {
                textureKey == "base" && (resolvedModel.baseTintIndex ?: -1) >= 0 && tint != null -> blend(textureColor, tint to 1.0)
                textureKey.startsWith("texture_") && (resolvedModel.tintIndex ?: -1) >= 0 && tint != null -> blend(textureColor, tint to 1.0)
                else -> textureColor
            }
        }

        return GeneratorSupport.averageColors(colors).toHex()
    }

    private fun resolveTextureReference(textures: Map<String, String>, preferredKeys: List<String>): String? {
        val effectivePreferredKeys = if ("particle" in textures && textures.keys.any { it != "particle" && it != "all" }) {
            preferredKeys.filterNot { it == "particle" } + "particle"
        } else {
            preferredKeys
        }

        for (key in effectivePreferredKeys) {
            val value = textures[key] ?: continue
            return resolveTextureValue(textures, value)
        }

        for (value in textures.values) return resolveTextureValue(textures, value)
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

    private fun averageColor(textureId: String): RgbColor = averageColor(resources.loadTextureImage(textureId))

    private fun averageColor(image: BufferedImage): RgbColor {
        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0L
        for (x in 0 until image.width) {
            for (y in 0 until image.height) {
                val argb = image.getRGB(x, y)
                val alpha = argb ushr 24 and 0xFF
                if (alpha == 0) continue
                r += argb ushr 16 and 0xFF
                g += argb ushr 8 and 0xFF
                b += argb and 0xFF
                count++
            }
        }

        if (count == 0L) return RgbColor(0, 0, 0)
        return RgbColor((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
    }

    private fun tintTexture(textureId: String, tint: RgbColor): RgbColor {
        val base = averageColor(textureId)
        return RgbColor(
            red = (base.red * tint.red / 255.0).roundToInt().coerceIn(0, 255),
            green = (base.green * tint.green / 255.0).roundToInt().coerceIn(0, 255),
            blue = (base.blue * tint.blue / 255.0).roundToInt().coerceIn(0, 255)
        )
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

    private fun blendChannel(base: Int, overlay: Int, alpha: Double): Int =
        (base * (1.0 - alpha) + overlay * alpha).roundToInt().coerceIn(0, 255)

    private companion object {
        const val DEFAULT_GTCEU_FALLBACK_COLOR = "#808080"
    }
}
