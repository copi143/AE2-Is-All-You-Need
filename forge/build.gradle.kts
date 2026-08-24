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

sourceSets.main {
    java.srcDir("src")
    kotlin.srcDir("src")
    resources.srcDir("resources")
}

sourceSets.test {
    kotlin.srcDir("test")
}

dependencies {
    implementation(libs.kff)
    annotationProcessor(variantOf(libs.mixin) { classifier("processor") })

    jarJar(project(":kaptor"))
    jarJar(project(":averith"))

    jarJar(libs.ojalgo)
    jarJar(libs.jetbrains.markdown) {
        isTransitive = false
    }
    jarJar(libs.netty.codec.http) {
        isTransitive = false
    }

    // ModernUI-Core is jarJar'd so the dev (exploded) run can load it via Forge's META-INF/jarjar
    // mechanism - it has no mods.toml and no Automatic-Module-Name, so ModLauncher silently skips it
    // on the plain classpath. stripModernUiCore removes it again before packaging so the release jar
    // does NOT embed it (players provide ModernUI themselves).
    jarJar(libs.mui.core)
    modRuntimeOnly(libs.mui)

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
    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
    testImplementation(libs.asm.tree)
    testRuntimeOnly(libs.junit.launcher)
}

val copyTransformerToRunMods = tasks.register("copyTransformerToRunMods") {
    group = "build"
    dependsOn(":transformer:pluginJar")
    doLast {
        val src = project(":transformer").tasks.named("pluginJar").get().outputs.files.singleFile
        val dir = layout.projectDirectory.dir("run/mods").asFile
        dir.mkdirs()
        src.copyTo(dir.resolve("ae2isallyouneed-transformer.jar"), overwrite = true)
    }
}

tasks.matching { it.name.startsWith("prepare") && it.name.contains("Run") }.configureEach {
    dependsOn(copyTransformerToRunMods)
}

afterEvaluate {
    listOf("prepareClientRun", "prepareServerRun", "prepareDataRun").forEach { name ->
        tasks.findByName(name)?.dependsOn(copyTransformerToRunMods)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
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
    dependsOn(copyTransformerToRunMods)
    archiveClassifier.set("mod")
    exclude("META-INF/jarjar/ModernUI-Core-*.jar")
    exclude("META-INF/jarjar/metadata.json")
    from(cleanJarJarMetadata) {
        rename { "META-INF/jarjar/metadata.json" }
    }
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
repositories {
    mavenCentral()
}
