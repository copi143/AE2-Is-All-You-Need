plugins {
    id("org.jetbrains.kotlin.jvm")
}

group = "averith"
version = "0.0.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    withSourcesJar()
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(kotlin("stdlib"))

    testImplementation(kotlin("test"))
}

sourceSets.main {
    kotlin.setSrcDirs(listOf("src"))
}
sourceSets.test {
    kotlin.setSrcDirs(listOf("test"))
}
