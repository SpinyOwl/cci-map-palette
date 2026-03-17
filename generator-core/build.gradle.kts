plugins {
    kotlin("jvm") version "2.1.20"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.4")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.4")
}
