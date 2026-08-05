import org.gradle.internal.extensions.stdlib.capitalized
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

plugins {
    id("multiloader-loader")
    alias(libs.plugins.moddev)
    alias(libs.plugins.kotlinCompose)
}

val modId: String by project
val compose = libs.versions.compose.get()

mixin {
    add(sourceSets.main.get(), "${modId}.refmap.json")
    config("${modId}.mixins.json")
    config("${modId}.forge.mixins.json")
}
tasks.jar {
    manifest {
        attributes["MixinConfigs"] = "${modId}.mixins.json,${modId}.forge.mixins.json"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

neoForge {
    version = libs.versions.forge
    // Automatically enable neoforge AccessTransformers if the file exists
    val at = project(":common").file("src/main/resources/META-INF/accesstransformer.cfg")
    if (at.exists()) {
        accessTransformers.from(at.absolutePath)
    }
    parchment {
        minecraftVersion = libs.versions.parchmentMC
        mappingsVersion = libs.versions.parchment
    }
    runs {
        configureEach {
            systemProperty("forge.enabledGameTestNamespaces", modId)
            ideName = "Forge ${name.capitalized()} (${project.path})" // Unify the run config names with fabric
        }
        register("client") {
            client()
        }
        register("data") {
            data()
        }
        register("server") {
            server()
        }
    }
    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

sourceSets.main.get().resources { srcDir("src/generated/resources") }

dependencies {
    compileOnly(libs.compose.runtime)
    modImplementation(libs.kff)
    annotationProcessor(variantOf(libs.mixin) { classifier("processor") })

    // JiJ onto mod classloader. Non-transitive; skip kotlin-stdlib/coroutines (KFF) and antlr (Forge CP).
    jarJar(project(":kaptor"))
    listOf(
        "org.jetbrains.compose.runtime:runtime-desktop:$compose",
        "androidx.collection:collection-jvm:1.4.0",
        "org.jetbrains.kotlinx:atomicfu-jvm:0.23.2",
    ).forEach { jarJar(it) }

    // 这啥情况为啥必须 jarjar
    jarJar("icyllis.modernui:ModernUI-Core:3.12.0")
    modRuntimeOnly("icyllis.modernui:ModernUI-Forge:1.20.1-3.12.0.1")

//    modRuntimeOnly("dev.ftb.mods:ftb-quests-forge:${libs.versions.ftb.get()}")

    modImplementation(libs.jei.forge)
    modImplementation(libs.emi.forge)
    modImplementation(libs.jade.forge)

    modImplementation(libs.guideme)
    modImplementation(libs.ae2.forge)

    modImplementation(libs.gtceu)

    modCompileOnly("mekanism:Mekanism:${libs.versions.mek.get()}:api")
    modRuntimeOnly("mekanism:Mekanism:${libs.versions.mek.get()}")
    modRuntimeOnly("mekanism:Mekanism:${libs.versions.mek.get()}:additions")
    modRuntimeOnly("mekanism:Mekanism:${libs.versions.mek.get()}:generators")
    modRuntimeOnly("mekanism:Mekanism:${libs.versions.mek.get()}:tools")
}

// Drop module-info so atomicfu does not require a separate kotlin.stdlib module (KFF provides Kotlin).
tasks.named("jarJar") {
    doLast {
        layout.buildDirectory.dir("generated/jarJar").get().asFile.walkTopDown().filter { it.extension == "jar" }
            .forEach { jar ->
                val tmp = jar.resolveSibling("${jar.name}.tmp")
                JarFile(jar).use { input ->
                    JarOutputStream(tmp.outputStream()).use { output ->
                        input.entries().asSequence().filterNot { it.name.endsWith("module-info.class") }
                            .forEach { entry ->
                                output.putNextEntry(JarEntry(entry.name).apply { time = entry.time })
                                if (!entry.isDirectory) input.getInputStream(entry).use { it.copyTo(output) }
                                output.closeEntry()
                            }
                    }
                }
                jar.delete()
                tmp.renameTo(jar)
            }
    }
}

// runClient uses exploded sourceSet resources, not the built jar
tasks.named<ProcessResources>("processResources") {
    from(tasks.named("jarJar"))
}

repositories {
    maven {
        name = "Modrinth"
        url = uri("https://api.modrinth.com/maven")
    }
    maven {
        name = "TerraformersMC"
        url = uri("https://maven.terraformersmc.com/")
    }
    maven {
        name = "ModMaven"
        url = uri("https://modmaven.dev/")
    }
    maven {
        name = "GTCEu Maven"
        url = uri("https://maven.gtceu.com")
    }
    maven {
        name = "JetBrains Compose"
        url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
    maven {
        name = "Google Android"
        url = uri("https://dl.google.com/dl/android/maven2/")
    }
    maven {
        name = "IzzelAliz Maven"
        url = uri("https://maven.izzel.io/releases/")
    }
    maven {
        name = "Architectury Maven"
        url = uri("https://maven.architectury.dev/")
    }
    maven {
        name = "FTB Maven"
        url = uri("https://maven.ftb.dev/releases/")
    }
    maven {
        name = "FirstDarkDev Maven"
        url = uri("https://maven.firstdark.dev/snapshots")
    }
    mavenCentral()
}
