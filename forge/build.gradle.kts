import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.internal.extensions.stdlib.capitalized
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

plugins {
    kotlin("kapt")
    id("multiloader-loader")
    alias(libs.plugins.moddev)
    alias(libs.plugins.kotlin.compose)
}

val modId = project.property("modId") as String

mixin {
    add(sourceSets.main.get(), "$modId.refmap.json")
    config("$modId.mixins.json")
    config("$modId.forge.mixins.json")
}

tasks.jar {
    manifest {
        attributes["MixinConfigs"] = "$modId.mixins.json,$modId.forge.mixins.json"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

legacyForge {
    version = libs.versions.forge.get()
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
    implementation(libs.kff)
    annotationProcessor(variantOf(libs.mixin) { classifier("processor") })

    // JiJ onto mod classloader. Non-transitive; skip kotlin-stdlib/coroutines (KFF) and antlr (Forge CP).
    // Compose UI classes are merged straight into the mod jar via :common's composeClasses, not jarJar.
    jarJar(project(":kaptor"))

    // ModernUI-Core is jarJar'd so the dev (exploded) run can load it via Forge's META-INF/jarjar
    // mechanism - it has no mods.toml and no Automatic-Module-Name, so ModLauncher silently skips it
    // on the plain classpath. stripModernUiCore removes it again before packaging so the release jar
    // does NOT embed it (players provide ModernUI themselves).
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
    testImplementation(kotlin("test"))
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

// runClient uses exploded sourceSet resources, not the built jar, so processResources keeps the full
// jarJar payload (incl. ModernUI-Core) for dev. The release jar must NOT embed ModernUI-Core
// (players provide ModernUI themselves), so it drops the core jar and replaces the metadata with a
// clean copy (Forge resolves jarjar strictly through metadata.json -> jars[].path).
tasks.named<ProcessResources>("processResources") {
    from(tasks.named("jarJar"))
}

val cleanJarJarMetadata = tasks.register("cleanJarJarMetadata") {
    dependsOn("jarJar")
    val out = layout.buildDirectory.file("generated/jarJarClean/META-INF/jarjar/metadata.clean.json")
    outputs.file(out)
    doLast {
        val src = layout.buildDirectory.file("generated/jarJar/META-INF/jarjar/metadata.json").get().asFile
        if (!src.isFile) throw GradleException("jarJar metadata not found: $src")
        val data = JsonSlurper().parse(src) as Map<*, *>
        val jars = (data["jars"] as? List<*>)?.filter { entry ->
            val path = (entry as? Map<*, *>)?.get("path") as? String ?: ""
            !path.contains("ModernUI-Core")
        } ?: emptyList<Any>()
        val f = out.get().asFile
        f.parentFile.mkdirs()
        f.writeText(JsonOutput.toJson(mapOf("jars" to jars)))
    }
}

tasks.named<Jar>("jar") {
    exclude("META-INF/jarjar/ModernUI-Core-*.jar")
    exclude("META-INF/jarjar/metadata.json")
    from(cleanJarJarMetadata) {
        rename { "META-INF/jarjar/metadata.json" }
    }
}
repositories {
    mavenCentral()
}
