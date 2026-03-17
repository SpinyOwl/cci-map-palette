import java.io.File

plugins {
    base
}

group = "com.spinyowl"

data class LocalYamlConfig(
    val values: Map<String, String>,
    val colorGenerationSpecs: List<ColorGenerationSpec>
)

fun parseLocalYaml(file: File): LocalYamlConfig {
    if (!file.exists()) {
        return LocalYamlConfig(emptyMap(), emptyList())
    }

    val values = linkedMapOf<String, String>()
    val specs = mutableListOf<ColorGenerationSpec>()
    val lines = file.readLines()
    var index = 0

    fun currentIndent(rawLine: String): Int = rawLine.takeWhile { it == ' ' }.length
    fun trimComment(rawLine: String): String = rawLine.substringBefore('#').trimEnd()

    while (index < lines.size) {
        val rawLine = lines[index]
        val line = trimComment(rawLine)
        if (line.isBlank()) {
            index++
            continue
        }

        val trimmed = line.trim()
        if (trimmed.startsWith("- ")) {
            error("Unexpected top-level list entry in ${file.name}: $trimmed")
        }

        if (trimmed == "colorGenerationSpecs:") {
            index++
            while (index < lines.size) {
                val itemRawLine = lines[index]
                val itemLine = trimComment(itemRawLine)
                if (itemLine.isBlank()) {
                    index++
                    continue
                }

                val itemIndent = currentIndent(itemRawLine)
                if (itemIndent == 0) {
                    break
                }

                val itemTrimmed = itemLine.trim()
                require(itemTrimmed.startsWith("- ")) {
                    "Expected colorGenerationSpecs list item in ${file.name}: $itemTrimmed"
                }

                val itemValues = linkedMapOf<String, String>()
                val firstEntry = itemTrimmed.removePrefix("- ").trim()
                if (firstEntry.isNotBlank()) {
                    val keyValue = firstEntry.split(":", limit = 2)
                    require(keyValue.size == 2) { "Invalid YAML entry in ${file.name}: $firstEntry" }
                    itemValues[keyValue[0].trim()] = keyValue[1].trim().removeSurrounding("\"")
                }

                index++
                while (index < lines.size) {
                    val nestedRawLine = lines[index]
                    val nestedLine = trimComment(nestedRawLine)
                    if (nestedLine.isBlank()) {
                        index++
                        continue
                    }

                    val nestedIndent = currentIndent(nestedRawLine)
                    if (nestedIndent <= itemIndent) {
                        break
                    }

                    val nestedTrimmed = nestedLine.trim()
                    val keyValue = nestedTrimmed.split(":", limit = 2)
                    require(keyValue.size == 2) { "Invalid YAML entry in ${file.name}: $nestedTrimmed" }
                    itemValues[keyValue[0].trim()] = keyValue[1].trim().removeSurrounding("\"")
                    index++
                }

                val sourceNamespace = itemValues["sourceNamespace"]
                    ?: error("Missing sourceNamespace in colorGenerationSpecs item")

                specs += ColorGenerationSpec(
                    sourceNamespace = sourceNamespace,
                    sourceJar = resolveConfigPath(
                        itemValues["sourceJar"] ?: error("Missing sourceJar in colorGenerationSpecs item")
                    ),
                    assetJars = itemValues["assetJars"]
                        ?.split(',')
                        ?.map(String::trim)
                        ?.filter(String::isNotEmpty)
                        ?.map(::resolveConfigPath)
                        ?: emptyList(),
                    outputFile = itemValues["outputFile"]?.let(::resolveConfigPath)
                        ?: defaultColorsOutputFile(sourceNamespace),
                    blockstatePattern = itemValues["blockstatePattern"],
                    preferredTextureKeys = itemValues["preferredTextureKeys"]
                )
            }
            continue
        }

        val keyValue = trimmed.split(":", limit = 2)
        require(keyValue.size == 2) { "Invalid YAML entry in ${file.name}: $trimmed" }
        values[keyValue[0].trim()] = keyValue[1].trim().removeSurrounding("\"")
        index++
    }

    return LocalYamlConfig(values, specs)
}

val localYaml = parseLocalYaml(rootDir.resolve("local.yml"))

fun configValue(name: String, default: String): String {
    val localValue = localYaml.values[name]?.takeIf { it.isNotBlank() }
    if (localValue != null) {
        return localValue
    }

    return providers.gradleProperty(name).orNull?.takeIf { it.isNotBlank() } ?: default
}

fun resolveConfigPath(value: String): File {
    val file = File(value)
    return if (file.isAbsolute) file else rootDir.resolve(value).normalize()
}

