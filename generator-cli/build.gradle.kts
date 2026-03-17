plugins {
    kotlin("jvm") version "2.1.20"
    application
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":generator-core"))
}

application {
    mainClass = "com.spinyowl.ccimap.generator.cli.MainKt"
}
