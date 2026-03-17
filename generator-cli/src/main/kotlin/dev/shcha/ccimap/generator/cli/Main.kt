package dev.shcha.ccimap.generator.cli

import dev.shcha.ccimap.generator.core.ColorGenerationRequest
import dev.shcha.ccimap.generator.core.ColorMapGenerator
import java.nio.file.Path
import kotlin.io.path.Path

fun main(args: Array<String>) {
    val parsed = parseArgs(args.asList())
    val sourceJar = requireOption(parsed, "source-jar")
    val outputFile = requireOption(parsed, "output-file")
    val sourceNamespace = requireOption(parsed, "source-namespace")
    val blockstatePattern = parsed["blockstate-pattern"] ?: "assets/{namespace}/blockstates/*.json"
    val preferredTextureKeys = parsed["preferred-texture-keys"]
        ?.split(',')
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?: listOf("up", "top", "all", "end", "wool", "side", "carpet_side", "particle")

    ColorMapGenerator().generate(
        ColorGenerationRequest(
            sourceJar = Path(sourceJar),
            outputFile = Path(outputFile),
            sourceNamespace = sourceNamespace,
            blockstatePattern = blockstatePattern,
            preferredTextureKeys = preferredTextureKeys
        )
    )
}

private fun parseArgs(args: List<String>): Map<String, String> {
    require(args.size % 2 == 0) {
        "Arguments must be passed as --key value pairs."
    }

    return buildMap {
        var index = 0
        while (index < args.size) {
            val key = args[index]
            require(key.startsWith("--")) { "Unexpected argument: $key" }
            put(key.removePrefix("--"), args[index + 1])
            index += 2
        }
    }
}

private fun requireOption(options: Map<String, String>, key: String): String =
    options[key] ?: error("Missing required option --$key")
