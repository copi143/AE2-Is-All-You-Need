plugins {
    id("multiloader-common")
    alias(libs.plugins.moddev)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

legacyForge {
    mcpVersion = libs.versions.neoForm.get()
    file("resources/META-INF/accesstransformer.cfg").takeIf { it.exists() }
        ?.let { accessTransformers.from(it.absolutePath) }
    parchment {
        minecraftVersion = libs.versions.parchmentMC
        mappingsVersion = libs.versions.parchment
    }
}

dependencies {
    compileOnly(libs.mixin)
    compileOnlyApi(project(":valueschema"))
    ksp(project(":valueschema"))
    api(libs.kotlinx.coroutines.core)
    api(project(":averith"))
    api(project(":msdftext"))
    api(project(":indexing"))
    api(libs.compose.runtime)
    api(libs.compose.ui)
    api(libs.compose.foundation)
    api(libs.compose.foundation.layout)
    api(libs.compose.animation)
    api(libs.compose.material)
    // 编译期也可见 composeruntime 的 skiko 替换类（含新增的 Path/ImageBitmap 纯 JVM 实现）；
    // 运行时 fabric include / forge jarJar 本来打包的就是这个 repacked jar。
    compileOnly(project(":composeruntime"))
    api(libs.ojalgo)
    api(libs.jetbrains.markdown)
    api(libs.netty.codec.http)

    modCompileOnly(libs.jei.forge)
    modCompileOnly(variantOf(libs.emi.xplat) { classifier("api") })
    modCompileOnly(libs.emi.forge)

    modCompileOnly(libs.guideme)
    modCompileOnly(libs.ae2.forge)

    // The moddev-generated minecraft jar does not carry the Forge extension interfaces
    // (net.minecraftforge.common.extensions.*) that GTCEu's IMachineBlockEntity extends.
    compileOnly(variantOf(libs.forge) { classifier("universal") })
    jarJarCompileOnly(libs.gtceu)

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
    testCompileOnly(project(":composeruntime"))

    testImplementation("org.lwjgl:lwjgl:3.3.1")
    testRuntimeOnly("org.lwjgl:lwjgl:3.3.1:natives-linux")
    // MSDF 像素级诊断测试:真 GL 上下文复刻渲染器采样/混合状态。
    for (mod in listOf("glfw", "opengl", "stb")) {
        testImplementation("org.lwjgl:lwjgl-$mod:3.3.1")
        testRuntimeOnly("org.lwjgl:lwjgl-$mod:3.3.1:natives-linux")
    }
    // fastutil 由 Minecraft 内嵌提供（不在测试 classpath），这里仅为测试暴露其类。
    testImplementation("it.unimi.dsi:fastutil:8.5.9")
    // 裁剪测试直接使用 Minecraft 编译环境的 JOML，保持矩阵实现与游戏运行时一致。
    testImplementation(files(configurations.named("compileClasspath").map { classpath ->
        classpath.filter { it.name.startsWith("joml-") }
    }))
    // Component 继承 Brigadier 的 Message，文本组件测试需要与 Minecraft 一致的类定义。
    testImplementation(files(configurations.named("compileClasspath").map { classpath ->
        classpath.filter { it.name.startsWith("brigadier-") }
    }))
    // 文本样式初始化 ExtraCodecs 时会引用 Authlib 的 Property。
    testRuntimeOnly(files(configurations.named("compileClasspath").map { classpath ->
        classpath.filter { it.name.startsWith("authlib-") }
    }))
    // PoseStack 初始化 Minecraft.Util 时需要 Mojang 的日志桥接。
    testRuntimeOnly(files(configurations.named("compileClasspath").map { classpath ->
        classpath.filter { it.name.startsWith("logging-") }
    }))
    // NetworkLogPage 等类型继承 AE2 的 PacketWritable，测试需要其类定义在 classpath 上。
    testImplementation(libs.ae2.forge)
    // NBT 相关测试需要 Minecraft 类；直接使用 MDG 产出的 merged jar（与 main 编译用的一致）。
    val minecraftMerged = layout.buildDirectory.file("moddev/artifacts/vanilla-1.20.1-merged.jar")
    testCompileOnly(files(minecraftMerged) { builtBy("createMinecraftArtifacts") })
    testRuntimeOnly(files(minecraftMerged) { builtBy("createMinecraftArtifacts") })
    // merged jar 无 POM，MC 1.20.1 的 DataFixerUpper 传递依赖需显式补（NBT 序列化用）。
    testRuntimeOnly("com.mojang:datafixerupper:6.0.8")

    testRuntimeOnly(project(":composeruntime"))
    testRuntimeOnly(project(":msdftext"))

    "resgenImplementation"("com.github.ajalt.colormath:colormath:3.6.1")
    "resgenImplementation"("com.google.code.gson:gson:2.10.1")
    "resgenImplementation"(libs.compose.runtime)
    "resgenImplementation"(libs.ojalgo)
}

// 主源码、测试编译与测试运行统一使用 composeruntime 的重打包 jar。
//（官方 ui-graphics-desktop 会遮挡其中的 skiko 替换类与新增 API）。
listOf("compileClasspath", "testCompileClasspath", "testRuntimeClasspath").forEach {
    configurations.named(it) {
        exclude(group = "org.jetbrains.compose.ui", module = "ui-graphics-desktop")
    }
}
// datafixerupper 6.0.8 依赖的 guava 31.0.1/error_prone 2.7.1 不在本地缓存；强制到已缓存版本以支持离线构建。
configurations["testRuntimeClasspath"].resolutionStrategy {
    force("com.google.guava:guava:31.1-jre")
}

tasks.withType<Test> {
    dependsOn(":composeruntime:jar")
}

sourceSets.main {
    kotlin.srcDirs("minecraftx", "ae2x")
    // KSP 生成目录显式挂入：loader 模块经 commonKotlin 配置重编译 common 源码，
    // 生成代码随之流入（artifacts 块在配置期求值，早于 KSP 插件自动注册 srcDir）。
    kotlin.srcDir(layout.buildDirectory.dir("generated/ksp/main/kotlin"))
}
