package dev.shcha.ccimap.generator.core

import java.nio.file.Path

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
