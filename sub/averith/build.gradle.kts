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
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

sourceSets {
    main {
        kotlin.setSrcDirs(listOf("src"))
    }
    test {
        kotlin.setSrcDirs(listOf("test"))
    }
}
