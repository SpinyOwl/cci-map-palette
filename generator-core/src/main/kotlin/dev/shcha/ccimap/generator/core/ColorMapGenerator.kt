package dev.shcha.ccimap.generator.core

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.nio.file.Files
import java.nio.file.Path
import java.util.TreeMap

class ColorMapGenerator {
    private val objectMapper: ObjectMapper = ObjectMapper().registerKotlinModule()

    fun generate(request: ColorGenerationRequest) {
        validateRequest(request)

        val jarPaths = listOf(request.sourceJar, *request.assetJars.toTypedArray())
        ZipResourceSet(jarPaths, objectMapper).use { resources ->
            val modelResolver = BlockModelResolver(objectMapper, resources)
            val colorResolver = BlockColorResolver(resources, modelResolver)

            val colors = generateBlockColors(request, resources, modelResolver, colorResolver)
            if (request.sourceNamespace == "gtceu") {
                GtceuMaterialAugmenter(resources, colorResolver).augment(colors)
            }

            writeOutput(request.outputFile, colors)
        }
    }

    private fun validateRequest(request: ColorGenerationRequest) {
        require(Files.exists(request.sourceJar)) { "Source jar does not exist: ${request.sourceJar}" }
        request.assetJars.forEach { assetJar ->
            require(Files.exists(assetJar)) { "Asset jar does not exist: $assetJar" }
        }
    }

    private fun generateBlockColors(
        request: ColorGenerationRequest,
        resources: ZipResourceSet,
        modelResolver: BlockModelResolver,
        colorResolver: BlockColorResolver
    ): MutableMap<String, String> {
        val blockstatePattern = globToRegex(request.blockstatePattern.replace("{namespace}", request.sourceNamespace))
        val blockstates = resources.listPrimaryEntries(blockstatePattern)

        require(blockstates.isNotEmpty()) {
            "No blockstates matched pattern '${request.blockstatePattern}' for namespace '${request.sourceNamespace}'"
        }

        val colors = linkedMapOf<String, String>()
        var skippedBlocks = 0

        for (blockstate in blockstates) {
            val blockId = Path.of(blockstate.name).fileName.toString().removeSuffix(".json")
            try {
                val modelIds = modelResolver.extractModelIds(
                    resources.readText(resources.primaryZip, blockstate),
                    blockstate.name
                )
                colors[blockId] = colorResolver.resolveBlockStateHexColor(
                    sourceNamespace = request.sourceNamespace,
                    blockId = blockId,
                    modelIds = modelIds,
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

        if (skippedBlocks > 0) {
            System.err.println(
                "Generated ${colors.size} colors and skipped $skippedBlocks blocks for namespace '${request.sourceNamespace}'."
            )
        }

        return colors
    }

    private fun writeOutput(outputFile: Path, colors: Map<String, String>) {
        outputFile.parent?.let(Files::createDirectories)
        Files.writeString(
            outputFile,
            objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(TreeMap(colors)) + System.lineSeparator()
        )
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
}
