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

val copyTransformerToRunMods = tasks.register("copyTransformerToRunMods") {
    group = "build"
    dependsOn(":transformer:pluginJar")
    val src = project(":transformer").layout.buildDirectory.file("libs/ae2isallyouneed-transformer.jar")
    val dest = layout.projectDirectory.file("run/mods/ae2isallyouneed-transformer.jar")
    inputs.file(src)
    outputs.file(dest)
    doLast {
        val destFile = dest.asFile
        destFile.parentFile.mkdirs()
        src.get().asFile.copyTo(destFile, overwrite = true)
    }
}

legacyForge {
    version = libs.versions.forge.get()
    // Automatically enable neoforge AccessTransformers if the file exists
    val at = project(":common").file("resources/META-INF/accesstransformer.cfg")
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
            taskBefore(copyTransformerToRunMods)
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
        register("ae2isallyouneed_core") {
            sourceSet(sourceSets.main.get())
        }
    }
}

kapt {
    keepJavacAnnotationProcessors = true
}

dependencies {
    implementation(libs.kff)
    annotationProcessor(variantOf(libs.mixin) { classifier("processor") })

    implementation(libs.compose.runtime)
    jarJar(project(":kaptor"))
    jarJar(project(":averith"))
    jarJar(project(":indexing"))
    jarJar(project(":composeruntime"))
    jarJar(project(":msdftext"))

    jarJar(libs.ojalgo)
    jarJar(libs.jetbrains.markdown) {
        isTransitive = false
    }
    jarJar(libs.netty.codec.http) {
        isTransitive = false
    }

//    modRuntimeOnly(libs.ftbq)

    modImplementation(libs.jei.forge)
    modImplementation(libs.emi.forge)
    modImplementation(libs.jade.forge)

    modImplementation(libs.guideme)
    modImplementation(libs.ae2.forge)

    modImplementation(libs.gtceu)

    modCompileOnly(variantOf(libs.mek) { classifier("api") })
    modRuntimeOnly(libs.mek)
    modRuntimeOnly(variantOf(libs.mek) { classifier("additions") })
    modRuntimeOnly(variantOf(libs.mek) { classifier("generators") })
    modRuntimeOnly(variantOf(libs.mek) { classifier("tools") })

    // Botania: compile against the api classifier; no runtime dependency here (players provide
    // the full jar, which additionally requires Patchouli/Curios).
    modCompileOnly(variantOf(libs.botania) { classifier("api") })
    testImplementation(libs.asm.tree)
}

fun Task.usesTransformerJar() {
    dependsOn(copyTransformerToRunMods)
}

tasks.matching {
    val n = it.name
    n.startsWith("run") || (n.startsWith("prepare") && n.contains("Run"))
}.configureEach { usesTransformerJar() }

afterEvaluate {
    listOf(
        "runClient", "runServer", "runData",
        "prepareClientRun", "prepareServerRun", "prepareDataRun",
        "prepareRunClient", "prepareRunServer", "prepareRunData",
    ).forEach { name ->
        tasks.findByName(name)?.usesTransformerJar()
    }
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

// runClient uses exploded sourceSet resources, not the built jar, so include the jarJar
// output in processResources for dev runs.
tasks.named<ProcessResources>("processResources") {
    from(tasks.named("jarJar"))
}

tasks.named<Jar>("jar") {
    dependsOn(copyTransformerToRunMods)
    archiveClassifier.set("mod")
}

val wrapForgeJar = tasks.register<Jar>("wrapForgeJar") {
    group = "build"
    description = "Single mods/ jar: transformer plugin + embedded game mod"
    archiveClassifier.set("")
    dependsOn(":transformer:pluginJar")
    from(zipTree(project(":transformer").layout.buildDirectory.file("libs/ae2isallyouneed-transformer.jar"))) {
        exclude("META-INF/MANIFEST.MF")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Automatic-Module-Name"] = "allyouneed.transformer"
    }
}

afterEvaluate {
    val game = tasks.findByName("reobfJar") ?: tasks.named("jar").get()
    wrapForgeJar.configure {
        dependsOn(game)
        from(game.outputs.files) {
            into("META-INF/mod")
            rename { "game.jar" }
        }
    }
}

tasks.named("assemble") {
    dependsOn(wrapForgeJar)
}
