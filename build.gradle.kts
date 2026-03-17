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

allprojects {
    group = rootProject.group
    version = configValue("version", "0.1.0")
}

subprojects {
    repositories {
        mavenCentral()
    }
}

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
        println("mods.sourceJar=${configuredSourceJar.path}")
        println("colors.sourceNamespace=$configuredSourceNamespace")
        println("resourcepack.colorsFile=${configuredColorsOutput.path}")
        println("instance.resourcepacksDir=${instanceResourcepacksDir?.path ?: "<unset>"}")
    }
}

tasks.register<JavaExec>("generateColors") {
    group = "resourcepack"
    description = "Regenerates ftbchunks block colors. Override with -PgenerateColorsSourceJar, -PgenerateColorsOutputFile, and -PgenerateColorsSourceNamespace."

    dependsOn(":generator-cli:classes")
    classpath = project(":generator-cli").extensions
        .getByType(org.gradle.api.plugins.JavaPluginExtension::class.java)
        .sourceSets
        .getByName("main")
        .runtimeClasspath
    mainClass.set("dev.shcha.ccimap.generator.cli.MainKt")

    val taskSourceJar = providers.gradleProperty("generateColorsSourceJar")
        .orNull
        ?.let(::resolveConfigPath)
        ?: configuredSourceJar
    val taskOutputFile = providers.gradleProperty("generateColorsOutputFile")
        .orNull
        ?.let(::resolveConfigPath)
        ?: configuredColorsOutput
    val taskSourceNamespace = providers.gradleProperty("generateColorsSourceNamespace").orNull
        ?: configuredSourceNamespace

    args(
        "--source-jar",
        taskSourceJar.path,
        "--output-file",
        taskOutputFile.path,
        "--source-namespace",
        taskSourceNamespace
    )

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