data class ColorGenerationSpec(
    val sourceNamespace: String,
    val sourceJar: File,
    val assetJars: List<File> = emptyList(),
    val outputFile: File,
    val blockstatePattern: String? = null,
    val preferredTextureKeys: String? = null
)

fun defaultColorsOutputFile(sourceNamespace: String): File =
    resolveConfigPath("resourcepack/assets/$sourceNamespace/ftbchunks_block_colors.json")

fun parseColorGenerationSpecs(rawValue: String): List<ColorGenerationSpec> {
    if (rawValue.isBlank()) {
        return emptyList()
    }

    return rawValue.split(';')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map { entry ->
            val parts = entry.split('|').map(String::trim)
            require(parts.size >= 2) {
                "Each colorGenerationSpecs entry must contain namespace|sourceJar and optionally outputFile|blockstatePattern|preferredTextureKeys|assetJars: $entry"
            }

            ColorGenerationSpec(
                sourceNamespace = parts[0],
                sourceJar = resolveConfigPath(parts[1]),
                assetJars = parts.getOrNull(5)?.takeIf { it.isNotBlank() }
                    ?.split(',')
                    ?.map(String::trim)
                    ?.filter(String::isNotEmpty)
                    ?.map(::resolveConfigPath)
                    ?: emptyList(),
                outputFile = parts.getOrNull(2)?.takeIf { it.isNotBlank() }?.let(::resolveConfigPath)
                    ?: defaultColorsOutputFile(parts[0]),
                blockstatePattern = parts.getOrNull(3)?.takeIf { it.isNotBlank() },
                preferredTextureKeys = parts.getOrNull(4)?.takeIf { it.isNotBlank() }
            )
        }
}

allprojects {
    group = rootProject.group
    version = configValue("version", "0.1.0")
}

subprojects {
    repositories {
        mavenCentral()
    }
}

evaluationDependsOn(":generator-core")
evaluationDependsOn(":generator-cli")

val resourcepackDirPath = configValue("resourcepackDir", "resourcepack")
val resourcepackDir = layout.projectDirectory.dir(resourcepackDirPath)
val archiveBaseNameValue = configValue("resourcepackArchiveBaseName", "cci-map-palette")
val configuredColorsOutput = resolveConfigPath(
    configValue(
        "resourcepackColorsFile",
        "resourcepack/assets/chisel_chipped_integration/ftbchunks_block_colors.json"
    )
)
val configuredSourceJar = resolveConfigPath(configValue("modsSourceJar", "../../../mods/chisel_chipped_integration-v1.1.6-1.20.1.jar"))
val configuredSourceNamespace = configValue("colorsSourceNamespace", "chisel_chipped_integration")
val configuredColorGenerationSpecs = if (localYaml.colorGenerationSpecs.isNotEmpty()) {
    localYaml.colorGenerationSpecs
} else {
    parseColorGenerationSpecs(configValue("colorGenerationSpecs", ""))
}
val instanceResourcepacksDir = configValue("instanceResourcepacksDir", "").takeIf { it.isNotBlank() }?.let(::resolveConfigPath)
val defaultProjectName = rootProject.name
val generatorCliProject = project(":generator-cli")
val generatorCliRuntimeClasspath = files(
    generatorCliProject.tasks.named("jar"),
    generatorCliProject.configurations.named("runtimeClasspath")
)

tasks.register("printProjectConfig") {
    group = "help"
    description = "Prints the resolved project configuration from gradle.properties and local.yml."

    doLast {
        println("project.id=${configValue("projectId", defaultProjectName)}")
        println("project.name=${configValue("projectDisplayName", defaultProjectName)}")
        println("project.version=$version")
        println("minecraft.version=${configValue("minecraftVersion", "1.20.1")}")
        println("resourcepack.dir=${resourcepackDir.asFile}")
        println("mods.sourceJar=${configuredSourceJar.path}")
        println("colors.sourceNamespace=$configuredSourceNamespace")
        println("resourcepack.colorsFile=${configuredColorsOutput.path}")
        println("colors.generationSpecs=${configuredColorGenerationSpecs.size}")
        println("instance.resourcepacksDir=${instanceResourcepacksDir?.path ?: "<unset>"}")
    }
}

