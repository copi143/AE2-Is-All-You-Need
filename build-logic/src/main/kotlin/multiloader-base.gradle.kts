val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

plugins {
    `java-library`
    id("org.jetbrains.kotlin.jvm")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get().toInt()))
    withSourcesJar()
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
}

sourceSets.main {
    kotlin.setSrcDirs(listOf("src"))
}
sourceSets.test {
    kotlin.setSrcDirs(listOf("test"))
}
