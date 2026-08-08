plugins {
    id("multiloader-common")
    alias(libs.plugins.moddev)
    alias(libs.plugins.kotlinCompose)
}

neoForge {
    neoFormVersion = libs.versions.neoForm
    // Automatically enable AccessTransformers if the file exists
    val at = file("src/main/resources/META-INF/accesstransformer.cfg")
    if (at.exists()) {
        accessTransformers.from(at.absolutePath)
    }
    parchment {
        minecraftVersion = libs.versions.parchmentMC
        mappingsVersion = libs.versions.parchment
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    compileOnly(libs.mixin)
    api(project(":kaptor"))
    @Suppress("AvoidDuplicateDependencies") listOf(
        libs.compose.runtime,
        libs.compose.ui,
        libs.compose.foundation,
        libs.compose.foundation.layout,
        libs.compose.animation,
        libs.compose.material,
    ).forEach {
        api(it)
        testImplementation(it)
    }

//    modCompileOnly("dev.ftb.mods:ftb-quests:${libs.versions.ftb.get()}")

    modCompileOnly(libs.jei.forge)
    modCompileOnly("dev.emi:emi-xplat-mojmap:${libs.versions.emi.get()}:api")

    modCompileOnly(libs.guideme)
    modCompileOnly(libs.ae2.forge)

    // The moddev-generated minecraft jar does not carry the Forge extension interfaces
    // (net.minecraftforge.common.extensions.*) that GTCEu's IMachineBlockEntity extends.
    compileOnly("net.minecraftforge:forge:${libs.versions.forge.get()}:universal")
    modCompileOnly(libs.gtceu)

    // Mixin's IMixinConfigPlugin declares org.objectweb.asm.tree.ClassNode (and the shaded
    // mixin jar does not bundle ASM), so the plugin needs it on the compile classpath.
    compileOnly("org.ow2.asm:asm-tree:9.8")

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testRuntimeOnly(libs.junit.launcher)
}

configurations["testRuntimeClasspath"].exclude(
    group = "org.jetbrains.compose.ui",
    module = "ui-graphics-desktop",
)

dependencies {
    testRuntimeOnly(files("${rootProject.projectDir}/graphicsrepl/build/libs/ui-graphics-desktop-noskiko.jar"))
}

tasks.withType<Test> {
    useJUnitPlatform()
    dependsOn(":graphicsrepl:patchUiGraphics")
}

// Compose runtime bundle. Resolves the official desktop jars (minus skiko and the official
// ui-graphics-desktop) plus the skiko-free replacement jar, and merges their classes into a single
// directory that fabric/forge then merge straight into their mod jars (no jar-in-jar). The official
// ui-desktop uber jar still bundles androidx.compose.ui.graphics; its classes are dropped here and
// provided exclusively by the noskiko replacement jar to avoid a JPMS split-package at runtime.
val composeRuntime = configurations.create("composeRuntime") {
    isCanBeResolved = true
    isCanBeConsumed = true
    exclude(group = "org.jetbrains.skiko")
    exclude(group = "org.jetbrains.compose.ui", module = "ui-graphics-desktop")
    // KFF 4.12.0 (forge) / FLK (fabric) provide kotlin-stdlib, coroutines and atomicfu on the mod
    // classloader; bundling them again causes JPMS split-package errors (e.g. kotlin.jdk7,
    // kotlin.jvm.functions). KFF 4.12.0's bundled stdlib is new enough for compose 1.12.
    exclude(group = "org.jetbrains.kotlin")
    exclude(group = "org.jetbrains.kotlinx")
}

dependencies {
    composeRuntime.name.let {
        add(it, "org.jetbrains.compose.ui:ui-desktop:${libs.versions.compose.get()}")
        add(it, "org.jetbrains.compose.foundation:foundation-desktop:${libs.versions.compose.get()}")
        add(it, "org.jetbrains.compose.foundation:foundation-layout-desktop:${libs.versions.compose.get()}")
        add(it, "org.jetbrains.compose.animation:animation-desktop:${libs.versions.compose.get()}")
        add(it, "org.jetbrains.compose.material:material-desktop:${libs.versions.compose.get()}")
        add(it, files("${rootProject.projectDir}/graphicsrepl/build/libs/ui-graphics-desktop-noskiko.jar"))
    }
}

val unpackComposeClasses = tasks.register<Sync>("unpackComposeClasses") {
    dependsOn(":graphicsrepl:patchUiGraphics", composeRuntime)
    from(composeRuntime.map { file ->
        if (file.isDirectory) {
            file
        } else if (file.name.startsWith("ui-desktop-")) {
            zipTree(file).matching { exclude("androidx/compose/ui/graphics/**") }
        } else {
            zipTree(file)
        }
    })
    into(layout.buildDirectory.dir("composeClasses"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

configurations.create("composeClasses") {
    isCanBeResolved = false
    isCanBeConsumed = true
}

artifacts {
    add("composeClasses", layout.buildDirectory.dir("composeClasses").map { it.asFile }) {
        builtBy(unpackComposeClasses)
    }
}

configurations {
    create("commonJava") {
        isCanBeResolved = false
        isCanBeConsumed = true
    }
    create("commonKotlin") {
        isCanBeResolved = false
        isCanBeConsumed = true
    }
    create("commonResources") {
        isCanBeResolved = false
        isCanBeConsumed = true
    }
}

sourceSets.create("resgen") {
    compileClasspath += sourceSets.main.get().output
    kotlin.srcDir("resgen")
}.let {
    tasks.register<JavaExec>("generateAssets") {
        description = "Generates assets for the mod using the resgen source set."
        dependsOn(it.compileClasspath)
        dependsOn(tasks.named("classes"))
        classpath = it.runtimeClasspath
        mainClass.set("allyouneed.resgen.MainKt")
        javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
        workingDir = rootProject.layout.projectDirectory.asFile
    }
    tasks.jar {
        dependsOn(it.classesTaskName)
    }
    dependencies {
        "resgenImplementation"("com.github.ajalt.colormath:colormath:3.6.1")
        "resgenImplementation"("com.google.code.gson:gson:2.10.1")
        "resgenImplementation"(libs.compose.runtime)
    }
    sourceSets.main {
        resources.srcDir("res")
    }
}

artifacts {
    add("commonJava", sourceSets.main.get().java.sourceDirectories.first())
    add("commonKotlin", sourceSets.main.get().kotlin.sourceDirectories.filter { !it.name.endsWith("java") }.first())
    sourceSets.main.get().resources.sourceDirectories.forEach { resourceDir ->
        add("commonResources", resourceDir)
    }
}
