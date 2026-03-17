package dev.shcha.ccimap.generator.core

import kotlin.math.roundToInt

internal object GeneratorSupport {
    val knownColorSuffixes = listOf(
        "light_blue", "light_gray", "white", "orange", "magenta", "yellow", "lime", "pink",
        "gray", "cyan", "purple", "blue", "brown", "green", "red", "black", "dark", "light"
    )

    val tintColors = mapOf(
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

    val builtinTextureFallbacks = mapOf(
        "minecraft:block/tnt_side" to RgbColor(170, 94, 89),
        "minecraft:block/tnt_top" to RgbColor(194, 172, 107),
        "minecraft:block/tnt_bottom" to RgbColor(160, 126, 89)
    )

    fun averageColors(colors: List<RgbColor>): RgbColor {
        val red = colors.sumOf { it.red } / colors.size
        val green = colors.sumOf { it.green } / colors.size
        val blue = colors.sumOf { it.blue } / colors.size
        return RgbColor(red, green, blue)
    }

    fun parseRgb(rawColor: Int?): RgbColor? {
        if (rawColor == null) return null
        return RgbColor(
            red = rawColor ushr 16 and 0xFF,
            green = rawColor ushr 8 and 0xFF,
            blue = rawColor and 0xFF
        )
    }

    fun parseHexColor(value: String): RgbColor {
        val normalized = value.removePrefix("#")
        return when (normalized.length) {
            6 -> RgbColor(
                red = normalized.substring(0, 2).toInt(16),
                green = normalized.substring(2, 4).toInt(16),
                blue = normalized.substring(4, 6).toInt(16)
            )

            8 -> {
                val alpha = normalized.substring(0, 2).toInt(16) / 255.0
                val red = normalized.substring(2, 4).toInt(16)
                val green = normalized.substring(4, 6).toInt(16)
                val blue = normalized.substring(6, 8).toInt(16)
                RgbColor(
                    red = (red * alpha).roundToInt(),
                    green = (green * alpha).roundToInt(),
                    blue = (blue * alpha).roundToInt()
                )
            }

            else -> error("Unsupported hex color: $value")
        }
    }

    fun tintFromBlockId(blockId: String): RgbColor? {
        val suffix = knownColorSuffixes.firstOrNull { blockId.endsWith("_$it") } ?: return null
        return tintColors[suffix]
    }
}
