package dev.shcha.ccimap.generator.core

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper

internal class BlockModelResolver(
    private val objectMapper: ObjectMapper,
    private val resources: ZipResourceSet
) {
    private val mapType = object : TypeReference<Map<String, Any?>>() {}

    fun extractModelIds(blockstateText: String, entryPath: String): List<String> {
        val blockstateJson = objectMapper.readValue(blockstateText, mapType)
        val models = collectModelIds(blockstateJson)
        require(models.isNotEmpty()) { "No model found in blockstate: $entryPath" }
        return models.distinct()
    }

    fun resolveModel(modelEntryPath: String, visited: MutableSet<String> = linkedSetOf()): ResolvedModel {
        require(visited.add(modelEntryPath)) {
            "Model parent cycle detected: ${visited.joinToString(" -> ")} -> $modelEntryPath"
        }

        val modelJson = resources.readJsonMap(modelEntryPath)
        return resolveModelFromJson(modelJson, modelEntryPath, visited)
    }

    fun toModelEntryPath(modelId: String): String {
        val separatorIndex = modelId.indexOf(':')
        require(separatorIndex >= 0) { "Model id is missing namespace: $modelId" }
        val namespace = modelId.substring(0, separatorIndex)
        val path = modelId.substring(separatorIndex + 1)
        return "assets/$namespace/models/$path.json"
    }

    private fun resolveModelFromJson(
        modelJson: Map<String, Any?>,
        modelEntryPath: String,
        visited: MutableSet<String>
    ): ResolvedModel {
        val parentModel = (modelJson["parent"] as? String)
            ?.let { resolveParentModelEntryPath(it, modelEntryPath) }
            ?.let { parentEntryPath ->
                try {
                    resolveModel(parentEntryPath, visited)
                } catch (exception: Exception) {
                    if (parentEntryPath.startsWith("assets/minecraft/models/")) null else throw exception
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
                    resolveModelFromJson(child, modelEntryPath, visited.toMutableSet())
                }
            }
        )
    }

    private fun collectModelIds(value: Any?): List<String> =
        when (value) {
            is Map<*, *> -> {
                val directModel = value["model"]?.toString()?.let(::listOf) ?: emptyList()
                directModel + value.values.flatMap(::collectModelIds)
            }

            is List<*> -> value.flatMap(::collectModelIds)
            else -> emptyList()
        }

    private fun readOwnTextures(modelJson: Map<String, Any?>, modelEntryPath: String): Map<String, String> {
        val texturesValue = modelJson["textures"] ?: return emptyMap()
        require(texturesValue is Map<*, *>) { "Unexpected textures section in model: $modelEntryPath" }
        return texturesValue.entries.associate { (key, value) -> key.toString() to value.toString() }
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
}
