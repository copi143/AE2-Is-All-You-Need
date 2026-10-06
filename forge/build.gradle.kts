import org.gradle.internal.extensions.stdlib.capitalized
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

plugins {
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
    manifest.attributes["MixinConfigs"] = "$modId.mixins.json,$modId.forge.mixins.json"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val transformerPlugin = configurations.create("transformerPlugin") {
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    transformerPlugin(project(path = ":transformer", configuration = "plugin"))
}

val copyTransformerToRunMods = tasks.register<Copy>("copyTransformerToRunMods") {
    group = "build"
    from(transformerPlugin)
    into(layout.projectDirectory.dir("run/mods"))
}

legacyForge {
    version = libs.versions.forge.get()
    project(":common").file("resources/META-INF/accesstransformer.cfg").takeIf { it.exists() }
        ?.let { accessTransformers.from(it.absolutePath) }
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
        register("client") { client() }
        register("server") { server() }
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

dependencies {
    implementation(libs.kff)
    annotationProcessor(variantOf(libs.mixin) { classifier("processor") })

    implementation(libs.compose.runtime)
    compileOnly(project(":composeruntime"))

    jarJar(project(":averith"))
    jarJar(project(":indexing"))
    jarJar(project(":composeruntime"))
    jarJar(project(":msdftext"))
    // antlr 是 forge 的依赖，所以不用打包
    jarJar(libs.ojalgo)
    jarJar(libs.jetbrains.markdown) {
        isTransitive = false
    }
    jarJar(libs.netty.codec.http) {
        isTransitive = false
    }

    modImplementation(libs.jei.forge)
    modImplementation(libs.emi.forge)
    modImplementation(libs.jade.forge)

    modImplementation(libs.guideme)
    modImplementation(libs.ae2.forge)

    // ========================= 兼容模组 =========================

    jarJarCompileOnly(libs.gtceu)
    modRuntimeOnly(libs.gtceu)

    modCompileOnly(variantOf(libs.mek) { classifier("api") })
    modRuntimeOnly(libs.mek)
    modRuntimeOnly(variantOf(libs.mek) { classifier("additions") })
    modRuntimeOnly(variantOf(libs.mek) { classifier("generators") })
    modRuntimeOnly(variantOf(libs.mek) { classifier("tools") })

    modCompileOnly(variantOf(libs.botania) { classifier("api") })
    modRuntimeOnly(libs.botania)
    modRuntimeOnly("maven.modrinth:nU0bVIaL:94dtOLgZ") // Patchouli
    modRuntimeOnly("maven.modrinth:vvuO3ImH:IPQlZkz1") // Curios API

    // ========================= AE 扩展 =========================

    modRuntimeOnly("maven.modrinth:UhW5uCKw:eoUaDkZf") // Glodium
    modRuntimeOnly("maven.modrinth:JiOqfoFM:uq3lO4ER") // Extended AE

    // ========================= 测试环境 =========================

    modRuntimeOnly("maven.modrinth:lhGA9TYQ:1MKTLiiG") // Architectury API
    modRuntimeOnly("maven.modrinth:9s6osm5g:t8TXrZvZ") // Cloth Config API
    modRuntimeOnly("maven.modrinth:KZO4S4DO:xDOvxyqP") // Powah!
    modRuntimeOnly("maven.modrinth:tIm2nV03:WUzj4tgJ") // Immersive Engineering
    modRuntimeOnly("maven.modrinth:LNytGWDc:8amzvn9x") // Create

    testImplementation(libs.asm.tree)
}

configurations.compileClasspath.get().exclude(
    group = "org.jetbrains.compose.ui",
    module = "ui-graphics-desktop",
)

fun Task.usesTransformerJar() {
    dependsOn(copyTransformerToRunMods)
}

tasks.matching {
    val n = it.name
    n.startsWith("run") || (n.startsWith("prepare") && n.contains("Run"))
}.configureEach { usesTransformerJar() }

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
    manifest.attributes["Automatic-Module-Name"] = "allyouneed.transformer"
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
