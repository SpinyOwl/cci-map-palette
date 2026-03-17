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
    mainClass = "dev.shcha.ccimap.generator.cli.MainKt"
}
