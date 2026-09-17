plugins {
    kotlin("kapt")
    id("multiloader-common")
    alias(libs.plugins.moddev)
    alias(libs.plugins.kotlin.compose)
}

legacyForge {
    mcpVersion = libs.versions.neoForm.get()
    // Automatically enable AccessTransformers if the file exists
    val at = file("resources/META-INF/accesstransformer.cfg")
    if (at.exists()) {
        accessTransformers.from(at.absolutePath)
    }
    parchment {
        minecraftVersion = libs.versions.parchmentMC
        mappingsVersion = libs.versions.parchment
    }
}

dependencies {
    compileOnly(libs.mixin)
    api(libs.kotlinx.coroutines.core)
    api(project(":kaptor"))
    api(project(":averith"))
    api(project(":msdftext"))
    api(project(":indexing"))
    api(libs.compose.runtime)
    api(libs.compose.ui)
    api(libs.compose.foundation)
    api(libs.compose.foundation.layout)
    api(libs.compose.animation)
    api(libs.compose.material)
    api(libs.ojalgo)
    api(libs.jetbrains.markdown)
    api(libs.netty.codec.http)

//    modCompileOnly("dev.ftb.mods:ftb-quests:${libs.versions.ftb.get()}")

    modCompileOnly(libs.jei.forge)
    modCompileOnly(variantOf(libs.emi.xplat) { classifier("api") })
    modCompileOnly(libs.emi.forge)

    modCompileOnly(libs.guideme)
    modCompileOnly(libs.ae2.forge)

    // The moddev-generated minecraft jar does not carry the Forge extension interfaces
    // (net.minecraftforge.common.extensions.*) that GTCEu's IMachineBlockEntity extends.
    compileOnly("net.minecraftforge:forge:${libs.versions.forge.get()}:universal")
    modCompileOnly(libs.gtceu)

    // Botania mana integration compiles against the api classifier (Xplat interfaces +
    // BotaniaForgeCapabilities). Runtime is optional; registration only happens when loaded.
    modCompileOnly(variantOf(libs.botania) { classifier("api") })

    // Mixin's IMixinConfigPlugin declares org.objectweb.asm.tree.ClassNode (and the shaded
    // mixin jar does not bundle ASM), so the plugin needs it on the compile classpath.
    compileOnly(libs.asm.tree)
    testImplementation(libs.asm.tree)
    testImplementation(libs.asm.analysis)
    testImplementation(project(":transformer"))
    testImplementation(project(path = ":transformer", configuration = "injectClasses"))
    testImplementation(libs.slf4j)

    testImplementation("org.lwjgl:lwjgl:3.3.1")
    testRuntimeOnly("org.lwjgl:lwjgl:3.3.1:natives-linux")
    // MSDF 像素级诊断测试:真 GL 上下文复刻渲染器采样/混合状态。
    for (mod in listOf("glfw", "opengl", "stb")) {
        testImplementation("org.lwjgl:lwjgl-$mod:3.3.1")
        testRuntimeOnly("org.lwjgl:lwjgl-$mod:3.3.1:natives-linux")
    }
    // fastutil 由 Minecraft 内嵌提供（不在测试 classpath），这里仅为测试暴露其类。
    testImplementation("it.unimi.dsi:fastutil:8.5.9")

    testRuntimeOnly(project(":composeruntime"))
    testRuntimeOnly(project(":msdftext"))
    testRuntimeOnly(project(":graphicsrepl"))
}

configurations["testRuntimeClasspath"].exclude(
    group = "org.jetbrains.compose.ui",
    module = "ui-graphics-desktop",
)

tasks.withType<Test> {
    dependsOn(":graphicsrepl:jar")
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

sourceSets.main {
    kotlin.srcDirs("minecraftx", "ae2x")
    resources.srcDirs("res")
}

sourceSets.create("resgen") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
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
        inputs.dir(layout.projectDirectory.dir("resgen"))
        outputs.dir(layout.projectDirectory.dir("res"))
    }
    tasks.jar {
        dependsOn(it.classesTaskName)
    }
    dependencies {
        "resgenImplementation"("com.github.ajalt.colormath:colormath:3.6.1")
        "resgenImplementation"("com.google.code.gson:gson:2.10.1")
        "resgenImplementation"(libs.compose.runtime)
        "resgenImplementation"(libs.ojalgo)
    }
}

artifacts {
    sourceSets.main.get().java.sourceDirectories.forEach { resourceDir ->
        add("commonJava", resourceDir)
    }
    sourceSets.main.get().kotlin.sourceDirectories.forEach { resourceDir ->
        add("commonKotlin", resourceDir)
    }
    sourceSets.main.get().resources.sourceDirectories.forEach { resourceDir ->
        add("commonResources", resourceDir)
    }
}
