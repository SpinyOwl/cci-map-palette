package dev.shcha.ccimap.generator.core

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode
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

            if (request.sourceNamespace == "gtceu") {
                augmentGtceuGeneratedBlocks(zipFiles, colors)
                Files.writeString(
                    request.outputFile,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(TreeMap(colors)) + System.lineSeparator()
                )
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
        val baseTintIndex: Int? = null,
        val compositeChildren: List<ResolvedModel> = emptyList()
    )

    private data class RgbColor(
        val red: Int,
        val green: Int,
        val blue: Int
    ) {
        fun toHex(): String = "#%02X%02X%02X".format(red, green, blue)
    }

    private data class GtceuMaterialDefinition(
        val id: String,
        var primaryColor: Int? = null,
        var secondaryColor: Int? = null,
        var iconSet: String? = null,
        var hasSolidForm: Boolean = false
    )

    private sealed interface BytecodeValue
    private data class IntValue(val value: Int) : BytecodeValue
    private data class FloatValue(val value: Float) : BytecodeValue
    private data class StringValue(val value: String) : BytecodeValue
    private data class ResourceLocationValue(val id: String) : BytecodeValue
    private data class BuilderValue(val definition: GtceuMaterialDefinition) : BytecodeValue
    private data class MarkerValue(val owner: String) : BytecodeValue
    private data class FieldValue(val owner: String, val name: String) : BytecodeValue
    private data object UnknownValue : BytecodeValue

    private fun readJsonMap(zips: List<ZipFile>, entryPath: String): Map<String, Any?> {
        val (zip, entry) = requireEntry(zips, entryPath)
        val jsonText = readText(zip, entry)
        return objectMapper.readValue(jsonText, mapType)
    }

    private fun augmentGtceuGeneratedBlocks(zips: List<ZipFile>, colors: MutableMap<String, String>) {
        val materialDefinitions = loadGtceuMaterialDefinitions(zips)
        var synthesizedBlocks = 0

        for (definition in materialDefinitions.values) {
            if (!definition.hasSolidForm) {
                continue
            }

            val blockId = "${definition.id}_block"
            if (colors.containsKey(blockId)) {
                continue
            }

            colors[blockId] = resolveGtceuMaterialBlockColor(zips, definition)
            synthesizedBlocks++
        }

        if (synthesizedBlocks > 0) {
            System.err.println("Synthesized $synthesizedBlocks GTCEu material block colors.")
        }
    }

    private fun loadGtceuMaterialDefinitions(zips: List<ZipFile>): Map<String, GtceuMaterialDefinition> {
        val (_, entry) = requireEntry(zips, GTCEU_ELEMENT_MATERIALS_CLASS)
        val classNode = ClassNode()
        zips.firstNotNullOf { zip ->
            zip.getEntry(GTCEU_ELEMENT_MATERIALS_CLASS)?.let { foundEntry ->
                zip.getInputStream(foundEntry).use { input ->
                    ClassReader(input).accept(classNode, 0)
                }
                zip
            }
        }

        val registerMethod = classNode.methods.firstOrNull { it.name == "register" && it.desc == "()V" }
            ?: error("Unable to find GTCEu material register method")

        return parseGtceuMaterialDefinitions(registerMethod)
    }

    private fun parseGtceuMaterialDefinitions(method: MethodNode): Map<String, GtceuMaterialDefinition> {
        val stack = mutableListOf<BytecodeValue>()
        val locals = mutableMapOf<Int, BytecodeValue>()
        val materials = linkedMapOf<String, GtceuMaterialDefinition>()
        val iterator = method.instructions.iterator()

        while (iterator.hasNext()) {
            when (val instruction = iterator.next()) {
                is LdcInsnNode -> {
                    when (val constant = instruction.cst) {
                        is Int -> stack += IntValue(constant)
                        is Float -> stack += FloatValue(constant)
                        is String -> stack += StringValue(constant)
                        else -> stack += UnknownValue
                    }
                }

                is IntInsnNode -> stack += IntValue(instruction.operand)

                is InsnNode -> handleZeroOperandInstruction(instruction.opcode, stack)

                is VarInsnNode -> when (instruction.opcode) {
                    Opcodes.ALOAD -> stack += locals[instruction.`var`] ?: UnknownValue
                    Opcodes.ASTORE -> locals[instruction.`var`] = popOrUnknown(stack)
                }

                is TypeInsnNode -> {
                    if (instruction.opcode == Opcodes.NEW) {
                        stack += MarkerValue(instruction.desc)
                    }
                }

                is FieldInsnNode -> {
                    if (instruction.opcode == Opcodes.GETSTATIC) {
                        stack += FieldValue(instruction.owner, instruction.name)
                    }
                }

                is MethodInsnNode -> handleMethodInstruction(instruction, stack, materials)
            }
        }

        return materials
    }

    private fun handleZeroOperandInstruction(opcode: Int, stack: MutableList<BytecodeValue>) {
        when (opcode) {
            Opcodes.ACONST_NULL -> stack += UnknownValue
            Opcodes.ICONST_M1 -> stack += IntValue(-1)
            Opcodes.ICONST_0 -> stack += IntValue(0)
            Opcodes.ICONST_1 -> stack += IntValue(1)
            Opcodes.ICONST_2 -> stack += IntValue(2)
            Opcodes.ICONST_3 -> stack += IntValue(3)
            Opcodes.ICONST_4 -> stack += IntValue(4)
            Opcodes.ICONST_5 -> stack += IntValue(5)
            Opcodes.FCONST_0 -> stack += FloatValue(0f)
            Opcodes.FCONST_1 -> stack += FloatValue(1f)
            Opcodes.FCONST_2 -> stack += FloatValue(2f)
            Opcodes.DUP -> stack.lastOrNull()?.let { stack += it }
            Opcodes.POP -> popOrUnknown(stack)
        }
    }

    private fun handleMethodInstruction(
        instruction: MethodInsnNode,
        stack: MutableList<BytecodeValue>,
        materials: MutableMap<String, GtceuMaterialDefinition>
    ) {
        val argumentCount = countMethodArguments(instruction.desc)
        val arguments = MutableList(argumentCount) { popOrUnknown(stack) }.asReversed()
        val receiver = if (instruction.opcode != Opcodes.INVOKESTATIC) popOrUnknown(stack) else null

        when {
            instruction.owner == GTCEU_OWNER && instruction.name == "id" -> {
                val id = (arguments.firstOrNull() as? StringValue)?.value
                if (id == null) {
                    stack += UnknownValue
                    return
                }
                stack += ResourceLocationValue(id)
            }

            instruction.owner == MATERIAL_BUILDER_OWNER && instruction.name == "<init>" -> {
                val id = (arguments.firstOrNull() as? ResourceLocationValue)?.id
                if (receiver is MarkerValue && id != null) {
                    val definition = materials.getOrPut(id) { GtceuMaterialDefinition(id) }
                    stack += BuilderValue(definition)
                } else {
                    stack += UnknownValue
                }
            }

            instruction.owner == MATERIAL_BUILDER_OWNER -> {
                val builder = receiver as? BuilderValue
                if (builder == null) {
                    if (returnsValue(instruction.desc)) {
                        stack += UnknownValue
                    }
                    return
                }

                when (instruction.name) {
                    "ingot", "gem" -> builder.definition.hasSolidForm = true
                    "color" -> builder.definition.primaryColor = (arguments.firstOrNull() as? IntValue)?.value ?: builder.definition.primaryColor
                    "secondaryColor" -> builder.definition.secondaryColor = (arguments.firstOrNull() as? IntValue)?.value ?: builder.definition.secondaryColor
                    "iconSet" -> {
                        val iconSet = (arguments.firstOrNull() as? FieldValue)
                            ?.takeIf { it.owner == MATERIAL_ICON_SET_OWNER }
                            ?.name
                            ?.lowercase()
                        if (iconSet != null) {
                            builder.definition.iconSet = iconSet
                        }
                    }
                    "buildAndRegister" -> {
                        materials[builder.definition.id] = builder.definition
                        stack += UnknownValue
                        return
                    }
                }

                if (returnsValue(instruction.desc)) {
                    stack += builder
                }
            }

            else -> if (returnsValue(instruction.desc)) {
                stack += UnknownValue
            }
        }
    }

    private fun resolveGtceuMaterialBlockColor(
        zips: List<ZipFile>,
        definition: GtceuMaterialDefinition
    ): String {
        val primary = parseRgb(definition.primaryColor) ?: return DEFAULT_GTCEU_FALLBACK_COLOR
        val secondary = parseRgb(definition.secondaryColor) ?: primary
        val iconSet = definition.iconSet ?: return primary.toHex()
        val modelPath = "assets/gtceu/models/block/material_sets/$iconSet/block.json"

        return try {
            val resolvedModel = resolveModel(zips, modelPath)
            val botTextures = resolvedModel.textures
                .filterKeys { it.startsWith("bot_") }
                .values
                .map { resolveTextureValue(resolvedModel.textures, it) }
                .distinct()
            val topTextures = resolvedModel.textures
                .filterKeys { it.startsWith("top_") }
                .values
                .map { resolveTextureValue(resolvedModel.textures, it) }
                .distinct()

            val tintedLayers = mutableListOf<RgbColor>()
            tintedLayers += botTextures.map { tintTexture(zips, it, primary) }
            tintedLayers += topTextures.map { tintTexture(zips, it, secondary) }

            if (tintedLayers.isEmpty()) {
                primary.toHex()
            } else {
                averageColors(tintedLayers).toHex()
            }
        } catch (_: Exception) {
            primary.toHex()
        }
    }

    private fun tintTexture(zips: List<ZipFile>, textureId: String, tint: RgbColor): RgbColor {
        val base = averageColor(zips, textureId)
        return RgbColor(
            red = (base.red * tint.red / 255.0).roundToInt().coerceIn(0, 255),
            green = (base.green * tint.green / 255.0).roundToInt().coerceIn(0, 255),
            blue = (base.blue * tint.blue / 255.0).roundToInt().coerceIn(0, 255)
        )
    }

    private fun averageColors(colors: List<RgbColor>): RgbColor {
        val red = colors.sumOf { it.red } / colors.size
        val green = colors.sumOf { it.green } / colors.size
        val blue = colors.sumOf { it.blue } / colors.size
        return RgbColor(red, green, blue)
    }

    private fun parseRgb(rawColor: Int?): RgbColor? {
        if (rawColor == null) {
            return null
        }

        return RgbColor(
            red = rawColor ushr 16 and 0xFF,
            green = rawColor ushr 8 and 0xFF,
            blue = rawColor and 0xFF
        )
    }

    private fun popOrUnknown(stack: MutableList<BytecodeValue>): BytecodeValue =
        if (stack.isEmpty()) UnknownValue else stack.removeLast()

    private fun countMethodArguments(descriptor: String): Int {
        var count = 0
        var index = descriptor.indexOf('(') + 1
        while (descriptor[index] != ')') {
            when (descriptor[index]) {
                'L' -> {
                    index = descriptor.indexOf(';', index) + 1
                    count++
                }
                '[' -> {
                    while (descriptor[index] == '[') {
                        index++
                    }
                    if (descriptor[index] == 'L') {
                        index = descriptor.indexOf(';', index) + 1
                    } else {
                        index++
                    }
                    count++
                }
                else -> {
                    index++
                    count++
                }
            }
        }
        return count
    }

    private fun returnsValue(descriptor: String): Boolean = !descriptor.endsWith(")V")

    private fun resolveModel(
        zips: List<ZipFile>,
        modelEntryPath: String,
        visited: MutableSet<String> = linkedSetOf()
    ): ResolvedModel {
        require(visited.add(modelEntryPath)) { "Model parent cycle detected: ${visited.joinToString(" -> ")} -> $modelEntryPath" }

        val modelJson = readJsonMap(zips, modelEntryPath)
        return resolveModelFromJson(zips, modelJson, modelEntryPath, visited)
    }

    private fun resolveModelFromJson(
        zips: List<ZipFile>,
        modelJson: Map<String, Any?>,
        modelEntryPath: String,
        visited: MutableSet<String>
    ): ResolvedModel {
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
        val ownChildren = readOwnChildren(modelJson, modelEntryPath)

        return ResolvedModel(
            textures = (parentModel?.textures ?: emptyMap()) + ownTextures,
            elements = ownElements ?: parentModel?.elements.orEmpty(),
            loader = modelJson["loader"]?.toString() ?: parentModel?.loader,
            tintIndex = (modelJson["tint_index"] as? Number)?.toInt() ?: parentModel?.tintIndex,
            baseTintIndex = (modelJson["base_tint_index"] as? Number)?.toInt() ?: parentModel?.baseTintIndex,
            compositeChildren = if (ownChildren.isEmpty()) {
                parentModel?.compositeChildren.orEmpty()
            } else {
                ownChildren.map { child ->
                    resolveModelFromJson(zips, child, modelEntryPath, visited.toMutableSet())
                }
            }
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

    private fun readOwnChildren(modelJson: Map<String, Any?>, modelEntryPath: String): List<Map<String, Any?>> {
        val childrenValue = modelJson["children"] ?: return emptyList()
        require(childrenValue is Map<*, *>) { "Unexpected children section in model: $modelEntryPath" }

        return childrenValue.values.mapIndexed { index, child ->
            require(child is Map<*, *>) { "Unexpected child at index $index in model: $modelEntryPath" }
            @Suppress("UNCHECKED_CAST")
            child as Map<String, Any?>
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
        if (resolvedModel.compositeChildren.isNotEmpty()) {
            val childColors = resolvedModel.compositeChildren.mapNotNull { child ->
                resolveFaceBasedColor(zips, child)?.let(::parseHexColor)
            }
            if (childColors.isNotEmpty()) {
                return averageColors(childColors).toHex()
            }
        }

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
        val baseTextureId = resolvedModel.textures["base"]?.let { resolveTextureValue(resolvedModel.textures, it) }
        val layerTextureId = listOf("texture_single", "texture_connected")
            .firstNotNullOfOrNull { key -> resolvedModel.textures[key]?.let { resolveTextureValue(resolvedModel.textures, it) } }
        if (baseTextureId == null && layerTextureId == null) {
            return null
        }

        var composedImage: BufferedImage? = null

        if (baseTextureId != null) {
            val baseImage = loadTextureImage(zips, baseTextureId)
            composedImage = if ((resolvedModel.baseTintIndex ?: -1) >= 0 && tint != null) {
                tintImage(baseImage, tint)
            } else {
                baseImage
            }
        }

        if (layerTextureId != null) {
            val layerImage = loadTextureImage(zips, layerTextureId)
            val tintedLayerImage = if ((resolvedModel.tintIndex ?: -1) >= 0 && tint != null) {
                tintImage(layerImage, tint)
            } else {
                layerImage
            }

            composedImage = if (composedImage == null) {
                tintedLayerImage
            } else {
                compositeImages(composedImage, tintedLayerImage)
            }
        }

        return composedImage?.let(::averageColor)?.toHex()
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
        return averageColor(loadTextureImage(zips, textureId))
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

    private fun loadTextureImage(zips: List<ZipFile>, textureId: String): BufferedImage {
        val separatorIndex = textureId.indexOf(':')
        require(separatorIndex >= 0) { "Texture id is missing namespace: $textureId" }

        val namespace = textureId.substring(0, separatorIndex)
        val path = textureId.substring(separatorIndex + 1)
        val (zip, entry) = requireEntry(zips, "assets/$namespace/textures/$path.png")
        return zip.getInputStream(entry).use(ImageIO::read)
            ?: error("Unable to decode image for texture: $textureId")
    }

    private fun tintImage(image: BufferedImage, tint: RgbColor): BufferedImage {
        val tinted = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        for (x in 0 until image.width) {
            for (y in 0 until image.height) {
                val argb = image.getRGB(x, y)
                val alpha = argb ushr 24 and 0xFF
                if (alpha == 0) {
                    continue
                }

                val red = ((argb ushr 16 and 0xFF) * tint.red / 255.0).roundToInt().coerceIn(0, 255)
                val green = ((argb ushr 8 and 0xFF) * tint.green / 255.0).roundToInt().coerceIn(0, 255)
                val blue = ((argb and 0xFF) * tint.blue / 255.0).roundToInt().coerceIn(0, 255)
                tinted.setRGB(x, y, alpha shl 24 or (red shl 16) or (green shl 8) or blue)
            }
        }
        return tinted
    }

    private fun compositeImages(base: BufferedImage, overlay: BufferedImage): BufferedImage {
        val width = maxOf(base.width, overlay.width)
        val height = maxOf(base.height, overlay.height)
        val composite = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

        for (x in 0 until width) {
            for (y in 0 until height) {
                val baseArgb = sampleImage(base, x, y, width, height)
                val overlayArgb = sampleImage(overlay, x, y, width, height)
                composite.setRGB(x, y, compositePixel(baseArgb, overlayArgb))
            }
        }

        return composite
    }

    private fun sampleImage(image: BufferedImage, x: Int, y: Int, targetWidth: Int, targetHeight: Int): Int {
        val sourceX = x * image.width / targetWidth
        val sourceY = y * image.height / targetHeight
        return image.getRGB(sourceX.coerceIn(0, image.width - 1), sourceY.coerceIn(0, image.height - 1))
    }

    private fun compositePixel(baseArgb: Int, overlayArgb: Int): Int {
        val baseAlpha = (baseArgb ushr 24 and 0xFF) / 255.0
        val overlayAlpha = (overlayArgb ushr 24 and 0xFF) / 255.0
        val outAlpha = overlayAlpha + baseAlpha * (1.0 - overlayAlpha)
        if (outAlpha <= 0.0) {
            return 0
        }

        fun compositeChannel(baseChannel: Int, overlayChannel: Int): Int {
            val value =
                (overlayChannel / 255.0 * overlayAlpha + baseChannel / 255.0 * baseAlpha * (1.0 - overlayAlpha)) / outAlpha
            return (value * 255.0).roundToInt().coerceIn(0, 255)
        }

        val red = compositeChannel(baseArgb ushr 16 and 0xFF, overlayArgb ushr 16 and 0xFF)
        val green = compositeChannel(baseArgb ushr 8 and 0xFF, overlayArgb ushr 8 and 0xFF)
        val blue = compositeChannel(baseArgb and 0xFF, overlayArgb and 0xFF)
        val alpha = (outAlpha * 255.0).roundToInt().coerceIn(0, 255)
        return alpha shl 24 or (red shl 16) or (green shl 8) or blue
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
        zip.getInputStream(entry).bufferedReader().use { it.readText().removePrefix("\uFEFF") }

    private fun parseHexColor(value: String): RgbColor {
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
        const val DEFAULT_GTCEU_FALLBACK_COLOR = "#808080"
        const val GTCEU_ELEMENT_MATERIALS_CLASS = "com/gregtechceu/gtceu/common/data/materials/ElementMaterials.class"
        const val GTCEU_OWNER = "com/gregtechceu/gtceu/GTCEu"
        const val MATERIAL_BUILDER_OWNER = "com/gregtechceu/gtceu/api/data/chemical/material/Material\$Builder"
        const val MATERIAL_ICON_SET_OWNER = "com/gregtechceu/gtceu/api/data/chemical/material/info/MaterialIconSet"
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
