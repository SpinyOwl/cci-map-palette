import java.io.File
import java.util.Properties

plugins {
    base
}

group = "dev.shcha"

val localProperties = Properties().apply {
    val localPropertiesFile = rootDir.resolve("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}

fun configValue(name: String, default: String): String {
    val localValue = localProperties.getProperty(name)?.takeIf { it.isNotBlank() }
    if (localValue != null) {
        return localValue
    }

    return providers.gradleProperty(name).orNull?.takeIf { it.isNotBlank() } ?: default
}

fun resolveConfigPath(value: String): File {
    val file = File(value)
    return if (file.isAbsolute) file else rootDir.resolve(value).normalize()
}

version = configValue("version", "0.1.0")

val resourcepackDirPath = configValue("resourcepackDir", "resourcepack")
val resourcepackDir = layout.projectDirectory.dir(resourcepackDirPath)
val archiveBaseNameValue = configValue("resourcepackArchiveBaseName", "cci-map-palette")
val colorsOutput = resolveConfigPath(configValue(
    "resourcepackColorsFile",
    "resourcepack/assets/chisel_chipped_integration/ftbchunks_block_colors.json"
)).path
val sourceJar = resolveConfigPath(configValue("modsSourceJar", "../../../mods/chisel_chipped_integration-v1.1.6-1.20.1.jar")).path
val instanceResourcepacksDir = configValue("instanceResourcepacksDir", "").takeIf { it.isNotBlank() }?.let(::resolveConfigPath)
val defaultProjectName = rootProject.name

tasks.register("printProjectConfig") {
    group = "help"
    description = "Prints the resolved project configuration from gradle.properties and local.properties."

    doLast {
        println("project.id=${configValue("projectId", defaultProjectName)}")
        println("project.name=${configValue("projectDisplayName", defaultProjectName)}")
        println("project.version=$version")
        println("minecraft.version=${configValue("minecraftVersion", "1.20.1")}")
        println("resourcepack.dir=${resourcepackDir.asFile}")
        println("mods.sourceJar=$sourceJar")
        println("instance.resourcepacksDir=${instanceResourcepacksDir?.path ?: "<unset>"}")
    }
}

tasks.register<Exec>("generateColors") {
    group = "resourcepack"
    description = "Regenerates ftbchunks block colors from the configured mod jar."

    commandLine(
        "powershell",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        layout.projectDirectory.file("scripts/generate-ftbchunks-colors.ps1").asFile.absolutePath,
        "-SourceJar",
        sourceJar,
        "-OutputFile",
        colorsOutput
    )
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
