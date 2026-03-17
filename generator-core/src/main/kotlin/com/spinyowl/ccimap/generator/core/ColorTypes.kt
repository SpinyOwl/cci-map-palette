package com.spinyowl.ccimap.generator.core

internal data class ResolvedModel(
    val textures: Map<String, String>,
    val elements: List<Map<String, Any?>> = emptyList(),
    val loader: String? = null,
    val tintIndex: Int? = null,
    val baseTintIndex: Int? = null,
    val compositeChildren: List<ResolvedModel> = emptyList()
)

internal data class RgbColor(
    val red: Int,
    val green: Int,
    val blue: Int
) {
    fun toHex(): String = "#%02X%02X%02X".format(red, green, blue)
}

internal data class GtceuMaterialDefinition(
    val id: String,
    var primaryColor: Int? = null,
    var secondaryColor: Int? = null,
    var iconSet: String? = null,
    var hasSolidForm: Boolean = false
)
