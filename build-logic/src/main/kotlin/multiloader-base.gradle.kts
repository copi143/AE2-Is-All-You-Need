import org.jetbrains.dokka.gradle.DokkaExtension

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

plugins {
    `java-library`
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.dokka-javadoc")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get().toInt()))
    withSourcesJar()
    withJavadocJar()
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
}

sourceSets.main {
    java.srcDirs.clear()
    kotlin.srcDirs.clear()
    kotlin.setSrcDirs(listOf("src"))
    resources.srcDirs.clear()
    resources.setSrcDirs(listOf("resources"))
}

sourceSets.test {
    java.srcDirs.clear()
    kotlin.srcDirs.clear()
    kotlin.setSrcDirs(listOf("test"))
    resources.srcDirs.clear()
    resources.setSrcDirs(listOf("test/resources"))
}

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.junit.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Prevent the default javadoc task from running, as Dokka is
// responsible for generating the docs now
tasks.named<Javadoc>("javadoc") {
    isEnabled = false
}

// Make the javadoc jar take in the Dokka output
tasks.named<Jar>("javadocJar") {
    dependsOn(tasks.named("dokkaGeneratePublicationJavadoc"))
    from(tasks.named("dokkaGeneratePublicationJavadoc"))
}

configure<DokkaExtension> {
    dokkaSourceSets.configureEach {
        skipDeprecated.set(false)
        reportUndocumented.set(false)
        sourceRoots.from(project.the<JavaPluginExtension>().sourceSets["main"].allSource)
    }
}
