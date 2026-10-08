plugins {
    id("kotlin-project")
}

version = "0.1.0+compose.${libs.versions.compose.get().replace('-', '.')}"

// 项目依赖必须使用重打包后的主 jar；编译目录不包含嵌入的 Compose 类与资源。
listOf("apiElements", "runtimeElements").forEach {
    configurations.named(it) {
        outgoing.variants.clear()
    }
}

val composeRuntime = configurations.create("composeRuntime") {
    isCanBeResolved = true
    isCanBeConsumed = false
    exclude(group = "org.jetbrains.skiko")
    exclude(group = "org.jetbrains.kotlin")
    exclude(group = "org.jetbrains.kotlinx")
}.also {
    dependencies {
        it(libs.compose.ui)
        it(libs.compose.ui.graphics)
        it(libs.compose.foundation)
        it(libs.compose.foundation.layout)
        it(libs.compose.animation)
        it(libs.compose.material)
    }
}

dependencies {
    compileOnly(libs.compose.ui.graphics)
}

// The PathIterator replacement constructs PathSegment via its module-internal
// constructor; friend-paths grants access to the official ui-graphics internals.
configurations.detachedConfiguration(dependencies.create(libs.compose.ui.graphics.get())).let {
    it.isTransitive = false
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions.freeCompilerArgs.add(provider { "-Xfriend-paths=${it.singleFile.absolutePath}" })
    }
}

tasks.named<Jar>("jar") {
    description =
        "Produces a Compose desktop runtime jar without skiko, with the skiko-dependent ui-graphics classes replaced by the local implementations."
    from(composeRuntime.map(::zipTree))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes(mapOf("Automatic-Module-Name" to "org.jetbrains.compose.desktop.runtime"))
    }
}