tasks.register<JavaExec>("generateColors") {
    group = "resourcepack"
    description = "Regenerates ftbchunks block colors. Override with -PgenerateColorsSourceJar, -PgenerateColorsOutputFile, and -PgenerateColorsSourceNamespace."

    dependsOn(":generator-cli:jar")
    classpath = generatorCliRuntimeClasspath
    mainClass.set("dev.shcha.ccimap.generator.cli.MainKt")

    val taskSourceJar = providers.gradleProperty("generateColorsSourceJar")
        .orNull
        ?.let(::resolveConfigPath)
        ?: configuredSourceJar
    val taskAssetJars = providers.gradleProperty("generateColorsAssetJars")
        .orNull
        ?.split(',')
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?.map(::resolveConfigPath)
        ?: emptyList()
    val taskOutputFile = providers.gradleProperty("generateColorsOutputFile")
        .orNull
        ?.let(::resolveConfigPath)
    val taskSourceNamespace = providers.gradleProperty("generateColorsSourceNamespace").orNull
        ?: configuredSourceNamespace
    val resolvedTaskOutputFile = taskOutputFile
        ?: if (providers.gradleProperty("generateColorsSourceNamespace").isPresent) {
            defaultColorsOutputFile(taskSourceNamespace)
        } else {
            configuredColorsOutput
        }

    args(
        "--source-jar",
        taskSourceJar.path,
        "--output-file",
        resolvedTaskOutputFile.path,
        "--source-namespace",
        taskSourceNamespace
    )

    if (taskAssetJars.isNotEmpty()) {
        args("--asset-jars", taskAssetJars.joinToString(",") { it.path })
    }

    val blockstatePattern = providers.gradleProperty("generateColorsBlockstatePattern").orNull
        ?: providers.gradleProperty("colorsBlockstatePattern").orNull
    if (!blockstatePattern.isNullOrBlank()) {
        args("--blockstate-pattern", blockstatePattern)
    }

    val preferredKeys = providers.gradleProperty("generateColorsPreferredTextureKeys").orNull
        ?: providers.gradleProperty("colorsPreferredTextureKeys").orNull
    if (!preferredKeys.isNullOrBlank()) {
        args("--preferred-texture-keys", preferredKeys)
    }
}

val configuredColorGenerationTasks = configuredColorGenerationSpecs.map { spec ->
    val taskName = "generateColors${spec.sourceNamespace.split('_', '-', '.')
        .filter(String::isNotBlank)
        .joinToString("") { token -> token.replaceFirstChar(Char::uppercaseChar) }}"

    tasks.register<JavaExec>(taskName) {
        group = "resourcepack"
        description = "Generates a color map for ${spec.sourceNamespace}."

        dependsOn(":generator-cli:jar")
        classpath = generatorCliRuntimeClasspath
        mainClass.set("dev.shcha.ccimap.generator.cli.MainKt")
        args(
            "--source-jar",
            spec.sourceJar.path,
            "--output-file",
            spec.outputFile.path,
            "--source-namespace",
            spec.sourceNamespace
        )

        if (spec.assetJars.isNotEmpty()) {
            args("--asset-jars", spec.assetJars.joinToString(",") { it.path })
        }

        if (!spec.blockstatePattern.isNullOrBlank()) {
            args("--blockstate-pattern", spec.blockstatePattern)
        }

        if (!spec.preferredTextureKeys.isNullOrBlank()) {
            args("--preferred-texture-keys", spec.preferredTextureKeys)
        }
    }
}

tasks.register("generateAllColors") {
    group = "resourcepack"
    description = "Generates color maps for all entries in the colorGenerationSpecs variable."

    doFirst {
        require(configuredColorGenerationTasks.isNotEmpty()) {
            "No colorGenerationSpecs configured. Set the variable in gradle.properties or local.yml."
        }
    }

    dependsOn(configuredColorGenerationTasks)
}

val packResourcepack by tasks.registering(Zip::class) {
    group = "build"
    description = "Builds a distributable zip of the resource pack."

    archiveBaseName.set(archiveBaseNameValue)
    archiveVersion.set(version.toString())
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(resourcepackDir)
}

tasks.assemble {
    dependsOn(packResourcepack)
}

tasks.register<Copy>("syncResourcepack") {
    group = "deployment"
    description = "Copies the resourcepack directory into the configured Minecraft instance resourcepacks folder."

    onlyIf { instanceResourcepacksDir != null }
    from(resourcepackDir)
    into(instanceResourcepacksDir ?: layout.buildDirectory.dir("noop").get().asFile)
}

tasks.register<Copy>("syncPackedResourcepack") {
    group = "deployment"
    description = "Copies the built resourcepack zip into the configured Minecraft instance resourcepacks folder."

    dependsOn(packResourcepack)
    onlyIf { instanceResourcepacksDir != null }
    from(packResourcepack.flatMap { it.archiveFile })
    into(instanceResourcepacksDir ?: layout.buildDirectory.dir("noop").get().asFile)
}
