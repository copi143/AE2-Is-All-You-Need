import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

sourceSets.main {
    kotlin.setSrcDirs(listOf("src"))
    resources.setSrcDirs(listOf("resources"))
}

val embed = configurations.create("embed")
val r8 = configurations.create("r8")
val r8Lib = configurations.create("r8Lib")

dependencies {
    compileOnly(kotlin("stdlib"))
    compileOnly(libs.asm.tree)
    compileOnly(libs.asm.analysis)
    compileOnly("cpw.mods:modlauncher:10.0.9")
    compileOnly("org.jetbrains:annotations:26.0.2")
    compileOnly("net.minecraftforge:forgespi:7.0.1")
    compileOnly(libs.slf4j)
    compileOnly(libs.fml) { isTransitive = false }
    embed(kotlin("stdlib"))
    embed(libs.asm.analysis) { isTransitive = false }
    r8(libs.r8)
    r8Lib(libs.slf4j)
    r8Lib(libs.asm)
    r8Lib(libs.asm.tree)
    r8Lib("cpw.mods:modlauncher:10.0.9")
    r8Lib("net.minecraftforge:forgespi:7.0.1")
    r8Lib(libs.fml) { isTransitive = false }
    testImplementation(kotlin("stdlib"))
    testImplementation(libs.asm.tree)
    testImplementation(libs.asm.analysis)
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.junit.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xno-param-assertions",
            "-Xno-call-assertions",
            "-Xno-receiver-assertions",
        )
    }
}

val r8Output = layout.buildDirectory.file("r8/shrunk.jar")
val pluginOutput = layout.buildDirectory.file("libs/ae2isallyouneed-transformer.jar")
val r8Rules = layout.projectDirectory.file("r8.pro")
val r8Jdk = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(17))
}

val r8Jar = tasks.register<JavaExec>("r8Jar") {
    group = "build"
    description = "Shrink and inline Kotlin stdlib into transformer classes"
    dependsOn(tasks.jar)
    classpath = r8
    mainClass.set("com.android.tools.r8.R8")
    inputs.files(tasks.jar)
    inputs.files(embed)
    inputs.files(r8Lib)
    inputs.file(r8Rules)
    outputs.file(r8Output)
    argumentProviders.add(
        CommandLineArgumentProvider {
            val out = r8Output.get().asFile
            out.parentFile.mkdirs()
            out.delete()
            buildList {
                add("--release")
                add("--classfile")
                add("--output")
                add(out.absolutePath)
                add("--pg-conf")
                add(r8Rules.asFile.absolutePath)
                add("--lib")
                add(r8Jdk.get().metadata.installationPath.asFile.absolutePath)
                r8Lib.forEach {
                    add("--lib")
                    add(it.absolutePath)
                }
                add(tasks.jar.get().archiveFile.get().asFile.absolutePath)
                embed.filter { it.isFile }.forEach { add(it.absolutePath) }
            }
        },
    )
}

val pluginJar = tasks.register("pluginJar") {
    group = "build"
    description = "Package R8 output as the plugin jar"
    dependsOn(r8Jar)
    inputs.file(r8Output)
    outputs.file(pluginOutput)
    doLast {
        val src = r8Output.get().asFile
        val dest = pluginOutput.get().asFile
        dest.parentFile.mkdirs()
        ZipFile(src).use { zip ->
            ZipOutputStream(dest.outputStream().buffered()).use { out ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    if (name == "META-INF/versions" || name.startsWith("META-INF/versions/")) continue
                    if (name.startsWith("META-INF/maven/")) continue
                    if (name.endsWith(".kotlin_builtins") || name.endsWith("module-info.class")) continue
                    if (name.endsWith(".kotlin_module")) continue
                    val bytes = if (name == "META-INF/MANIFEST.MF") {
                        buildString {
                            appendLine("Manifest-Version: 1.0")
                            appendLine("Automatic-Module-Name: allyouneed.transformer")
                        }.toByteArray()
                    } else {
                        zip.getInputStream(entry).readBytes()
                    }
                    out.putNextEntry(ZipEntry(name))
                    out.write(bytes)
                    out.closeEntry()
                }
            }
        }
    }
}

tasks.named("assemble") {
    dependsOn(pluginJar)
}
